package org.tuoyu.common.core.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 集合转换。
 * <p>
 * 集合参数为 null 或空时返回可修改的空集合；返回的集合都可以修改（不可修改的集合在序列化等场景中会出问题）。
 *
 * @author tuoyu
 */
public final class StreamUtils {

    private StreamUtils() {
    }

    /**
     * @param collection 集合
     * @param function   条件，null 元素也交给它判断
     * @param <E>        元素类型
     * @return 满足条件的元素，保持原顺序
     */
    public static <E> List<E> filter(Collection<E> collection, Predicate<E> function) {
        List<E> result = new ArrayList<>();
        if (isEmpty(collection)) {
            return result;
        }
        for (E element : collection) {
            if (function.test(element)) {
                result.add(element);
            }
        }
        return result;
    }

    /**
     * @param collection 集合
     * @param function   条件
     * @param <E>        元素类型
     * @return 第一个满足条件的元素；没有时为空
     */
    public static <E> Optional<E> findFirst(Collection<E> collection, Predicate<E> function) {
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        for (E element : collection) {
            if (function.test(element)) {
                return Optional.ofNullable(element);
            }
        }
        return Optional.empty();
    }

    /**
     * @param collection 集合
     * @param function   映射函数，结果为 null 的元素跳过
     * @param <E>        元素类型
     * @return 映射结果用逗号连接；集合为空时为空串
     */
    public static <E> String join(Collection<E> collection, Function<E, String> function) {
        return join(collection, function, StringUtils.SEPARATOR);
    }

    /**
     * @param collection 集合
     * @param function   映射函数，结果为 null 的元素跳过
     * @param delimiter  分隔符
     * @param <E>        元素类型
     * @return 映射结果用分隔符连接；集合为空时为空串
     */
    public static <E> String join(Collection<E> collection, Function<E, String> function, CharSequence delimiter) {
        if (isEmpty(collection)) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(delimiter);
        for (E element : collection) {
            String value = function.apply(element);
            if (value != null) {
                joiner.add(value);
            }
        }
        return joiner.toString();
    }

    /**
     * @param collection 集合
     * @param key        键的映射函数
     * @param <V>        元素类型
     * @param <K>        键的类型
     * @return 键 → 元素；跳过 null 元素，键重复时保留第一个
     */
    public static <V, K> Map<K, V> toIdentityMap(Collection<V> collection, Function<V, K> key) {
        Map<K, V> result = new HashMap<>();
        if (isEmpty(collection)) {
            return result;
        }
        for (V element : collection) {
            if (element != null) {
                result.putIfAbsent(key.apply(element), element);
            }
        }
        return result;
    }

    /**
     * @param collection 集合
     * @param key        键的映射函数
     * @param value      值的映射函数，结果不能为 null
     * @param <E>        元素类型
     * @param <K>        键的类型
     * @param <V>        值的类型
     * @return 键 → 值；跳过 null 元素，键重复时保留第一个
     * @throws NullPointerException 值的映射结果为 null 时
     */
    public static <E, K, V> Map<K, V> toMap(Collection<E> collection, Function<E, K> key, Function<E, V> value) {
        Map<K, V> result = new HashMap<>();
        if (isEmpty(collection)) {
            return result;
        }
        for (E element : collection) {
            if (element == null) {
                continue;
            }
            K k = key.apply(element);
            V v = Objects.requireNonNull(value.apply(element), "映射得到的值不能为 null");
            result.putIfAbsent(k, v);
        }
        return result;
    }

    /**
     * @param collection 集合
     * @param key        分组键的映射函数
     * @param <E>        元素类型
     * @param <K>        键的类型
     * @return 键 → 该组的元素；跳过 null 元素，键按首次出现的顺序，组内保持原顺序
     */
    public static <E, K> Map<K, List<E>> groupByKey(Collection<E> collection, Function<E, K> key) {
        Map<K, List<E>> result = new LinkedHashMap<>();
        if (isEmpty(collection)) {
            return result;
        }
        for (E element : collection) {
            if (element != null) {
                result.computeIfAbsent(key.apply(element), k -> new ArrayList<>()).add(element);
            }
        }
        return result;
    }

    /**
     * @param collection 集合
     * @param function   映射函数，null 元素也交给它，结果为 null 的跳过
     * @param <E>        元素类型
     * @param <T>        结果类型
     * @return 映射结果，保持原顺序
     */
    public static <E, T> List<T> toList(Collection<E> collection, Function<E, T> function) {
        List<T> result = new ArrayList<>();
        if (isEmpty(collection)) {
            return result;
        }
        for (E element : collection) {
            T value = function.apply(element);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * @param collection 集合
     * @param function   映射函数，null 元素也交给它，结果为 null 的跳过
     * @param <E>        元素类型
     * @param <T>        结果类型
     * @return 映射结果
     */
    public static <E, T> Set<T> toSet(Collection<E> collection, Function<E, T> function) {
        Set<T> result = new HashSet<>();
        if (isEmpty(collection)) {
            return result;
        }
        for (E element : collection) {
            T value = function.apply(element);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

}
