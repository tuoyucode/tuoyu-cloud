package org.tuoyu.mes.equipment.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * OEE 的一天或汇总：基础数据与四项指标。
 * <p>
 * 指标为百分数（保留两位小数）；不能计算时为空，原因见对应的状态（{@code OeeMetricStatus}：ok 已计算、
 * unconfigured 未配置、no_data 无数据、idle 无生产），界面按状态显示“未配置”等，不当作 0 或 100%
 *
 * @author tuoyu
 */
@Data
public class MesOeeStatVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日期（汇总时为空）
     */
    private LocalDate statDate;

    /**
     * 参与汇总的生产日数（只在汇总中有值）
     */
    private Integer activeDays;

    /**
     * 计划生产时间（分钟），未配置时为空
     */
    private Long plannedMinutes;

    /**
     * 停机时间（分钟）
     */
    private Long downtimeMinutes;

    /**
     * 运行时间（分钟）= 计划生产时间 − 停机时间，不小于 0；未配置计划生产时间时为空
     */
    private Long runMinutes;

    /**
     * 产出数（合格 + 不良）
     */
    private BigDecimal outputQty;

    private BigDecimal qualifiedQty;

    /**
     * 理论加工时间（分钟）= Σ 标准节拍 × 产出数；缺少标准节拍时为空
     */
    private BigDecimal idealMinutes;

    private BigDecimal availability;

    private String availabilityStatus;

    private BigDecimal performance;

    private String performanceStatus;

    private BigDecimal quality;

    private String qualityStatus;

    private BigDecimal oee;

    private String oeeStatus;

}
