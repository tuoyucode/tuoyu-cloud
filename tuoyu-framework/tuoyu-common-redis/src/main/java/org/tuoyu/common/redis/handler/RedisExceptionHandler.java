package org.tuoyu.common.redis.handler;

import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.springframework.core.annotation.Order;
import com.baomidou.lock.exception.LockFailureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.redis.config.Lock4jConfiguration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Redis异常处理器
 *
 * @author AprilWind
 */
@Slf4j
@Order(ExceptionHandlerOrder.MODULE)
@RestControllerAdvice
public class RedisExceptionHandler {

    /**
     * Lock4j 自己的获取锁失败异常：默认策略（{@link Lock4jConfiguration}）抛出 429 的业务异常，由全局异常处理返回；
     * 这里处理 {@code @Lock4j(failStrategy = ...)} 指定的其他策略抛出的 Lock4j 异常，状态与默认策略一致（429）
     */
    @ExceptionHandler(LockFailureException.class)
    public ResponseEntity<ApiResult<Void>> handleLockFailureException(LockFailureException e, HttpServletRequest request) {
        log.warn("获取锁失败 '{}'：{}", request.getRequestURI(), e.getMessage());
        return ApiErrors.response(HttpStatus.TOO_MANY_REQUESTS, Lock4jConfiguration.LOCK_FAILURE_MESSAGE);
    }

}
