package org.tuoyu.mes.material.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.material.domain.MesMaterialRequest;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 领退料单业务对象 mes_material_request（新增、修改与查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesMaterialRequest.class, reverseConvertGenerate = false)
public class MesMaterialRequestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "领退料单 ID 不能为空", groups = OnUpdate.class)
    private Long requestId;

    /**
     * 单号（查询条件，新增时自动生成）
     */
    private String requestCode;

    /**
     * 类型（issue 领料、supplement 补料、return 退料），新增后不能修改
     */
    @NotBlank(message = "单据类型不能为空", groups = OnCreate.class)
    private String requestType;

    @NotNull(message = "工单不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long workorderId;

    /**
     * 工单编号（查询条件，模糊）
     */
    private String workorderCode;

    @NotNull(message = "仓库不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long warehouseId;

    /**
     * 单据状态（查询条件）
     */
    private String requestStatus;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

    /**
     * 明细（新增、修改时整体保存）
     */
    @NotEmpty(message = "至少填写一行物料明细", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 500, message = "明细不能超过 500 行", groups = {OnCreate.class, OnUpdate.class})
    private List<@Valid MesMaterialRequestItemBo> items;

}
