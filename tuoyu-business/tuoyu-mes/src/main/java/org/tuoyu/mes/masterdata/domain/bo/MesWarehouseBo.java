package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;

import java.io.Serial;
import java.io.Serializable;

/**
 * 仓库业务对象 mes_warehouse（同时用作查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesWarehouse.class, reverseConvertGenerate = false)
public class MesWarehouseBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "仓库 ID 不能为空", groups = OnUpdate.class)
    private Long warehouseId;

    @NotNull(message = "所属工厂不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long factoryId;

    @NotBlank(message = "仓库编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "仓库编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String warehouseCode;

    @NotBlank(message = "仓库名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "仓库名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String warehouseName;

    /**
     * 仓库类型（WarehouseType 的编码）
     */
    @NotBlank(message = "仓库类型不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String warehouseType;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
