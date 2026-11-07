package org.tuoyu.common.core.web;

import org.springframework.core.Ordered;

/**
 * 异常处理类（{@code @RestControllerAdvice}）的顺序。
 * <p>
 * Spring MVC 按顺序逐个查找能处理异常的类，先找到的生效；全局异常处理有兜底的 {@code RuntimeException}、
 * {@code Exception} 方法，如果排在前面，模块专用的处理（数据库、认证、远程调用等）就不会生效。
 * 所以模块专用的处理类标 {@code @Order(ExceptionHandlerOrder.MODULE)}，全局（兜底）的标
 * {@code @Order(ExceptionHandlerOrder.GLOBAL)}，不依赖自动配置的加载顺序。
 *
 * @author tuoyu
 */
public final class ExceptionHandlerOrder {

    /**
     * 模块专用的异常处理，排在全局异常处理之前
     */
    public static final int MODULE = Ordered.LOWEST_PRECEDENCE - 100;

    /**
     * 全局（兜底）的异常处理，排在最后
     */
    public static final int GLOBAL = Ordered.LOWEST_PRECEDENCE;

    private ExceptionHandlerOrder() {
    }

}
