package org.tuoyu.auth.service;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.lock.annotation.Lock4j;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthUser;
import org.tuoyu.auth.form.RegisterBody;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.enums.LoginType;
import org.tuoyu.common.core.enums.UserType;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.CaptchaException;
import org.tuoyu.common.core.exception.user.CaptchaExpireException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.redis.guard.VerifyResult;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.bo.RemoteUserBo;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;
import org.tuoyu.system.api.model.LoginUser;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 登录校验方法
 * <p>
 * 认证加固的设计见 docs/adr/0002-认证加固与可信代理.md。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class SysLoginService {

    /**
     * 查询用户时表示“账号不存在、已停用”的错误码。这两种情况与凭证错误返回同样的提示，不泄露账号是否存在
     */
    private static final Set<String> HIDDEN_USER_ERRORS = Set.of("user.not.exists", "user.blocked");

    private final RemoteUserService remoteUserService;
    private final RemoteSocialService remoteSocialService;

    private final CaptchaProperties captchaProperties;
    private final AuthSecurityProperties authSecurityProperties;
    private final VerifyCodeService verifyCodeService;
    private final LoginFailureLimiter loginFailureLimiter;
    private final PasswordHasher passwordHasher;

    /**
     * 绑定第三方用户
     *
     * @param authUserData 授权响应实体
     */
    @Lock4j
    public void socialRegister(AuthUser authUserData) {
        String authId = authUserData.getSource() + authUserData.getUuid();
        // 第三方用户信息
        RemoteSocialBo bo = BeanUtil.toBean(authUserData, RemoteSocialBo.class);
        BeanUtil.copyProperties(authUserData.getToken(), bo);
        Long userId = UserContext.userId();
        bo.setUserId(userId);
        bo.setAuthId(authId);
        bo.setOpenId(authUserData.getUuid());
        bo.setUserName(authUserData.getUsername());
        bo.setNickName(authUserData.getNickname());
        List<RemoteSocialVo> checkList = remoteSocialService.selectByAuthId(authId);
        if (CollUtil.isNotEmpty(checkList)) {
            throw new ServiceException("此三方账号已经被绑定!");
        }
        // 查询是否已经绑定用户
        RemoteSocialBo params = new RemoteSocialBo();
        params.setUserId(userId);
        params.setSource(bo.getSource());
        List<RemoteSocialVo> list = remoteSocialService.queryList(params);
        if (CollUtil.isEmpty(list)) {
            // 没有绑定用户, 新增用户信息
            remoteSocialService.insertByBo(bo);
        } else {
            // 更新用户信息
            bo.setId(list.getFirst().getId());
            remoteSocialService.updateByBo(bo);
            // 如果要绑定的平台账号已经被绑定过了 是否抛异常自行决断
            // throw new ServiceException("此平台账号已经被绑定!");
        }
    }

    /**
     * 退出登录
     */
    public void logout() {
        try {
            LoginUser loginUser = LoginHelper.getLoginUser();
            if (ObjectUtil.isNull(loginUser)) {
                return;
            }
            recordLoginInfo(loginUser.getUsername(), Constants.LOGOUT, MessageUtils.message("user.logout.success"));
        } catch (NotLoginException ignored) {
        } finally {
            try {
                StpUtil.logout();
            } catch (NotLoginException ignored) {
            }
        }
    }

    /**
     * 注册时的用户类型：必须与某个用户类型完全一致。
     * 不使用 UserType.getUserType：它按“包含”匹配，找不到时抛普通异常（返回 500）
     *
     * @param value 请求中的用户类型
     * @return 用户类型标识
     */
    static String registerUserType(String value) {
        for (UserType type : UserType.values()) {
            if (type.getUserType().equals(value)) {
                return value;
            }
        }
        throw new ServiceException("用户类型不正确");
    }

    /**
     * 注册
     */
    public void register(RegisterBody registerBody) {
        String username = registerBody.getUsername();
        String password = registerBody.getPassword();
        // 校验用户类型是否存在
        String userType = registerUserType(registerBody.getUserType());

        boolean captchaEnabled = captchaProperties.getEnabled();
        // 验证码开关
        if (captchaEnabled) {
            validateCaptcha(username, registerBody.getCode(), registerBody.getUuid());
        }

        // 注册用户信息
        RemoteUserBo remoteUserBo = new RemoteUserBo();
        remoteUserBo.setUserName(username);
        remoteUserBo.setNickName(username);
        remoteUserBo.setPassword(passwordHasher.hash(password));
        remoteUserBo.setUserType(userType);

        remoteUserService.registerUserInfo(remoteUserBo);
        recordLoginInfo(username, Constants.REGISTER, MessageUtils.message("user.register.success"));
    }

    /**
     * 校验图形验证码。验证码只能校验一次，无论对错都立即作废。
     *
     * @param username 用户名（写入登录日志）
     * @param code     验证码
     * @param uuid     唯一标识
     */
    public void validateCaptcha(String username, String code, String uuid) {
        VerifyResult result = verifyCodeService.checkCaptcha(uuid, code);
        if (result == VerifyResult.EXPIRED) {
            recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        if (result != VerifyResult.PASSED) {
            recordLoginInfo(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error"));
            throw new CaptchaException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param username 用户名
     * @param status   状态
     * @param message  消息内容
     */
    public void recordLoginInfo(String username, String status, String message) {
        // 封装对象
        LoginInfoEvent loginInfoEvent = new LoginInfoEvent();
        loginInfoEvent.setUsername(username);
        loginInfoEvent.setStatus(status);
        loginInfoEvent.setMessage(message);
        SpringUtils.context().publishEvent(loginInfoEvent);
    }

    /**
     * 校验登录凭证，并按账号与来源 IP 计数失败次数。
     * <ol>
     *     <li>处于锁定期时直接拒绝，不再校验凭证；</li>
     *     <li>校验失败时计数，返回统一的提示（不区分账号不存在、已停用与凭证错误），真实原因只写入登录日志；</li>
     *     <li>本次失败达到次数时锁定，提示“登录失败次数过多，请 N 分钟后再试”；</li>
     *     <li>校验通过时清除账号维度的计数。</li>
     * </ol>
     *
     * @param loginType 登录方式，决定统一提示的文案
     * @param account   登录账号（用户名、手机号或邮箱），作为账号维度的计数依据
     * @param attempt   凭证校验
     * @return 校验通过的用户
     */
    public LoginUser authenticate(LoginType loginType, String account, Supplier<AuthAttempt> attempt) {
        String ip = ServletUtils.getClientIP();
        Duration locked = loginFailureLimiter.lockedFor(account, ip);
        if (locked != null) {
            recordLoginInfo(account, Constants.LOGIN_FAIL, MessageUtils.message("user.login.reason.rejected"));
            throw new UserException("user.login.locked", LoginFailureLimiter.toMinutes(locked));
        }

        AuthAttempt result = attempt.get();
        if (result.passed()) {
            loginFailureLimiter.reset(account);
            return result.user();
        }

        LoginFailureLimiter.Failure failure = loginFailureLimiter.recordFailure(account, ip);
        String reason = result.failReason();
        if (failure.locked()) {
            reason = reason + "；" + MessageUtils.message("user.login.reason.locked",
                failure.accountFailures(), LoginFailureLimiter.toMinutes(failure.lockedFor()));
        }
        recordLoginInfo(account, Constants.LOGIN_FAIL, reason);
        if (failure.locked()) {
            throw new UserException("user.login.locked", LoginFailureLimiter.toMinutes(failure.lockedFor()));
        }
        throw new UserException(loginType == LoginType.PASSWORD ? "user.login.failed" : "user.login.code.failed");
    }

    /**
     * 查询登录用户。账号不存在或已停用时返回失败（原因只写入登录日志），其他异常照常抛出。
     *
     * @param loader 查询用户
     * @return 校验结果
     */
    public AuthAttempt findUser(Supplier<LoginUser> loader) {
        try {
            LoginUser user = loader.get();
            return user == null ? AuthAttempt.failure(MessageUtils.message("user.not.exists", "")) : AuthAttempt.success(user);
        } catch (UserException e) {
            if (HIDDEN_USER_ERRORS.contains(e.getCode())) {
                return AuthAttempt.failure(e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 校验短信、邮箱验证码（一次性使用，连续输错达到上限后作废）。
     *
     * @param scene  场景，见 {@link VerifyCodeService#SCENE_SMS}
     * @param target 接收方（手机号、邮箱）
     * @param input  用户输入
     * @return 失败原因；校验通过时返回 null
     */
    public String checkVerifyCode(String scene, String target, String input) {
        VerifyResult result = verifyCodeService.checkCode(scene, target, input,
            authSecurityProperties.getVerifyCode().getMaxAttempts());
        return switch (result) {
            case PASSED -> null;
            case EXPIRED -> MessageUtils.message("user.login.reason.code.expire");
            case MISMATCH -> MessageUtils.message("user.login.reason.code.mismatch");
            case EXHAUSTED -> MessageUtils.message("user.login.reason.code.exhausted");
        };
    }

    /**
     * 校验客户端密钥。未开启 {@code tuoyu.auth.client.require-secret} 时不校验。
     *
     * @param client       客户端
     * @param clientSecret 请求中的客户端密钥
     * @return 是否通过
     */
    public boolean isClientSecretAccepted(RemoteClientVo client, String clientSecret) {
        if (!authSecurityProperties.getClient().isRequireSecret()) {
            return true;
        }
        String expected = client.getClientSecret();
        if (expected == null || expected.isEmpty() || clientSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), clientSecret.getBytes(StandardCharsets.UTF_8));
    }

}
