package org.tuoyu.mes.equipment.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * OEE 的查询条件：按设备或产线、按日期范围（含首尾两天，最长 366 天）
 *
 * @author tuoyu
 */
@Data
public class MesOeeQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象类型（device 设备、line 产线）
     */
    @NotBlank(message = "请选择按设备还是按产线统计")
    private String targetType;

    @NotNull(message = "请选择设备或产线")
    private Long targetId;

    @NotNull(message = "请选择开始日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate beginDate;

    @NotNull(message = "请选择结束日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

}
