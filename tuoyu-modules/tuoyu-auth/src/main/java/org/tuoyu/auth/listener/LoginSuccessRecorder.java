package org.tuoyu.auth.listener;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.tuoyu.auth.event.UserLoginSuccessEvent;
import org.tuoyu.auth.service.LoginAudit;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.ip.AddressUtils;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.SysUserOnline;

import java.time.Duration;

/**
 * 登录成功后的记录：在线用户（有效期与令牌一致）、登录日志与用户的最近登录信息。
 * <p>
 * 日志中只记录令牌的最后 8 位，完整令牌不写入日志。
 *
 * @author tuoyu
 */
@Slf4j
@Component
public class LoginSuccessRecorder {

    private static final String USER_AGENT_HEADER = "User-Agent";

    private static final int TOKEN_TAIL_LENGTH = 8;

    /**
     * 令牌永不过期时 Sa-Token 的有效期取值
     */
    private static final long NEVER_EXPIRE = -1;

    private final RemoteUserService remoteUserService;

    private final LoginAudit loginAudit;

    public LoginSuccessRecorder(RemoteUserService remoteUserService, LoginAudit loginAudit) {
        this.remoteUserService = remoteUserService;
        this.loginAudit = loginAudit;
    }

    /**
     * @param event 登录成功事件
     */
    @EventListener
    public void onLoginSuccess(UserLoginSuccessEvent event) {
        SaLoginParameter parameter = event.loginParameter();
        String tokenValue = event.tokenValue();
        String ip = ServletUtils.getClientIP();
        String username = asString(parameter.getExtra(LoginHelper.USER_NAME_KEY));

        SysUserOnline online = new SysUserOnline();
        online.setIpaddr(ip);
        online.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        UserAgent userAgent = parseUserAgent();
        if (userAgent != null) {
            online.setBrowser(userAgent.getBrowser() == null ? null : userAgent.getBrowser().getName());
            online.setOs(userAgent.getOs() == null ? null : userAgent.getOs().getName());
        }
        online.setLoginTime(System.currentTimeMillis());
        online.setTokenId(tokenValue);
        online.setUserName(username);
        online.setClientKey(asString(parameter.getExtra(LoginHelper.CLIENT_KEY)));
        online.setDeviceType(parameter.getDeviceType());
        online.setDeptName(asString(parameter.getExtra(LoginHelper.DEPT_NAME_KEY)));
        String key = CacheNames.ONLINE_TOKEN_KEY + tokenValue;
        if (parameter.getTimeout() == NEVER_EXPIRE) {
            RedisUtils.setCacheObject(key, online);
        } else {
            RedisUtils.setCacheObject(key, online, Duration.ofSeconds(parameter.getTimeout()));
        }

        loginAudit.record(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success"));
        remoteUserService.recordLoginInfo(asLong(parameter.getExtra(LoginHelper.USER_KEY)), ip);
        log.info("用户登录成功：loginId={}，令牌 {}", event.loginId(), maskToken(tokenValue));
    }

    /**
     * @param token 令牌
     * @return 只保留最后 8 位的令牌，例如 {@code ***12345678}
     */
    static String maskToken(String token) {
        if (token == null) {
            return "***";
        }
        int start = Math.max(0, token.length() - TOKEN_TAIL_LENGTH);
        return "***" + token.substring(start);
    }

    private static UserAgent parseUserAgent() {
        HttpServletRequest request = ServletUtils.getRequest();
        String header = request == null ? null : request.getHeader(USER_AGENT_HEADER);
        return StringUtils.isBlank(header) ? null : UserAgentUtil.parse(header);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 扩展信息中的数字经 JWT 解析后可能是 Integer 或 Long
     */
    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || StringUtils.isBlank(value.toString())) {
            return null;
        }
        return Long.valueOf(value.toString().trim());
    }

}
