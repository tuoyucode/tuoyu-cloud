package org.tuoyu.mes.analysis.domain.bo;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 分析报表的查询条件（各报表共用）
 * <p>
 * 开始日期与结束日期必填（按整天，两端都包含），最长 366 天；工厂、车间、产线、产品可选。
 *
 * @author tuoyu
 */
@Data
public class MesAnalysisQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 开始日期（包含）
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginDate;

    /**
     * 结束日期（包含）
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /**
     * 工厂
     */
    private Long factoryId;

    /**
     * 车间
     */
    private Long workshopId;

    /**
     * 产线
     */
    private Long lineId;

    /**
     * 产品
     */
    private Long materialId;

    /**
     * 统计维度（{@link org.tuoyu.mes.analysis.enums.AnalysisDimension} 的编码），为空时用报表的默认维度
     */
    private String dimension;

    /**
     * 按日期统计时的粒度（{@link org.tuoyu.mes.analysis.enums.Granularity} 的编码），为空时按日
     */
    private String granularity;

}
