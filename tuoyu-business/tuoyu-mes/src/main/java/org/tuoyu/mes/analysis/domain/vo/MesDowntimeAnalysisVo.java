package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 停机分析
 *
 * @author tuoyu
 */
@Data
public class MesDowntimeAnalysisVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计维度：reason、line、date
     */
    private String dimension;

    /**
     * 按日期统计时的粒度
     */
    private String granularity;

    private LocalDateTime generatedAt;

    private long eventCount;

    private long openCount;

    private long downtimeMinutes;

    private BigDecimal avgDowntimeMinutes;

    private List<MesDowntimeRowVo> rows = new ArrayList<>();

}
