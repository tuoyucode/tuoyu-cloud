package org.tuoyu.mes.trace.service;

import org.tuoyu.mes.trace.domain.vo.MesTraceVo;

/**
 * 追溯查询：按序列号、生产批次号、物料批次号或工单编号查出工单的完整生产记录（只读）
 *
 * @author tuoyu
 */
public interface IMesTraceService {

    /**
     * 按关键字追溯，依次尝试：序列号 → 生产批次号 → 物料批次号 → 工单编号，先匹配到的为准
     *
     * @param keyword 关键字
     * @return 追溯结果；没有匹配时 matchType 为空、工单列表为空
     */
    MesTraceVo trace(String keyword);

}
