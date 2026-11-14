package org.tuoyu.common.core.utils.reflect;

import cn.hutool.core.util.ReflectUtil;
import org.tuoyu.common.core.utils.StringUtils;

/**
 * 反射工具。
 * <p>
 * 继承 hutool 的 {@link ReflectUtil}（{@code getField}、{@code getFields}、{@code setFieldValue} 等经本类调用）。
 *
 * @author tuoyu
 */
public class ReflectUtils extends ReflectUtil {

    private static final String GETTER_PREFIX = "get";

    private ReflectUtils() {
    }

    /**
     * 按属性路径逐级调用 getter，例如 {@code dept.leader.name} 依次调用 {@code getDept()}、{@code getLeader()}、
     * {@code getName()}，上一级的返回值作为下一级的对象。
     * <p>
     * getter 可以在父类中声明，也可以不是 public；只认 {@code get} 前缀。
     *
     * @param obj          对象
     * @param propertyName 属性路径，用 {@code .} 分隔，空段忽略
     * @param <E>          结果类型
     * @return 最后一级的值
     * @throws cn.hutool.core.exceptions.UtilException 某一级的 getter 不存在时
     * @throws IllegalArgumentException                某一级的对象为 null 时
     */
    @SuppressWarnings("unchecked")
    public static <E> E invokeGetter(Object obj, String propertyName) {
        Object current = obj;
        for (String name : StringUtils.split(propertyName, '.')) {
            if (current == null) {
                throw new IllegalArgumentException("读取属性 " + propertyName + " 时遇到 null，无法继续读取 " + name);
            }
            current = invoke(current, GETTER_PREFIX + StringUtils.capitalize(name));
        }
        return (E) current;
    }

}
