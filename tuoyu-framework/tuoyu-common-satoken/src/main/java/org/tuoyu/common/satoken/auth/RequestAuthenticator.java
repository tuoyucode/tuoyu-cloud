package org.tuoyu.common.satoken.auth;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;
import org.tuoyu.common.core.utils.NetUtils;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.satoken.handler.NotLoginMessage;
import org.tuoyu.common.satoken.utils.LoginHelper;

import java.util.List;

/**
 * 请求的登录校验，网关与各服务共用（登录校验下沉到各服务）：
 * <ol>
 *     <li>白名单中的路径不校验；</li>
 *     <li>必须已登录；</li>
 *     <li>请求的客户端 ID（请求头或参数 {@code clientid}）必须与令牌中的一致；</li>
 *     <li>客户端配置了可访问路径、IP 白名单时，按它们校验。</li>
 * </ol>
 * 路径一律使用完整路径（前端访问的路径）。服务收到的请求已被网关去掉前缀，由 {@link #fullPath} 还原。
 * <p>
 * 白名单与客户端可访问路径只按规范写法的路径匹配（见 {@link #isCanonicalPath}）：Web 容器路由时会解析 {@code ..}、
 * 去掉路径参数，按原文匹配会让 {@code /白名单路径/../受保护路径} 之类的写法通过校验后访问到另一个接口。
 *
 * @author tuoyu
 */
public class RequestAuthenticator {

    /**
     * 网关转发时写入的前缀（被去掉的第一段路径）
     */
    public static final String FORWARDED_PREFIX = "X-Forwarded-Prefix";

    private static final String CLIENT_RULE_SEPARATOR_REGEX = "[,;\\r\\n]+";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AuthWhitelistProperties whitelist;

    public RequestAuthenticator(AuthWhitelistProperties whitelist) {
        this.whitelist = whitelist;
    }

    /**
     * @param path 完整路径
     * @return 是否在免登录白名单中
     */
    public boolean isWhitelisted(String path) {
        return isCanonicalPath(path) && whitelist.getWhites().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * 是否为规范写法的路径：以 {@code /} 开头，不含路径参数（{@code ;}）、编码字符（{@code %}）、反斜杠、连续的斜杠，
     * 也没有 {@code .} 或 {@code ..} 段。浏览器与前端发出的请求都是这种写法；其他写法不参与白名单与可访问路径的匹配
     *
     * @param path 请求路径
     * @return 是否为规范写法
     */
    public static boolean isCanonicalPath(String path) {
        if (path == null || !path.startsWith("/") || path.contains("//")) {
            return false;
        }
        if (path.indexOf(';') >= 0 || path.indexOf('%') >= 0 || path.indexOf('\\') >= 0) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验登录与客户端规则，白名单中的路径直接通过
     *
     * @param request 请求
     * @param path    完整路径
     * @throws NotLoginException      未登录，或客户端 ID 与令牌不一致
     * @throws NotPermissionException 客户端不允许访问该路径，或来源 IP 不在白名单
     */
    public void authenticate(HttpServletRequest request, String path) {
        if (isWhitelisted(path)) {
            return;
        }
        StpUtil.checkLogin();

        String headerCid = request.getHeader(LoginHelper.CLIENT_KEY);
        String paramCid = ServletUtils.getParameter(LoginHelper.CLIENT_KEY);
        String clientId = tokenExtra(LoginHelper.CLIENT_KEY);
        if (!StringUtils.equalsAny(clientId, headerCid, paramCid)) {
            // 不传令牌：Sa-Token 会把它拼进异常消息，消息会原样返回给客户端并写进日志
            throw NotLoginException.newInstance(StpUtil.getLoginType(), NotLoginMessage.CLIENT_MISMATCH, "客户端ID与Token不匹配", null);
        }

        String accessPath = tokenExtra(LoginHelper.CLIENT_ACCESS_PATH_KEY);
        if (StringUtils.isNotBlank(accessPath)) {
            List<String> rules = StringUtils.str2List(accessPath, CLIENT_RULE_SEPARATOR_REGEX, true, true);
            if (!isCanonicalPath(path) || !StringUtils.matches(path, rules)) {
                throw new NotPermissionException("当前客户端未授权访问该接口路径");
            }
        }

        String ipWhitelist = tokenExtra(LoginHelper.CLIENT_IP_WHITELIST_KEY);
        if (StringUtils.isNotBlank(ipWhitelist)) {
            String clientIp = ServletUtils.getClientIP(request);
            List<String> rules = StringUtils.str2List(ipWhitelist, CLIENT_RULE_SEPARATOR_REGEX, true, true);
            if (rules.stream().noneMatch(rule -> NetUtils.isMatchIpRule(rule, clientIp))) {
                throw new NotPermissionException("当前客户端IP不在白名单内");
            }
        }
    }

    /**
     * 还原完整路径：网关去掉了第一段路径并写入 {@value #FORWARDED_PREFIX}；没有去掉前缀的路由（请求路径本来就以它开头）
     * 与单体（没有这个请求头）直接使用请求路径。
     * <p>
     * 调用方必须先确认请求来自网关（same-token 校验通过），否则请求头不可信。
     *
     * @param requestUri      请求路径
     * @param forwardedPrefix 网关写入的前缀，可以为空
     * @return 完整路径
     */
    public static String fullPath(String requestUri, String forwardedPrefix) {
        if (StringUtils.isBlank(forwardedPrefix) || !forwardedPrefix.startsWith("/")) {
            return requestUri;
        }
        String prefix = forwardedPrefix.endsWith("/") ? forwardedPrefix.substring(0, forwardedPrefix.length() - 1) : forwardedPrefix;
        if (requestUri.equals(prefix) || requestUri.startsWith(prefix + "/")) {
            return requestUri;
        }
        return prefix + (requestUri.startsWith("/") ? requestUri : "/" + requestUri);
    }

    private static String tokenExtra(String key) {
        Object extra = StpUtil.getExtra(key);
        return extra == null ? null : extra.toString();
    }

}
