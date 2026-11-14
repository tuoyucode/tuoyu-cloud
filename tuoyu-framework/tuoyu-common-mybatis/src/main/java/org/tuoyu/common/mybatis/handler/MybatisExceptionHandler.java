package org.tuoyu.common.mybatis.handler;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.dynamic.datasource.exception.CannotFindDataSourceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.core.web.ExceptionHandlerOrder;

/**
 * 数据访问异常的响应。
 * <p>
 * 数据库报错中可能有 SQL、表名、字段与数据，一律不返回客户端：主键或唯一索引冲突返回固定提示（409），
 * 其他异常只返回错误编号（500），详细信息按编号记在日志中。根因是业务异常（例如 SQL 拦截器中的租户校验）时，
 * 按业务异常的状态与提示返回。作为模块级的异常处理，排在全局异常处理之前。
 *
 * @author tuoyu
 */
@Slf4j
@Order(ExceptionHandlerOrder.MODULE)
@RestControllerAdvice
public class MybatisExceptionHandler {

    public MybatisExceptionHandler() {
    }

    /**
     * 主键或唯一索引冲突
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResult<Void>> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        log.warn("请求地址'{}'，数据库中已存在记录：{}", request.getRequestURI(), e.getMessage());
        return ApiErrors.response(HttpStatus.CONFLICT, "数据库中已存在该记录，请联系管理员确认");
    }

    /**
     * MyBatis 执行时的异常，按根因区分
     */
    @ExceptionHandler(MyBatisSystemException.class)
    public ResponseEntity<ApiResult<Void>> handleMyBatisSystemException(MyBatisSystemException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        Throwable root = getRootCause(e);
        if (root instanceof ServiceException serviceException) {
            log.warn("请求地址'{}'，业务异常：{}", uri, serviceException.getMessage());
            return ApiErrors.response(serviceException);
        }
        if (root instanceof CannotFindDataSourceException) {
            log.error("请求地址'{}'，未找到数据源：{}", uri, root.getMessage());
            return ApiErrors.response(HttpStatus.ERROR, "未找到数据源，请联系管理员确认");
        }
        String errorId = RandomUtil.randomNumbers(8);
        log.error("请求地址'{}'，数据访问异常，错误编号 {}", uri, errorId, e);
        return ApiErrors.response(HttpStatus.ERROR, "数据访问异常，请联系管理员 [错误编号: " + errorId + "]");
    }

    /**
     * 沿 cause 取最底层的异常；cause 指向自身时停止
     */
    static Throwable getRootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /**
     * 沿 cause 链（含自身）找第一个指定类型的异常
     *
     * @return 找到的异常，没有时为 null
     */
    static <E extends Throwable> E findCause(Throwable e, Class<E> type) {
        Throwable current = e;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return null;
    }

}
