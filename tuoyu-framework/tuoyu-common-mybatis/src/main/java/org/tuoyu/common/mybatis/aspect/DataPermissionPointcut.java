package org.tuoyu.common.mybatis.aspect;

import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.tuoyu.common.mybatis.annotation.DataPermission;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 数据权限切点：方法上直接标注了 {@link DataPermission}，或目标类上标注了它时匹配。
 * <p>
 * 目标类是 JDK 动态代理（MyBatis 的 Mapper 就是）时，看它直接实现的接口（Mapper 接口）上是否标注；
 * 否则看目标类本身。都不向父类、父接口查找，与注解不可继承一致。类过滤器接受所有类。
 *
 * @author tuoyu
 */
class DataPermissionPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return method.isAnnotationPresent(DataPermission.class) || findTypeAnnotation(targetClass) != null;
    }

    /**
     * @param type 目标类，可以为 null
     * @return 目标类（JDK 动态代理时为它实现的第一个标注了的接口）上的注解；没有时为 null
     */
    static DataPermission findTypeAnnotation(Class<?> type) {
        if (type == null) {
            return null;
        }
        if (Proxy.isProxyClass(type)) {
            for (Class<?> face : type.getInterfaces()) {
                DataPermission permission = face.getAnnotation(DataPermission.class);
                if (permission != null) {
                    return permission;
                }
            }
            return null;
        }
        return type.getAnnotation(DataPermission.class);
    }

}
