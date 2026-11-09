package org.tuoyu.mes.common;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 列表中按 ID 批量取关联对象（名称、编码等），避免逐行查询。
 *
 * @author tuoyu
 */
public final class MesLookups {

    private MesLookups() {
    }

    /**
     * 按 ID 批量查询，返回 ID → 实体
     *
     * @param mapper 关联对象的 Mapper
     * @param ids    ID（可含 null 与重复值）
     * @param idOf   取实体 ID 的方法
     * @return ID → 实体；没有 ID 时为空。返回的 Map 可以用 null 取值（得到 null），关联 ID 为空的行不必单独判断
     */
    public static <T> Map<Long, T> byIds(BaseMapper<T> mapper, Collection<Long> ids, Function<T, Long> idOf) {
        Set<Long> distinct = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, T> result = new HashMap<>();
        if (distinct.isEmpty()) {
            return result;
        }
        for (T item : mapper.selectByIds(distinct)) {
            result.putIfAbsent(idOf.apply(item), item);
        }
        return result;
    }

    /**
     * 从一组对象中取出关联 ID
     */
    public static <V> Set<Long> ids(Collection<V> items, Function<V, Long> idOf) {
        return items.stream().map(idOf).filter(Objects::nonNull).collect(Collectors.toSet());
    }

}
