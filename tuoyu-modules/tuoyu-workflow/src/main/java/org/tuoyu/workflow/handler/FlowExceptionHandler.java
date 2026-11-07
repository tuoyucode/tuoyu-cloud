package org.tuoyu.workflow.handler;

import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.springframework.core.annotation.Order;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.springframework.http.ResponseEntity;
import org.dromara.warm.flow.core.exception.FlowException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 工作流失败异常：流程引擎拒绝的操作（例如流程已结束、节点不存在），按业务异常返回 400
 *
 * @author Lion Li
 */
@Slf4j
@Order(ExceptionHandlerOrder.MODULE)
@RestControllerAdvice
public class FlowExceptionHandler {

    /**
     * 工作流失败异常。
     *
     * @param e       异常信息
     * @param request 当前请求
     * @return 400 响应
     */
    @ExceptionHandler(FlowException.class)
    public ResponseEntity<ApiResult<Void>> handleFlowException(FlowException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}'", requestURI, e);
        return ApiErrors.response(HttpStatus.BAD_REQUEST, e.getMessage());
    }

}
