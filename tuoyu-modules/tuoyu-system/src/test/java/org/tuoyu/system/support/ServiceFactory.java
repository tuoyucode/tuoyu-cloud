package org.tuoyu.system.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 按 public 构造器的参数类型，从给定的依赖中挑选对象创建被测类。
 * <p>
 * 服务实现类的构造器参数可能随实现增加，测试给出全部可能用到的依赖即可，
 * 多余的依赖不使用。只使用 public 构造器，不访问私有成员。
 *
 * @author tuoyu
 */
public final class ServiceFactory {

    private ServiceFactory() {
    }

    /**
     * @param type         被测类
     * @param dependencies 依赖（按类型匹配，同一类型取第一个）
     * @return 新对象
     */
    public static <T> T create(Class<T> type, Object... dependencies) {
        Constructor<?> constructor = Arrays.stream(type.getConstructors())
            .filter(c -> Modifier.isPublic(c.getModifiers()))
            .max(Comparator.comparingInt(Constructor::getParameterCount))
            .orElseThrow(() -> new IllegalStateException(type.getName() + " 没有 public 构造器"));
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            args[i] = Arrays.stream(dependencies)
                .filter(parameterType::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(type.getSimpleName() + " 的构造器需要 " + parameterType.getName()
                    + "，测试没有提供"));
        }
        try {
            return type.cast(constructor.newInstance(args));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("创建 " + type.getName() + " 失败", e);
        }
    }
}
