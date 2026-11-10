package org.tuoyu.mes.analysis.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 不良柏拉图
 *
 * @author tuoyu
 */
@Data
public class MesDefectParetoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 统计维度：operation、level、source、handleMethod、product
     */
    private String dimension;

    private LocalDateTime generatedAt;

    private long recordCount;

    private BigDecimal defectQty;

    /**
     * 按不良数量从多到少排列
     */
    private List<MesDefectParetoRowVo> rows = new ArrayList<>();

}
