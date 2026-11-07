package org.tuoyu.gateway.handler;

import cn.hutool.core.util.RandomUtil;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.observability.trace.TraceContext;

/**
 * 网关的错误页面（容器的错误分派）：网关过滤器中抛出、{@link GatewayExceptionHandler} 没有接住的异常，
 * 以及 {@code response.sendError}，由容器转到这里。响应与各服务的错误页面一致：{@link ApiResult}、
 * HTTP 状态表达错误类别、带 traceId（链路过滤器在错误分派中沿用原请求的链路）。
 * <p>
 * 不返回异常消息：4xx 用与状态相符的固定提示；有异常或状态为 500 时只返回错误编号，详细信息按编号记在日志中。
 * 不是错误分派（直接访问错误路径）时按路径不存在处理（404）。
 * 不标注 Sa-Token 注解：只由错误分派访问，网关的登录过滤器排除了错误路径（接口权限注解检查中登记为例外）。
 *
 * @author tuoyu
 */
@Slf4j
@RestController
@RequestMapping("${spring.web.error.path:${error.path:/error}}")
public class GatewayErrorController implements ErrorController {

    @RequestMapping
    public ResponseEntity<ApiResult<Void>> error(HttpServletRequest request) {
        ResponseEntity<ApiResult<Void>> response = resolve(request);
        ApiResult<Void> body = response.getBody();
        if (body != null) {
            body.setTraceId(TraceContext.traceId());
        }
        return response;
    }

    private static ResponseEntity<ApiResult<Void>> resolve(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Throwable exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable t ? t : null;
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code == null && exception == null) {
            return ApiErrors.response(HttpStatus.NOT_FOUND, ApiErrors.statusMessage(HttpStatus.NOT_FOUND));
        }
        int status = code instanceof Integer value && value >= HttpStatus.BAD_REQUEST && value <= 599
            ? value : HttpStatus.ERROR;
        if (exception != null || status == HttpStatus.ERROR) {
            String errorId = RandomUtil.randomNumbers(8);
            log.error("[网关]请求路径:{},请求处理失败（{}）,错误编号:{}", uri, status, errorId, exception);
            return ApiErrors.response(HttpStatus.ERROR, "内部服务器错误 [错误编号: " + errorId + "]");
        }
        log.warn("[网关]请求错误（{}），请求路径:{}", status, uri);
        return ApiErrors.response(status, ApiErrors.statusMessage(status));
    }

}
