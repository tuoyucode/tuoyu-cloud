package org.tuoyu.common.satoken.handler;

import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.springframework.core.annotation.Order;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SaToken异常处理器：未登录 401，无权限、无角色 403
 *
 * @author Lion Li
 */
@Slf4j
@Order(ExceptionHandlerOrder.MODULE)
@RestControllerAdvice
public class SaTokenExceptionHandler {

    /**
     * 处理权限码、角色校验失败异常。
     *
     * @param e       异常信息
     * @param request 当前请求
     * @return 403 响应
     */
    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<ApiResult<Void>> handleNotAccessException(RuntimeException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String reason = e instanceof NotRoleException ? "角色权限校验失败" : "权限码校验失败";
        log.warn("请求地址'{}',{}'{}'", requestURI, reason, e.getMessage());
        return ApiErrors.response(AuthErrors.of(e));
    }

    /**
     * 处理未登录或登录态失效异常。
     *
     * @param e       异常信息
     * @param request 当前请求
     * @return 401 响应
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResult<Void>> handleNotLoginException(NotLoginException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        // 异常消息中带着令牌，日志只记类型
        log.warn("请求地址'{}',认证失败（类型 {}）,无法访问系统资源", requestURI, e.getType());
        return ApiErrors.response(AuthErrors.of(e));
    }

}
