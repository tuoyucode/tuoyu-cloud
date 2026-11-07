package org.tuoyu.common.core.utils;

import io.github.linpeilie.Converter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 对象转换（实体、Bo、Vo 之间）。转换规则由类上的 mapstruct-plus 注解（{@code @AutoMapper}）在编译时生成，
 * 运行时使用容器中的 {@link Converter}。
 * <p>
 * 空值安全：来源为 null 时返回 null，列表为 null 或空时返回空列表。
 *
 * @author tuoyu
 */
public final class BeanMapper {

    private BeanMapper() {
    }

    /**
     * 转换为新对象。来源是 {@code Map<String, Object>} 时按键名填充目标对象
     *
     * @param source     来源，可以为 null
     * @param targetType 目标类型
     * @return 新对象；来源为 null 时为 null
     */
    @SuppressWarnings("unchecked")
    public static <S, T> T map(S source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        requireTargetType(targetType);
        if (source instanceof Map<?, ?> map) {
            return converter().convert((Map<String, Object>) map, targetType);
        }
        return converter().convert(source, targetType);
    }

    /**
     * 把来源的属性复制到已有对象
     *
     * @param source 来源，可以为 null
     * @param target 目标对象，不能为 null
     * @return 目标对象；来源为 null 时原样返回目标对象
     */
    public static <S, T> T map(S source, T target) {
        if (target == null) {
            throw new IllegalArgumentException("目标对象不能为空");
        }
        if (source == null) {
            return target;
        }
        return converter().convert(source, target);
    }

    /**
     * 逐个转换
     *
     * @param sources    来源列表，可以为 null
     * @param targetType 目标类型
     * @return 新列表（可修改）；来源为 null 或空时为空列表
     */
    public static <S, T> List<T> mapList(Collection<S> sources, Class<T> targetType) {
        requireTargetType(targetType);
        if (sources == null || sources.isEmpty()) {
            return new ArrayList<>();
        }
        List<S> list = sources instanceof List<S> l ? l : new ArrayList<>(sources);
        List<T> converted = converter().convert(list, targetType);
        return converted == null ? new ArrayList<>() : new ArrayList<>(converted);
    }

    private static void requireTargetType(Class<?> targetType) {
        if (targetType == null) {
            throw new IllegalArgumentException("目标类型不能为空");
        }
    }

    private static Converter converter() {
        return SpringUtils.getBean(Converter.class);
    }

}
