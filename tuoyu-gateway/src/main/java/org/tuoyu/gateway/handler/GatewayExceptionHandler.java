package org.tuoyu.gateway.handler;

import cn.dev33.satoken.exception.NotLoginException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.web.ClientDisconnect;
import org.tuoyu.common.satoken.handler.NotLoginMessage;
import org.tuoyu.common.json.utils.JsonUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 网关统一异常处理
 *
 * @author Lion Li
 */
@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public void handleNotLogin(HttpServletRequest request, HttpServletResponse response, NotLoginException ex) throws IOException {
        // 异常消息中带着令牌：日志只记类型，返回固定提示
        log.warn("[网关认证失败]请求路径:{},类型:{}", request.getRequestURI(), ex.getType());
        writeJson(response, HttpStatus.UNAUTHORIZED, NotLoginMessage.of(ex));
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
            writeJson(response, HttpStatus.SERVICE_UNAVAILABLE, "服务暂不可用，请稍后再试");
            return;
        }
        int code;
        String msg;
        if (ex instanceof ResponseStatusException responseStatusException) {
            code = responseStatusException.getStatusCode().value();
            msg = responseStatusException.getMessage();
        } else {
            code = HttpStatus.ERROR;
            msg = "内部服务器错误";
        }

        log.error("[网关异常处理]请求路径:{},异常信息:{}", request.getRequestURI(), exceptionMessage, ex);
        writeJson(response, code, msg);
    }

    private void writeJson(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJsonString(R.fail(code, msg)));
    }
}
