package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesRoute;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 工艺路线业务对象 mes_route（同时用作查询条件）：表头与工序一起保存
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesRoute.class, reverseConvertGenerate = false)
public class MesRouteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工艺路线 ID 不能为空", groups = OnUpdate.class)
    private Long routeId;

    /**
     * 产品（成品或半成品）
     */
    @NotNull(message = "产品不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long materialId;

    @NotBlank(message = "工艺路线编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "工艺路线编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String routeCode;

    @NotBlank(message = "工艺路线名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "工艺路线名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String routeName;

    @NotBlank(message = "版本不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 32, message = "版本不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String routeVersion;

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
     * 工序（整体覆盖，至少一道）
     */
    @NotEmpty(message = "请至少添加一道工序", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 200, message = "一条工艺路线最多 200 道工序", groups = {OnCreate.class, OnUpdate.class})
    private List<@Valid MesRouteOperationBo> operations;

}
