package org.tuoyu.mes.quality.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.mes.quality.domain.MesDefectRecord;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 不良记录业务对象 mes_defect_record（手工登记与查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesDefectRecord.class, reverseConvertGenerate = false)
public class MesDefectRecordBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 不良单号（查询条件，登记时自动生成）
     */
    private String defectCode;

    /**
     * 来源（查询条件）
     */
    private String sourceType;

    @NotNull(message = "工单不能为空", groups = OnCreate.class)
    private Long workorderId;

    /**
     * 工单编号（查询条件，模糊）
     */
    private String workorderCode;

    /**
     * 工序任务（可选，必须属于该工单）
     */
    private Long taskId;

    @NotNull(message = "不良数量不能为空", groups = OnCreate.class)
    @DecimalMin(value = "0", inclusive = false, message = "不良数量必须大于 0", groups = OnCreate.class)
    @Digits(integer = 12, fraction = 6, message = "不良数量最多 12 位整数、6 位小数", groups = OnCreate.class)
    private BigDecimal defectQty;

    /**
     * 不良等级（minor 轻微、major 严重、critical 致命）
     */
    @NotBlank(message = "不良等级不能为空", groups = OnCreate.class)
    private String defectLevel;

    @NotBlank(message = "不良描述不能为空", groups = OnCreate.class)
    @Size(max = 500, message = "不良描述不能超过 500 个字符", groups = OnCreate.class)
    private String description;

    /**
     * 处置状态（查询条件）
     */
    private String handleStatus;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = OnCreate.class)
    private String remark;

}
