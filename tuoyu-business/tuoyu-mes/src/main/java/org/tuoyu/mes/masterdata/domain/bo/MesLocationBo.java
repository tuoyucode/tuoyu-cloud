package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesLocation;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库位业务对象 mes_location（同时用作查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesLocation.class, reverseConvertGenerate = false)
public class MesLocationBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "库位 ID 不能为空", groups = OnUpdate.class)
    private Long locationId;

    @NotNull(message = "所属仓库不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long warehouseId;

    @NotBlank(message = "库位编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "库位编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String locationCode;

    @NotBlank(message = "库位名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "库位名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String locationName;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
