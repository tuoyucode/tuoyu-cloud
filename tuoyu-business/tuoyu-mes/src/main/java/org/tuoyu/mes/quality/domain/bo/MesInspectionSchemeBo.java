package org.tuoyu.mes.quality.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.quality.domain.MesInspectionScheme;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 检验方案业务对象 mes_inspection_scheme（新增、修改与查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesInspectionScheme.class, reverseConvertGenerate = false)
public class MesInspectionSchemeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "检验方案 ID 不能为空", groups = OnUpdate.class)
    private Long schemeId;

    @NotBlank(message = "方案编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "方案编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String schemeCode;

    @NotBlank(message = "方案名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "方案名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String schemeName;

    /**
     * 检验类型（first 首检、patrol 巡检、final 完工检）
     */
    @NotBlank(message = "检验类型不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String inspectionType;

    /**
     * 适用产品，为空表示通用
     */
    private Long materialId;

    /**
     * 适用工序编码，为空表示不限
     */
    @Size(max = 64, message = "工序编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String operationCode;

    @DecimalMin(value = "0", inclusive = false, message = "抽样数量必须大于 0", groups = {OnCreate.class, OnUpdate.class})
    @Digits(integer = 12, fraction = 6, message = "抽样数量最多 12 位整数、6 位小数", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal sampleQty;

    /**
     * 检验项目与标准（每行一项）
     */
    @Size(max = 2000, message = "检验项目不能超过 2000 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String checkItems;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
