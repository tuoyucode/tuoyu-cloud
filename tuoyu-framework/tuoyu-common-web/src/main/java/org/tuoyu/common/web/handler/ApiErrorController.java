package org.tuoyu.common.web.handler;

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
 * 错误页面（容器的错误分派）：过滤器链中抛出、Spring MVC 异常处理没有接住的异常，以及 {@code response.sendError}，
 * 由容器转到这里。响应与全局异常处理一致：{@link ApiResult}、HTTP 状态表达错误类别、带 traceId（链路过滤器在错误分派中沿用原请求的链路）。
 * <p>
 * 不返回异常消息与请求细节：4xx 用与状态相符的固定提示；有异常或状态为 500 时按未预期的异常处理，
 * 只返回错误编号，详细信息按编号记在日志中。不是错误分派（直接访问错误路径）时按路径不存在处理（404）。
 * <p>
 * 不标注 Sa-Token 注解：只由容器的错误分派访问，各服务与网关的登录过滤器都排除了错误路径，
 * 本模块也不依赖 Sa-Token（接口权限注解检查中登记为例外）。
 *
 * @author tuoyu
 */
@Slf4j
@RestController
@RequestMapping("${spring.web.error.path:${error.path:/error}}")
public class ApiErrorController implements ErrorController {

    @RequestMapping
    public ResponseEntity<ApiResult<Void>> error(HttpServletRequest request) {
        ResponseEntity<ApiResult<Void>> response = resolve(request);
        ApiResult<Void> body = response.getBody();
        if (body != null && body.getTraceId() == null) {
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
        int status = status(code);
        if (exception != null || status == HttpStatus.ERROR) {
            String errorId = RandomUtil.randomNumbers(8);
            log.error("请求地址'{}',请求处理失败（{}）, 错误编号: {}", uri, status, errorId, exception);
            return ApiErrors.response(HttpStatus.ERROR, "发生未知异常，请联系管理员 [错误编号: " + errorId + "]");
        }
        log.warn("请求地址'{}',请求错误（{}）", uri, status);
        return ApiErrors.response(status, ApiErrors.statusMessage(status));
    }

    /**
     * 错误分派中的状态；缺少或不是错误状态时按 500 处理
     */
    private static int status(Object value) {
        if (value instanceof Integer code && code >= HttpStatus.BAD_REQUEST && code <= 599) {
            return code;
        }
        return HttpStatus.ERROR;
    }

}
