package org.tuoyu.common.mybatis.aspect;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;

/**
 * 数据权限通知：调用期间把本次调用的 {@link DataPermission} 注解设为当前注解，结束后（包括抛出异常时）恢复上一层。
 * <p>
 * 注解先取方法上的，没有时取目标对象的类上的（JDK 动态代理取它实现的第一个标注了的接口）；都没有时调用期间没有注解，
 * 这样外层方法的注解不会作用到内层未标注的方法。返回值与异常原样传出。
 *
 * @author tuoyu
 */
class DataPermissionAdvice implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        DataPermission previous = DataPermissionHelper.getPermission();
        DataPermissionHelper.setPermission(resolve(invocation));
        try {
            return invocation.proceed();
        } finally {
            if (previous == null) {
                DataPermissionHelper.removePermission();
            } else {
                DataPermissionHelper.setPermission(previous);
            }
        }
    }

    private static DataPermission resolve(MethodInvocation invocation) {
        DataPermission permission = invocation.getMethod().getAnnotation(DataPermission.class);
        if (permission != null) {
            return permission;
        }
        Object target = invocation.getThis();
        return target == null ? null : DataPermissionPointcut.findTypeAnnotation(target.getClass());
    }

}
