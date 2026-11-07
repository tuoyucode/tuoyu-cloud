package org.tuoyu.gateway.handler;

import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.springframework.core.annotation.Order;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ClientDisconnect;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.observability.trace.TraceContext;
import org.tuoyu.common.satoken.handler.AuthErrors;
import org.springframework.http.MediaType;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 网关统一异常处理。响应体与各服务相同（{@link ApiResult}，带 traceId），HTTP 状态表达错误类别：
 * 未登录 401、无权限 403、路径不存在 404、下游服务没有可用实例 503、其他请求错误按其状态，未预期的异常 500
 *
 * @author Lion Li
 */
@Slf4j
@Order(ExceptionHandlerOrder.GLOBAL)
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public void handleNotLogin(HttpServletRequest request, HttpServletResponse response, NotLoginException ex) throws IOException {
        // 异常消息中带着令牌：日志只记类型，返回固定提示
        log.warn("[网关认证失败]请求路径:{},类型:{}", request.getRequestURI(), ex.getType());
        writeJson(response, AuthErrors.of(ex));
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public void handleForbidden(HttpServletRequest request, HttpServletResponse response, RuntimeException ex) throws IOException {
        log.warn("[网关权限校验失败]请求路径:{},原因:{}", request.getRequestURI(), ex.getMessage());
        writeJson(response, AuthErrors.of(ex));
    }

    @ExceptionHandler(Throwable.class)
    public void handle(HttpServletRequest request, HttpServletResponse response, Throwable ex) throws IOException {
        if (ClientDisconnect.isClientDisconnect(ex)) {
            // 客户端主动断开（关闭页面时消息推送的长连接）：响应已经不可用，不写响应，也不按错误记录
            log.debug("[网关]客户端已断开，请求路径:{}", request.getRequestURI());
            return;
        }
        String exceptionMessage = ex.getMessage();
        if ("NotFoundException".equals(ex.getClass().getSimpleName())
            || (exceptionMessage != null && exceptionMessage.contains("Unable to find instance"))) {
            // 路由存在、下游服务暂时没有可用实例（未启动、重启或下线中）：属于可预期的情况，返回 503，日志不带堆栈
            log.warn("[网关]下游服务暂不可用，请求路径:{},原因:{}", request.getRequestURI(), exceptionMessage);
            writeJson(response, ApiResult.fail(HttpStatus.SERVICE_UNAVAILABLE, "服务暂不可用，请稍后再试"));
            return;
        }
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().value() < HttpStatus.ERROR) {
            // 请求本身的问题（路径不存在、请求方式不支持等）：按其状态返回，记 WARN
            int status = errorResponse.getStatusCode().value();
            log.warn("[网关]请求错误（{}），请求路径:{},原因:{}", status, request.getRequestURI(), exceptionMessage);
            writeJson(response, ApiResult.fail(status, status == HttpStatus.NOT_FOUND ? "请求地址不存在" : "请求错误"));
            return;
        }
        int code;
        String msg;
        if (ex instanceof ResponseStatusException responseStatusException) {
            code = responseStatusException.getStatusCode().value();
            msg = responseStatusException.getReason() == null ? "内部服务器错误" : responseStatusException.getReason();
        } else {
            code = HttpStatus.ERROR;
            msg = "内部服务器错误";
        }

        log.error("[网关异常处理]请求路径:{},异常信息:{}", request.getRequestURI(), exceptionMessage, ex);
        writeJson(response, ApiResult.fail(code, msg));
    }

    private void writeJson(HttpServletResponse response, ApiResult<Void> result) throws IOException {
        result.setTraceId(TraceContext.traceId());
        response.setStatus(result.httpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJsonString(result));
    }
}
