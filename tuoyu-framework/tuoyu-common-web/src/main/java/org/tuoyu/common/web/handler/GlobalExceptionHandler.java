package org.tuoyu.common.web.handler;

import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.springframework.core.annotation.Order;
import cn.hutool.core.util.RandomUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.SseException;
import org.tuoyu.common.core.exception.base.BaseException;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.core.web.ClientDisconnect;
import org.tuoyu.common.core.web.RequestBodyTooLargeException;
import org.tuoyu.common.observability.trace.TraceContext;
import org.springframework.boot.json.JsonParseException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.expression.ExpressionException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * 全局异常处理器。响应体都是 {@link ApiResult}，HTTP 状态表达错误类别：
 * 参数与请求格式错误 400、业务异常按错误码（400 ~ 599 之间时用它，否则 400）、路径不存在 404、请求过大 413、
 * 未预期的异常 500（只返回错误编号，详细信息按编号记在日志中）。traceId 由 {@code ApiResultAdvice} 在写出前填入。
 * <p>
 * 日志级别：业务异常（账号或密码错误、数据校验不通过等）与请求本身的问题（参数格式、路径、请求方式）是可预期的，记 WARN；
 * 未知异常、系统异常记 ERROR 并带堆栈
 * <p>
 * 提示中不带框架或 JDK 的原始消息（类名、转换器报错等），只说明哪个参数有问题。
 * 顺序为 {@link ExceptionHandlerOrder#GLOBAL}：模块专用的异常处理类排在前面，这里的兜底方法不会抢先处理它们的异常
 *
 * @author Lion Li
 */
@Slf4j
@Order(ExceptionHandlerOrder.GLOBAL)
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 请求方式不支持：405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                               HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        return ApiErrors.response(HttpStatus.BAD_METHOD, ApiErrors.statusMessage(HttpStatus.BAD_METHOD));
    }

    /**
     * 业务异常：错误码在 400 ~ 599 之间时作为 HTTP 状态，否则 400；没有错误码时为 400
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiResult<Void>> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.warn("请求地址'{}',业务异常:{}", request.getRequestURI(), e.getMessage());
        return ApiErrors.response(e);
    }

    /**
     * 消息推送（SSE）的认证失败：响应类型是事件流，直接写出 JSON 文本
     */
    @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(SseException.class)
    public String handleSseException(SseException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.debug("请求地址'{}',认证失败'{}',无法访问系统资源", requestURI, e.getMessage());
        return ApiErrors.toJson(HttpStatus.UNAUTHORIZED, "认证失败，无法访问系统资源", TraceContext.traceId());
    }

    /**
     * 上传的文件超过上限：413
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e,
                                                                       HttpServletRequest request) {
        log.warn("请求地址'{}',上传内容超过上限", request.getRequestURI());
        return ApiErrors.response(HttpStatus.PAYLOAD_TOO_LARGE, "上传内容超过上限");
    }

    /**
     * 缺少必需的请求参数：400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingParameter(MissingServletRequestParameterException e,
                                                                  HttpServletRequest request) {
        log.warn("请求地址'{}',缺少请求参数'{}'", request.getRequestURI(), e.getParameterName());
        return ApiErrors.response(HttpStatus.BAD_REQUEST, String.format("缺少必需的请求参数[%s]", e.getParameterName()));
    }

    /**
     * 其他 servlet 异常：Spring MVC 的请求错误（媒体类型不支持、缺少请求头等）带有 HTTP 状态，按它返回；其余按未知异常处理
     */
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ApiResult<Void>> handleServletException(ServletException e, HttpServletRequest request) {
        if (e instanceof ErrorResponse errorResponse) {
            return requestError(errorResponse.getStatusCode().value(), e, request);
        }
        return unexpected(e, request, "发生未知异常，请联系管理员");
    }

    /**
     * 业务异常（用户、文件等模块的异常）：400
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResult<Void>> handleBaseException(BaseException e, HttpServletRequest request) {
        log.warn("请求地址'{}',业务异常:{}", request.getRequestURI(), e.getMessage());
        return ApiErrors.response(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 请求路径中缺少必需的路径变量：400
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingPathVariableException(MissingPathVariableException e,
                                                                              HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求路径中缺少必需的路径变量'{}'", requestURI);
        return ApiErrors.response(HttpStatus.BAD_REQUEST, String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配：400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
                                                                                     HttpServletRequest request) {
        log.warn("请求地址'{}',参数[{}]类型不匹配:{}", request.getRequestURI(), e.getName(), e.getMessage());
        return ApiErrors.response(HttpStatus.BAD_REQUEST, invalidParameter(e.getName()));
    }

    /**
     * 找不到路由：404
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResult<Void>> handleNoHandlerFoundException(ServletException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}'不存在.", requestURI);
        return ApiErrors.response(HttpStatus.NOT_FOUND, "请求地址不存在");
    }

    /**
     * 读写请求、响应时的 I/O 异常
     */
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(IOException.class)
    public void handleIoException(IOException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        if (ClientDisconnect.isClientDisconnect(e)) {
            // 客户端主动断开（关闭页面时消息推送的长连接）：属于正常情况。按异常判断而不是按路径，
            // 微服务中经网关转发后路径已去掉模块前缀
            log.debug("请求地址'{}',客户端已断开", requestURI);
            return;
        }
        log.error("请求地址'{}',连接中断", requestURI, e);
    }

    /**
     * sse 连接超时异常 不需要处理
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleRuntimeException(AsyncRequestTimeoutException e) {
    }

    /**
     * 拦截未知的运行时异常：500，只返回错误编号
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResult<Void>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        if (e instanceof ErrorResponse errorResponse) {
            return requestError(errorResponse.getStatusCode().value(), e, request);
        }
        return unexpected(e, request, "发生未知异常，请联系管理员");
    }

    /**
     * 系统异常：500，只返回错误编号
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception e, HttpServletRequest request) {
        return unexpected(e, request, "发生系统异常，请联系管理员");
    }

    /**
     * 参数校验失败：400
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResult<Void>> handleBindException(BindException e) {
        log.warn(e.getMessage());
        String message = StreamUtils.join(e.getAllErrors(), GlobalExceptionHandler::errorMessage, ", ");
        return ApiErrors.response(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 参数校验失败：400
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> constraintViolationException(ConstraintViolationException e) {
        log.warn(e.getMessage());
        String message = StreamUtils.join(e.getConstraintViolations(), ConstraintViolation::getMessage, ", ");
        return ApiErrors.response(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 参数校验失败：400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn(e.getMessage());
        String message = StreamUtils.join(e.getBindingResult().getAllErrors(), GlobalExceptionHandler::errorMessage, ", ");
        return ApiErrors.response(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 方法参数校验异常 用于处理 @Validated 注解：400
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResult<Void>> handlerMethodValidationException(HandlerMethodValidationException e) {
        log.warn(e.getMessage());
        String message = StreamUtils.join(e.getAllErrors(), GlobalExceptionHandler::errorMessage, ", ");
        return ApiErrors.response(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * JSON 解析异常（Jackson 在处理 JSON 格式出错时抛出）：400
     * 可能是请求体格式非法，也可能是服务端反序列化失败
     */
    @ExceptionHandler(JsonParseException.class)
    public ResponseEntity<ApiResult<Void>> handleJsonParseException(JsonParseException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}' 发生 JSON 解析异常: {}", requestURI, e.getMessage());
        return ApiErrors.response(HttpStatus.BAD_REQUEST, "请求数据格式错误");
    }

    /**
     * 请求体读取异常（通常是请求参数格式非法、字段类型不匹配等）：400。
     * <p>
     * 长度未知（分块传输）的请求体在读取中超过上限时，请求大小限制抛出的 {@link RequestBodyTooLargeException}
     * 会被消息转换器包装成本异常，按异常链识别出来返回 413，与声明了长度的超限请求一致
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e,
                                                                                 HttpServletRequest request) {
        if (findCause(e, RequestBodyTooLargeException.class) != null) {
            log.warn("请求地址'{}',请求体超过上限", request.getRequestURI());
            return ApiErrors.response(HttpStatus.PAYLOAD_TOO_LARGE, "请求体超过上限");
        }
        log.warn("请求地址'{}', 参数解析失败: {}", request.getRequestURI(), e.getMessage());
        return ApiErrors.response(HttpStatus.BAD_REQUEST, "请求参数格式错误");
    }

    /**
     * SpEL 表达式相关异常（数据权限、工作流条件等）：解析错误中有表达式、类名与字段名，只返回错误编号（500），详细信息记在日志中
     */
    @ExceptionHandler(ExpressionException.class)
    public ResponseEntity<ApiResult<Void>> handleSpelException(ExpressionException e, HttpServletRequest request) {
        String errorId = RandomUtil.randomNumbers(8);
        log.error("请求地址'{}'，SpEL解析异常, 错误编号: {}", request.getRequestURI(), errorId, e);
        return ApiErrors.response(HttpStatus.ERROR, "表达式解析失败，请联系管理员 [错误编号: " + errorId + "]");
    }

    /**
     * Spring MVC 判定的请求错误（自带 HTTP 状态）
     */
    private ResponseEntity<ApiResult<Void>> requestError(int status, Exception e, HttpServletRequest request) {
        if (status >= 500) {
            return unexpected(e, request, "发生未知异常，请联系管理员");
        }
        log.warn("请求地址'{}',请求错误（{}）:{}", request.getRequestURI(), status, e.getMessage());
        return ApiErrors.response(status, ApiErrors.statusMessage(status));
    }

    /**
     * 校验或绑定错误的提示：校验注解的提示原样使用（由开发者编写）；
     * 类型转换失败（例如 {@code pageSize=abc}）时 Spring 的默认消息带着类名与转换器的报错，用固定提示
     */
    static String errorMessage(MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError && fieldError.isBindingFailure()) {
            return invalidParameter(fieldError.getField());
        }
        return error.getDefaultMessage();
    }

    private static String invalidParameter(String name) {
        return String.format("参数[%s]格式不正确", name);
    }

    private static Throwable findCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 未预期的异常：500，只返回错误编号，详细信息按编号记在日志中
     */
    private ResponseEntity<ApiResult<Void>> unexpected(Exception e, HttpServletRequest request, String message) {
        String errorId = RandomUtil.randomNumbers(8);
        log.error("请求地址'{}',{}, 错误编号: {}", request.getRequestURI(), message, errorId, e);
        return ApiErrors.response(HttpStatus.ERROR, message + " [错误编号: " + errorId + "]");
    }

}
