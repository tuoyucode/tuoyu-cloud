package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 在制品：统计时刻的在制快照
 *
 * @author tuoyu
 */
@Data
public class MesWipAnalysisVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计维度：operation（按工序）或 line（按产线 + 工序）
     */
    private String dimension;

    private LocalDateTime generatedAt;

    /**
     * 在制工单数
     */
    private long workorderCount;

    private BigDecimal wipQty;

    private BigDecimal pendingInputQty;

    private List<MesWipRowVo> rows = new ArrayList<>();

}
