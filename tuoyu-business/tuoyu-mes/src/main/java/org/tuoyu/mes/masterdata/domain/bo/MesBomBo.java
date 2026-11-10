package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesBom;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 物料清单业务对象 mes_bom（同时用作查询条件）：表头与明细一起保存
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesBom.class, reverseConvertGenerate = false)
public class MesBomBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "物料清单 ID 不能为空", groups = OnUpdate.class)
    private Long bomId;

    /**
     * 产品（成品或半成品）
     */
    @NotNull(message = "产品不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long materialId;

    @NotBlank(message = "BOM 编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "BOM 编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String bomCode;

    @NotBlank(message = "版本不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 32, message = "版本不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String bomVersion;

    /**
     * 基准数量（明细用量对应的产品数量），不填时为 1
     */
    @DecimalMin(value = "0", inclusive = false, message = "基准数量必须大于 0", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal baseQty;

    /**
     * 是否默认版本（Y 是 N 否）
     */
    @Pattern(regexp = "[YN]", message = "是否默认只能是 Y 或 N", groups = {OnCreate.class, OnUpdate.class})
    private String isDefault;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

    /**
     * 明细（整体覆盖，至少一行）
     */
    @NotEmpty(message = "请至少添加一个子件", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 500, message = "一个物料清单最多 500 个子件", groups = {OnCreate.class, OnUpdate.class})
    private List<@Valid MesBomItemBo> items;

}
