package org.tuoyu.mes.equipment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.equipment.domain.MesOeeConfig;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * OEE 配置视图对象 mes_oee_config；没有配置时各项为空
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesOeeConfig.class)
public class MesOeeConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String targetType;

    private Long targetId;

    /**
     * 每日计划生产时间（分钟）
     */
    private Integer plannedMinutes;

    /**
     * 标准节拍（分钟/件）
     */
    private BigDecimal cycleMinutes;

    private String remark;

}
