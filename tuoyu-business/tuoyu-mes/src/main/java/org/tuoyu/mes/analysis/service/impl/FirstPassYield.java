package org.tuoyu.mes.analysis.service.impl;

import org.tuoyu.mes.analysis.domain.row.FirstPassRow;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 直通率（一次合格率，First Pass Yield）。
 * <p>
 * 口径：
 * <ol>
 *     <li>一个工单的直通率 = 它在统计范围内有报工的各道工序的良率（合格数 ÷ 报工数）之积；
 *     同一工序任务的多次报工先合计；</li>
 *     <li>一组工单（同一日期区间、产品或产线）的直通率 = 各工单直通率的加权平均，
 *     权重为工单在统计范围内<b>最前一道有报工的工序</b>的报工数（相当于投入数），投入多的工单影响大；</li>
 *     <li>没有报工（权重合计为 0）时为空。</li>
 * </ol>
 * 结果为百分数，保留两位小数。
 *
 * @author tuoyu
 */
final class FirstPassYield {

    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private FirstPassYield() {
    }

    /**
     * 一组计算单元（可以含多个工单、同一工序任务的多行）的直通率
     */
    static BigDecimal of(Collection<FirstPassRow> rows) {
        Map<Long, Map<Long, Unit>> byWorkorder = new LinkedHashMap<>();
        for (FirstPassRow row : rows) {
            if (row.getWorkorderId() == null || row.getTaskId() == null) {
                continue;
            }
            byWorkorder.computeIfAbsent(row.getWorkorderId(), id -> new LinkedHashMap<>())
                .computeIfAbsent(row.getTaskId(), id -> new Unit(row.getSeqNo()))
                .add(row.getReportQty(), row.getQualifiedQty());
        }
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        for (Map<Long, Unit> tasks : byWorkorder.values()) {
            BigDecimal product = BigDecimal.ONE;
            Unit first = null;
            for (Unit unit : tasks.values()) {
                if (unit.reported.signum() <= 0) {
                    continue;
                }
                product = product.multiply(unit.qualified.divide(unit.reported, PRECISION), PRECISION);
                if (first == null || unit.seqNo < first.seqNo) {
                    first = unit;
                }
            }
            if (first == null) {
                continue;
            }
            weighted = weighted.add(product.multiply(first.reported, PRECISION), PRECISION);
            weights = weights.add(first.reported);
        }
        if (weights.signum() <= 0) {
            return null;
        }
        return weighted.multiply(AnalysisMath.HUNDRED).divide(weights, AnalysisMath.SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 一个工序任务的报工合计
     */
    private static final class Unit {

        private final int seqNo;

        private BigDecimal reported = BigDecimal.ZERO;

        private BigDecimal qualified = BigDecimal.ZERO;

        private Unit(Integer seqNo) {
            this.seqNo = Objects.requireNonNullElse(seqNo, Integer.MAX_VALUE);
        }

        private void add(BigDecimal reportQty, BigDecimal qualifiedQty) {
            reported = reported.add(AnalysisMath.orZero(reportQty));
            qualified = qualified.add(AnalysisMath.orZero(qualifiedQty));
        }
    }

}
