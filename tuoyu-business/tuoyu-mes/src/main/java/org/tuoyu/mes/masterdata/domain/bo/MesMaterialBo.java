package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesMaterial;

import java.io.Serial;
import java.io.Serializable;

/**
 * 物料业务对象 mes_material（同时用作查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesMaterial.class, reverseConvertGenerate = false)
public class MesMaterialBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "物料 ID 不能为空", groups = OnUpdate.class)
    private Long materialId;

    @NotBlank(message = "物料编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "物料编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String materialCode;

    @NotBlank(message = "物料名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "物料名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String materialName;

    @Size(max = 255, message = "规格型号不能超过 255 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String specification;

    /**
     * 物料类型（MaterialType 的编码）
     */
    @NotBlank(message = "物料类型不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String materialType;

    @NotBlank(message = "计量单位不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 32, message = "计量单位不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String unit;

    /**
     * 追溯方式（TraceMode 的编码），不填时为不追溯
     */
    private String traceMode;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
