package org.tuoyu.mes.plan.service.impl;

import java.util.Map;

/**
 * 从批量查询的结果中按 ID 取关联对象。关联 ID 可以为空（例如未指定产线），为空时直接返回空，
 * 不依赖结果集合是否允许空键
 *
 * @author tuoyu
 */
public final class PlanLookups {

    private PlanLookups() {
    }

    public static <T> T get(Map<Long, T> items, Long id) {
        return id == null ? null : items.get(id);
    }

}
