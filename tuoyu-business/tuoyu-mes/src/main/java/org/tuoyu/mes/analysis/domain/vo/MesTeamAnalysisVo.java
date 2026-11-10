package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 班组绩效
 *
 * @author tuoyu
 */
@Data
public class MesTeamAnalysisVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDateTime generatedAt;

    private long reportCount;

    private BigDecimal reportQty;

    private BigDecimal qualifiedQty;

    private BigDecimal yieldRate;

    private BigDecimal workHours;

    /**
     * 各班组报工人数之和（同一人在多个班组报工时分别计入）
     */
    private long memberCount;

    /**
     * 整体人均合格数：合格数合计 ÷ 各班组报工人数之和
     */
    private BigDecimal perCapitaQty;

    /**
     * 按合格数从多到少排列
     */
    private List<MesTeamRowVo> rows = new ArrayList<>();

}
