package org.tuoyu.mes.equipment.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 保存 OEE 配置：两项都可以为空（为空表示未配置，对应的指标显示“未配置”）
 *
 * @author tuoyu
 */
@Data
public class MesOeeConfigBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象类型（device 设备、line 产线）
     */
    @NotBlank(message = "请选择按设备还是按产线配置")
    private String targetType;

    @NotNull(message = "请选择设备或产线")
    private Long targetId;

    /**
     * 每日计划生产时间（分钟）
     */
    @Min(value = 1, message = "每日计划生产时间至少 1 分钟")
    @Max(value = 1440, message = "每日计划生产时间不能超过 1440 分钟")
    private Integer plannedMinutes;

    /**
     * 标准节拍（分钟/件），为空时取工序的单件标准工时
     */
    @DecimalMin(value = "0", inclusive = false, message = "标准节拍必须大于 0")
    @Digits(integer = 12, fraction = 4, message = "标准节拍最多 12 位整数、4 位小数")
    private BigDecimal cycleMinutes;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
