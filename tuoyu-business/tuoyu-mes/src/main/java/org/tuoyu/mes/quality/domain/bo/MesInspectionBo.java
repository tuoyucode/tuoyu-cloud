package org.tuoyu.mes.quality.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.mes.quality.domain.MesInspection;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 检验单业务对象 mes_inspection（新增与查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesInspection.class, reverseConvertGenerate = false)
public class MesInspectionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 检验单号（查询条件，新增时自动生成）
     */
    private String inspectionCode;

    /**
     * 检验类型（first 首检、patrol 巡检、final 完工检）
     */
    @NotBlank(message = "检验类型不能为空", groups = OnCreate.class)
    private String inspectionType;

    @NotNull(message = "工单不能为空", groups = OnCreate.class)
    private Long workorderId;

    /**
     * 工单编号（查询条件，模糊）
     */
    private String workorderCode;

    /**
     * 工序任务：首检、巡检必填，完工检可空
     */
    private Long taskId;

    /**
     * 检验方案：为空时按检验类型、产品与工序自动匹配
     */
    private Long schemeId;

    /**
     * 计划检验数量：为空时取方案的抽样数量
     */
    @DecimalMin(value = "0", inclusive = false, message = "检验数量必须大于 0", groups = OnCreate.class)
    @Digits(integer = 12, fraction = 6, message = "检验数量最多 12 位整数、6 位小数", groups = OnCreate.class)
    private BigDecimal inspectQty;

    /**
     * 检验状态（查询条件）
     */
    private String inspectionStatus;

    /**
     * 检验结论（查询条件）
     */
    private String inspectionResult;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = OnCreate.class)
    private String remark;

}
