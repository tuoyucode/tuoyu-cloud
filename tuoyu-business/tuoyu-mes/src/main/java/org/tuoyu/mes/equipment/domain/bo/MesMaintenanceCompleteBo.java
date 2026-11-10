package org.tuoyu.mes.equipment.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 完成保养：实际内容、用时与更换的备件
 *
 * @author tuoyu
 */
@Data
public class MesMaintenanceCompleteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请填写实际保养内容")
    @Size(max = 1000, message = "实际保养内容不能超过 1000 个字符")
    private String actualContent;

    @NotNull(message = "请填写用时")
    @Min(value = 1, message = "用时至少 1 分钟")
    @Max(value = 100000, message = "用时不能超过 100000 分钟")
    private Integer workMinutes;

    @Size(max = 500, message = "更换的备件不能超过 500 个字符")
    private String spareParts;

    @Size(max = 500, message = "备注不能超过 500 个字符")
    private String remark;

}
