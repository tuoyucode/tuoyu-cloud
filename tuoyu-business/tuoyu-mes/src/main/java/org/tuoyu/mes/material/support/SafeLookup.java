package org.tuoyu.mes.material.support;

import java.util.Map;

/**
 * 按可能为空的 ID 取关联对象：{@code MesLookups.byIds} 没有 ID 时返回不可变的空 Map，
 * 它不接受 null 键，直接 {@code get(null)} 会抛出空指针异常
 *
 * @author tuoyu
 */
public final class SafeLookup {

    private SafeLookup() {
    }

    /**
     * ID 为空或没有对应对象时返回 null
     */
    public static <T> T get(Map<Long, T> map, Long id) {
        return id == null ? null : map.get(id);
    }

}
