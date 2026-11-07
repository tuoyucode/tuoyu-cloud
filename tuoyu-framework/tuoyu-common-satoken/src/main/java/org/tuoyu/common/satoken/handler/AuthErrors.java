package org.tuoyu.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.servlet.http.HttpServletResponse;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

/**
 * 登录与权限校验失败时的响应，全局异常处理与过滤器（网关、各服务的登录校验）共用：
 * <ul>
 *     <li>未登录、令牌失效：401，提示见 {@link NotLoginMessage}（不带令牌）；</li>
 *     <li>无权限、无角色（含客户端不允许访问的路径、来源 IP 不在白名单）：403；</li>
 *     <li>其他认证失败（例如请求不是来自网关）：401。</li>
 * </ul>
 *
 * @author tuoyu
 */
public final class AuthErrors {

    /**
     * 无权限时的提示
     */
    public static final String FORBIDDEN_MESSAGE = "没有访问权限，请联系管理员授权";

    /**
     * 其他认证失败时的提示
     */
    public static final String UNAUTHORIZED_MESSAGE = "认证失败，无法访问系统资源";

    private AuthErrors() {
    }

    /**
     * @param e 校验时抛出的异常
     * @return 对应的失败响应，HTTP 状态见 {@link ApiResult#httpStatus()}
     */
    public static ApiResult<Void> of(Throwable e) {
        if (e instanceof NotLoginException notLogin) {
            return ApiResult.fail(HttpStatus.UNAUTHORIZED, NotLoginMessage.of(notLogin));
        }
        if (e instanceof NotPermissionException || e instanceof NotRoleException) {
            return ApiResult.fail(HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
        }
        return ApiResult.fail(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }

    /**
     * 在过滤器中写出失败响应：设置 HTTP 状态与内容类型，返回响应体（Sa-Token 过滤器把错误处理的返回值写进响应）
     *
     * @param response 当前响应，可以为 null
     * @param result   失败响应（不带数据）
     * @param traceId  链路 ID，可以为 null
     * @return JSON 响应体，字段与 {@link ApiResult} 相同
     */
    public static String write(HttpServletResponse response, ApiResult<?> result, String traceId) {
        if (response != null && !response.isCommitted()) {
            response.setStatus(result.httpStatus());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        return ApiErrors.toJson(result.getCode(), result.getMessage(), traceId);
    }

}
