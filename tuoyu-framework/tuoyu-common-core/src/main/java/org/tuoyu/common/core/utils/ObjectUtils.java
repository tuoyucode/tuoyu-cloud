package org.tuoyu.common.core.utils;

import java.util.function.Function;

/**
 * 空值安全的读取。
 *
 * @author tuoyu
 */
public final class ObjectUtils {

    private ObjectUtils() {
    }

    /**
     * @param obj  对象
     * @param func 读取函数
     * @param <T>  对象类型
     * @param <E>  结果类型
     * @return 对象与读取函数都不为 null 时为读取结果，否则为 null
     */
    public static <T, E> E notNullGetter(T obj, Function<T, E> func) {
        return obj != null && func != null ? func.apply(obj) : null;
    }

    /**
     * @param obj          对象
     * @param func         读取函数
     * @param defaultValue 默认值
     * @param <T>          对象类型
     * @param <E>          结果类型
     * @return 对象与读取函数都不为 null 时为读取结果（即使结果为 null），否则为默认值
     */
    public static <T, E> E notNullGetter(T obj, Function<T, E> func, E defaultValue) {
        return obj != null && func != null ? func.apply(obj) : defaultValue;
    }

    /**
     * @param obj          对象
     * @param defaultValue 默认值
     * @param <T>          对象类型
     * @return obj 不为 null 时为 obj，否则为默认值
     */
    public static <T> T notNull(T obj, T defaultValue) {
        return obj != null ? obj : defaultValue;
    }

    /**
     * @param obj 对象
     * @return 是否为 null，或是表示空值的对象（{@code obj.equals(null)} 为 true，例如 JSON 的 null）
     */
    @SuppressWarnings("PMD.EqualsNull")
    public static boolean isNull(Object obj) {
        // 有意调用 equals(null)：表示空值的对象（例如 JSON 库中的 null 对象）在这里返回 true
        return obj == null || obj.equals(null);
    }

}
