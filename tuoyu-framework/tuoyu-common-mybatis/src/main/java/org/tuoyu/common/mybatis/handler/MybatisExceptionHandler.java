package org.tuoyu.common.mybatis.handler;

import org.tuoyu.common.core.web.ExceptionHandlerOrder;
import org.springframework.core.annotation.Order;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.dynamic.datasource.exception.CannotFindDataSourceException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.web.ApiErrors;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mybatis异常处理器
 *
 * @author Lion Li
 */
@Slf4j
@Order(ExceptionHandlerOrder.MODULE)
@RestControllerAdvice
public class MybatisExceptionHandler {

    /**
     * 主键或UNIQUE索引，数据重复异常：409
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResult<Void>> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        log.warn("请求地址'{}',数据库中已存在记录'{}'", requestURI, e.getMessage());
        return ApiErrors.response(HttpStatus.CONFLICT, "数据库中已存在该记录，请联系管理员确认");
    }

    /**
     * Mybatis系统异常 通用处理。
     * <p>
     * 异常消息中可能有 SQL 语句、表名、字段与数据库的报错，不能返回给客户端：只返回通用提示与错误编号（500），
     * 详细信息按错误编号记在日志中。根因是业务异常（ServiceException，例如租户校验失败、未登录）时，它的消息本来就是给用户看的，
     * 按业务异常的规则返回
     */
    @ExceptionHandler(MyBatisSystemException.class)
    public ResponseEntity<ApiResult<Void>> handleMyBatisSystemException(MyBatisSystemException e, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        Throwable root = getRootCause(e);
        if (root instanceof ServiceException se) {
            log.warn("请求地址'{}', 数据访问中的业务异常'{}'", requestURI, se.getMessage());
            return ApiErrors.response(se);
        }
        if (root instanceof CannotFindDataSourceException) {
            log.error("请求地址'{}', 未找到数据源", requestURI);
            return ApiErrors.response(HttpStatus.ERROR, "未找到数据源，请联系管理员确认");
        }
        String errorId = RandomUtil.randomNumbers(8);
        log.error("请求地址'{}', Mybatis系统异常, 错误编号: {}", requestURI, errorId, e);
        return ApiErrors.response(HttpStatus.ERROR, "数据访问异常，请联系管理员 [错误编号: " + errorId + "]");
    }

    /**
     * 获取异常的根因（递归查找）
     *
     * @param e 当前异常
     * @return 根因异常（最底层的 cause）
     * <p>
     * 逻辑说明：
     * 1. 如果 e 没有 cause，说明 e 本身就是根因，直接返回
     * 2. 如果 e 的 cause 和自身相同（防止循环引用），也返回 e
     * 3. 否则递归调用，继续向下寻找最底层的 cause
     */
    public static Throwable getRootCause(Throwable e) {
        Throwable cause = e.getCause();
        if (cause == null || cause == e) {
            return e;
        }
        return getRootCause(cause);
    }

    /**
     * 在异常链中查找指定类型的异常
     *
     * @param e     当前异常
     * @param clazz 目标异常类
     * @return 找到的指定类型异常，如果没有找到返回 null
     */
    public static Throwable findCause(Throwable e, Class<? extends Throwable> clazz) {
        Throwable t = e;
        while (t != null && t != t.getCause()) {
            if (clazz.isInstance(t)) {
                return t;
            }
            t = t.getCause();
        }
        return null;
    }

}
