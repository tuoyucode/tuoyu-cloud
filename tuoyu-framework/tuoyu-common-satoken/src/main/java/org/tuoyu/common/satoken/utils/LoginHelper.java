package org.tuoyu.common.satoken.utils;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.tuoyu.common.core.constant.HeaderNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.ip.AddressUtils;
import org.tuoyu.system.api.model.LoginUser;

/**
 * 登录与当前会话：登录时写入令牌扩展信息与令牌会话，读取当前请求的登录用户。
 * <p>
 * 只在 satoken 与 auth 模块中使用；业务代码读取当前用户一律通过 core 的 {@code UserContext}，
 * 查询其他会话的登录用户用 {@link LoginSessions}。
 *
 * @author tuoyu
 */
public final class LoginHelper {

    /**
     * 令牌会话中保存登录用户的键
     */
    public static final String LOGIN_USER_KEY = "loginUser";

    /**
     * 令牌扩展信息：用户 ID
     */
    public static final String USER_KEY = "userId";

    /**
     * 令牌扩展信息：用户名
     */
    public static final String USER_NAME_KEY = "userName";

    /**
     * 令牌扩展信息：部门 ID
     */
    public static final String DEPT_KEY = "deptId";

    /**
     * 令牌扩展信息：部门名称
     */
    public static final String DEPT_NAME_KEY = "deptName";

    /**
     * 令牌扩展信息：部门类别
     */
    public static final String DEPT_CATEGORY_KEY = "deptCategory";

    /**
     * 令牌扩展信息：客户端 ID，与请求头名相同，登录校验按它比对请求中的客户端
     */
    public static final String CLIENT_KEY = HeaderNames.CLIENT_ID;

    /**
     * 令牌扩展信息：客户端允许访问的路径
     */
    public static final String CLIENT_ACCESS_PATH_KEY = "clientAccessPath";

    /**
     * 令牌扩展信息：客户端的来源 IP 白名单
     */
    public static final String CLIENT_IP_WHITELIST_KEY = "clientIpWhitelist";

    private static final String USER_AGENT_HEADER = "User-Agent";

    private LoginHelper() {
    }

    /**
     * 登录：以 {@link LoginUser#getLoginId()} 登录，把用户与部门写入令牌扩展信息，把登录用户存进本次登录的令牌会话。
     * <p>
     * 在 HTTP 请求中登录时，补充登录用户中为空白的终端信息（IP、归属地、浏览器、系统、设备类型），已有的值保留。
     *
     * @param loginUser 登录用户
     * @param parameter 登录参数，可以为 null；调用方放入的扩展信息保留，登录监听器读取的就是这个对象
     */
    public static void login(LoginUser loginUser, SaLoginParameter parameter) {
        SaLoginParameter loginParameter = parameter == null ? new SaLoginParameter() : parameter;
        HttpServletRequest request = ServletUtils.getRequest();
        if (request != null) {
            fillTerminalInfo(loginUser, loginParameter, request);
        }
        putExtra(loginParameter, USER_KEY, loginUser.getUserId());
        putExtra(loginParameter, USER_NAME_KEY, loginUser.getUsername());
        putExtra(loginParameter, DEPT_KEY, loginUser.getDeptId());
        putExtra(loginParameter, DEPT_NAME_KEY, loginUser.getDeptName());
        putExtra(loginParameter, DEPT_CATEGORY_KEY, loginUser.getDeptCategory());
        StpUtil.login(loginUser.getLoginId(), loginParameter);
        StpUtil.getTokenSessionByToken(StpUtil.getTokenValue()).set(LOGIN_USER_KEY, loginUser);
    }

    /**
     * 当前请求的登录用户
     *
     * @return 登录用户；请求中没有令牌、未登录、令牌无效或过期、会话中没有登录用户时为 null
     */
    public static LoginUser getLoginUser() {
        String token = StpUtil.getTokenValue();
        if (StringUtils.isBlank(token)) {
            return null;
        }
        SaSession session;
        try {
            if (StpUtil.getLoginIdDefaultNull() == null) {
                return null;
            }
            // 不创建会话：令牌有效但会话已不存在时按没有登录用户处理
            session = StpUtil.getStpLogic().getTokenSessionByToken(token, false);
        } catch (SaTokenException e) {
            // 令牌无效、过期、被顶下线等情况 Sa-Token 抛出的异常类型不止一种，一律视为没有登录用户
            return null;
        }
        if (session == null) {
            return null;
        }
        return session.get(LOGIN_USER_KEY) instanceof LoginUser user ? user : null;
    }

    /**
     * 当前请求的用户 ID，取自令牌扩展信息
     *
     * @return 用户 ID；未登录、没有该值或读取失败时为 null
     */
    public static Long getUserId() {
        Object value;
        try {
            if (StringUtils.isBlank(StpUtil.getTokenValue())) {
                return null;
            }
            value = StpUtil.getExtra(USER_KEY);
        } catch (RuntimeException e) {
            // 未登录、令牌无效或不在请求上下文中：按没有用户 ID 处理
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || StringUtils.isBlank(value.toString())) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param userId 用户 ID，可以为 null
     * @return 是否为超级管理员
     */
    public static boolean isSuperAdmin(Long userId) {
        return SystemConstants.SUPER_ADMIN_USER_ID.equals(userId);
    }

    private static void fillTerminalInfo(LoginUser loginUser, SaLoginParameter parameter, HttpServletRequest request) {
        if (StringUtils.isBlank(loginUser.getIpaddr())) {
            loginUser.setIpaddr(ServletUtils.getClientIP(request));
        }
        String ip = loginUser.getIpaddr();
        if (StringUtils.isBlank(loginUser.getLoginLocation()) && StringUtils.isNotBlank(ip)) {
            loginUser.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        }
        String userAgentHeader = request.getHeader(USER_AGENT_HEADER);
        UserAgent userAgent = StringUtils.isBlank(userAgentHeader) ? null : UserAgentUtil.parse(userAgentHeader);
        if (userAgent != null) {
            if (StringUtils.isBlank(loginUser.getBrowser()) && userAgent.getBrowser() != null) {
                loginUser.setBrowser(userAgent.getBrowser().getName());
            }
            if (StringUtils.isBlank(loginUser.getOs()) && userAgent.getOs() != null) {
                loginUser.setOs(userAgent.getOs().getName());
            }
        }
        if (StringUtils.isBlank(loginUser.getDeviceType()) && StringUtils.isNotBlank(parameter.getDeviceType())) {
            loginUser.setDeviceType(parameter.getDeviceType());
        }
    }

    /**
     * 只写入有值的扩展信息：没有值的项读取时同样为 null，不必放进令牌
     */
    private static void putExtra(SaLoginParameter parameter, String key, Object value) {
        if (value != null) {
            parameter.setExtra(key, value);
        }
    }

}
