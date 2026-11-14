package org.tuoyu.common.core.utils;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 静态访问 Spring 容器。
 * <p>
 * 注册为 Bean 后，容器启动时把自己存入 hutool 的静态字段，之后经本类调用继承的静态方法
 * （{@code getBean}、{@code getBeanFactory}、{@code getProperty}、{@code getApplicationName} 等）。
 * 静态方法每次调用时才访问容器，不能在类加载时调用。
 *
 * @author tuoyu
 */
@Component
public final class SpringUtils extends SpringUtil {

    public SpringUtils() {
        // 由容器创建，回调方法把容器存入静态字段
    }

    /**
     * @return 当前的应用上下文
     */
    public static ApplicationContext context() {
        return getApplicationContext();
    }

    /**
     * 取得 Bean 的代理对象：在 Bean 内部用 {@code this} 调用时，经代理调用才能使 {@code @Transactional} 等生效。
     *
     * @param invoker Bean 内部的 {@code this}
     * @param <T>     Bean 的类型
     * @return 容器中该类型的 Bean（即代理对象）
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAopProxy(T invoker) {
        return (T) getBean(invoker.getClass());
    }

    /**
     * @param name Bean 名称
     * @return 容器中是否有该名称的 Bean
     */
    public static boolean containsBean(String name) {
        return getBeanFactory().containsBean(name);
    }

}
