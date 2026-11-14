package org.tuoyu.common.mybatis.aspect;

import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;

/**
 * 数据权限切面：调用标注了 {@code @DataPermission} 的方法（或标注在 Mapper 接口、Service 类上时其中的方法）期间，
 * 把注解放进当前线程，结束后恢复上一层；期间执行的 SQL 由数据权限拦截器按注解改写。
 * <p>
 * 只有经过 Spring 代理的调用才会经过本切面：MyBatis-Plus {@code BaseMapper} 的默认方法在 MyBatis 代理内部转调其他方法，
 * 不经过 Spring 代理，所以带数据权限的 Mapper 要把业务会调用的默认方法也覆盖并标注。
 * <p>
 * 每个实例持有自己的一对切点与通知，都没有状态。
 *
 * @author tuoyu
 */
public class DataPermissionPointcutAdvisor extends AbstractPointcutAdvisor {

    private final Pointcut pointcut = new DataPermissionPointcut();

    private final Advice advice = new DataPermissionAdvice();

    public DataPermissionPointcutAdvisor() {
        // 切点与通知都没有状态，构造时直接创建
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
