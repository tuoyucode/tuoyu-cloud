package org.tuoyu.mes.common;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * 按编码批量查询时每次 IN 的数量
     */
    static final int CODE_CHUNK = 500;

    private MesLookups() {
    }

    /**
     * 按编码批量查询（导入时把编码换成 ID、检查编码是否已存在），返回 {@link #codeKey(String)} → 实体
     * <p>
     * 键统一转为小写：MySQL 默认的排序规则比较时不区分大小写，查回的编码可能与传入的大小写不同，按原样取会对不上
     *
     * @param mapper     Mapper
     * @param type       实体类型
     * @param codeColumn 编码列
     * @param codes      编码（可含 null 与重复值）
     * @return 编码键 → 实体
     */
    public static <T> Map<String, T> byCodes(BaseMapper<T> mapper, Class<T> type, SFunction<T, String> codeColumn,
                                             Collection<String> codes) {
        Map<String, T> result = new HashMap<>();
        for (List<String> chunk : chunks(codes)) {
            for (T item : mapper.selectList(QueryBuilder.lambda(type).in(codeColumn, chunk).build())) {
                result.putIfAbsent(codeKey(codeColumn.apply(item)), item);
            }
        }
        return result;
    }

    /**
     * 去掉 null 与重复值后按每批 {@value #CODE_CHUNK} 个分开，用于 IN 查询（避免一条 SQL 中的参数过多）
     */
    public static <V> List<List<V>> chunks(Collection<V> values) {
        List<V> distinct = new ArrayList<>(values.stream().filter(Objects::nonNull).collect(Collectors.toSet()));
        List<List<V>> chunks = new ArrayList<>();
        for (int from = 0; from < distinct.size(); from += CODE_CHUNK) {
            chunks.add(distinct.subList(from, Math.min(distinct.size(), from + CODE_CHUNK)));
        }
        return chunks;
    }

    /**
     * 编码比较用的键：不区分大小写
     */
    public static String codeKey(String code) {
        return code == null ? null : code.toLowerCase(Locale.ROOT);
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
