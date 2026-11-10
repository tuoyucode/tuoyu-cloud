package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 良率与直通率
 *
 * @author tuoyu
 */
@Data
public class MesYieldAnalysisVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计维度：date、product、line
     */
    private String dimension;

    /**
     * 按日期统计时的粒度：day、week、month
     */
    private String granularity;

    private LocalDateTime generatedAt;

    private BigDecimal reportQty;

    private BigDecimal qualifiedQty;

    private BigDecimal defectQty;

    /**
     * 整体良率（百分数）
     */
    private BigDecimal yieldRate;

    /**
     * 整体直通率（百分数），口径同 {@link MesYieldRowVo#getFirstPassYield()}，按整个统计范围计算
     */
    private BigDecimal firstPassYield;

    private long workorderCount;

    private List<MesYieldRowVo> rows = new ArrayList<>();

}
