package org.tuoyu.common.tenant.mybatis;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.tuoyu.common.mybatis.annotation.TenantIgnore;
import org.tuoyu.common.tenant.core.TenantHelper;

/**
 * {@link TenantIgnore} 标注的方法（或类中的全部方法）在忽略租户隔离的代码块中执行。
 * 作用于 Spring Bean，包括 Mapper 的默认方法（Mapper 代理也是 Spring Bean）
 *
 * @author tuoyu
 */
public class TenantIgnoreAdvisor extends AbstractPointcutAdvisor {

    private final Pointcut pointcut = new ComposablePointcut(new AnnotationMatchingPointcut(TenantIgnore.class, true))
        .union(new AnnotationMatchingPointcut(null, TenantIgnore.class, true));

    private final MethodInterceptor advice = invocation -> TenantHelper.ignore(() -> {
        try {
            return invocation.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    });

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public org.aopalliance.aop.Advice getAdvice() {
        return advice;
    }

}
