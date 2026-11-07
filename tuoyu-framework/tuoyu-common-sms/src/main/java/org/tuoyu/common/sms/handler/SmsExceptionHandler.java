package org.tuoyu.common.sms.handler;

import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.springframework.core.annotation.Order;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.springframework.http.ResponseEntity;
import org.dromara.sms4j.comm.exception.SmsBlendException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SMS异常处理器
 *
 * @author AprilWind
 */
@Slf4j
@Order(ExceptionHandlerOrder.MODULE)
@RestControllerAdvice
public class SmsExceptionHandler {

    /**
     * sms异常
     */
    @ExceptionHandler(SmsBlendException.class)
    public ResponseEntity<ApiResult<Void>> handleSmsBlendException(SmsBlendException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生sms短信异常.", requestURI, e);
        return ApiErrors.response(HttpStatus.ERROR, "短信发送失败，请稍后再试...");
    }

}
