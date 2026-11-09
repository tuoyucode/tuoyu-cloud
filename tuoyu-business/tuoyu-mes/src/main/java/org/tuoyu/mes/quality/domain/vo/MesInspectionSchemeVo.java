package org.tuoyu.mes.quality.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.quality.domain.MesInspectionScheme;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验方案视图对象 mes_inspection_scheme
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesInspectionScheme.class)
public class MesInspectionSchemeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long schemeId;

    private String schemeCode;

    private String schemeName;

    /**
     * 检验类型（first 首检、patrol 巡检、final 完工检）
     */
    private String inspectionType;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private String operationCode;

    private BigDecimal sampleQty;

    private String checkItems;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    private String remark;

    private LocalDateTime createTime;

}
