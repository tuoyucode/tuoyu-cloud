package org.tuoyu.boot.datasource;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 单体中按模块切换数据源。
 * <p>
 * 单体保持与微服务相同的库划分：system、resource、gen 等使用 tuoyu-cloud，workflow 使用 tuoyu-workflow。
 * 调用某个模块的 Bean（Controller、Service 等 {@code @Component}）时，切换到该模块的数据源，调用结束后恢复。
 * <ul>
 *     <li>排在事务切面之前：事务开始时取连接，必须先确定数据源；</li>
 *     <li>排在 {@code @DS} 切面之前：方法上显式标注的 {@code @DS}（例如代码生成按所选数据源读取表结构）仍然生效；</li>
 *     <li>跨模块调用经过本地调用代理，代理挂起调用方事务后进入提供方的 Bean，在这里切换到提供方的库。
 *     所以每个访问数据库的模块都要登记，未登记的 Bean（例如框架组件）沿用调用方的数据源。</li>
 * </ul>
 *
 * @author tuoyu
 */
public class ModuleDataSourceAdvisor extends AbstractPointcutAdvisor {

    /**
     * 包 → 数据源，按包名从长到短匹配
     */
    private final List<Map.Entry<String, String>> routes;

    private final transient Pointcut pointcut = new StaticMethodMatcherPointcut() {

        @Override
        public ClassFilter getClassFilter() {
            return ModuleDataSourceAdvisor.this::isCandidate;
        }

        @Override
        public boolean matches(Method method, Class<?> targetClass) {
            return method.getDeclaringClass() != Object.class;
        }
    };

    private final transient Advice advice = (MethodInterceptor) invocation -> {
        Object target = invocation.getThis();
        String dataSource = target == null ? null : route(AopUtils.getTargetClass(target));
        if (dataSource == null) {
            return invocation.proceed();
        }
        DynamicDataSourceContextHolder.push(dataSource);
        try {
            return invocation.proceed();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    };

    /**
     * @param routes 包 → 数据源名（dynamic-datasource 中的名称）
     */
    public ModuleDataSourceAdvisor(Map<String, String> routes) {
        this.routes = routes.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
            .map(e -> Map.entry(e.getKey(), e.getValue()))
            .toList();
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    /**
     * 类所属模块的数据源，不属于任何登记的包时为 null
     */
    public String route(Class<?> type) {
        return route(type.getName());
    }

    /**
     * 按类的全名匹配，包名按整段比较（org.tuoyu.system 不匹配 org.tuoyu.systemx）
     */
    String route(String className) {
        for (Map.Entry<String, String> route : routes) {
            if (className.startsWith(route.getKey() + ".")) {
                return route.getValue();
            }
        }
        return null;
    }

    /**
     * 只代理登记包中的组件：配置类不需要；final 类无法生成子类代理，跳过（这类组件若访问数据库，沿用调用方的数据源）
     */
    boolean isCandidate(Class<?> type) {
        return route(type) != null
            && !type.isInterface()
            && !Modifier.isFinal(type.getModifiers())
            && AnnotatedElementUtils.hasAnnotation(type, Component.class)
            && !AnnotatedElementUtils.hasAnnotation(type, Configuration.class);
    }

    public List<String> dataSources() {
        return routes.stream().map(Map.Entry::getValue).distinct().toList();
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }

}
