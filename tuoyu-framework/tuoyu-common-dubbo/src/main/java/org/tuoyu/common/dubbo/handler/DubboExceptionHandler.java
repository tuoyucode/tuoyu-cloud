package org.tuoyu.common.dubbo.handler;

import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.springframework.core.annotation.Order;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.RpcException;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Dubbo异常处理器
 *
 * @author Lion Li
 */
@Slf4j
@Order(ExceptionHandlerOrder.MODULE)
@RestControllerAdvice
public class DubboExceptionHandler {

    /**
     * 跨服务调用失败（超时、没有提供方、序列化失败等）：500，不返回异常细节
     */
    @ExceptionHandler(RpcException.class)
    public ResponseEntity<ApiResult<Void>> handleDubboException(RpcException e) {
        log.error("RPC异常: {}", e.getMessage());
        return ApiErrors.response(HttpStatus.ERROR, "RPC异常，请联系管理员确认");
    }

}
