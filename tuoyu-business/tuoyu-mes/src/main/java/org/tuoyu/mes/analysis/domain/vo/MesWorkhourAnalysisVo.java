package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工时
 *
 * @author tuoyu
 */
@Data
public class MesWorkhourAnalysisVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计维度：user、team、operation
     */
    private String dimension;

    private LocalDateTime generatedAt;

    private long reportCount;

    private BigDecimal workMinutes;

    private BigDecimal workHours;

    private long missingCount;

    private List<MesWorkhourRowVo> rows = new ArrayList<>();

}
