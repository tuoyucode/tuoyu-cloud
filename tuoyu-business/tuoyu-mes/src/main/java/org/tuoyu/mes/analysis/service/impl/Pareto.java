package org.tuoyu.mes.analysis.service.impl;

import org.tuoyu.mes.analysis.domain.vo.MesDefectParetoRowVo;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * 柏拉图（帕累托）排列：按不良数量从多到少排序（相同时记录数多的在前，再按名称），计算占比与累计占比。
 * <p>
 * 累计占比由累计数量直接计算（不是逐项占比相加），最后一项为 100.00，不会因四舍五入出现 99.99 或 100.01。
 *
 * @author tuoyu
 */
final class Pareto {

    private static final Comparator<MesDefectParetoRowVo> ORDER = Comparator
        .comparing((MesDefectParetoRowVo row) -> AnalysisMath.orZero(row.getDefectQty()), Comparator.reverseOrder())
        .thenComparing(MesDefectParetoRowVo::getRecordCount, Comparator.reverseOrder())
        .thenComparing(row -> row.getLabel() == null ? "" : row.getLabel());

    private Pareto() {
    }

    /**
     * 排序并填写占比，返回排序后的列表（新列表）；全部数量为 0 时占比为空
     */
    static List<MesDefectParetoRowVo> rank(List<MesDefectParetoRowVo> rows) {
        List<MesDefectParetoRowVo> sorted = rows.stream().sorted(ORDER).toList();
        BigDecimal total = sorted.stream().map(row -> AnalysisMath.orZero(row.getDefectQty()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal running = BigDecimal.ZERO;
        for (MesDefectParetoRowVo row : sorted) {
            BigDecimal qty = AnalysisMath.orZero(row.getDefectQty());
            running = running.add(qty);
            row.setRatio(AnalysisMath.percent(qty, total));
            row.setCumulativeRatio(AnalysisMath.percent(running, total));
        }
        return sorted;
    }

}
