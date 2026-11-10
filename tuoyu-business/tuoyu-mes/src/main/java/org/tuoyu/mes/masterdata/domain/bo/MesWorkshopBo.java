package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;

import java.io.Serial;
import java.io.Serializable;

/**
 * 车间业务对象 mes_workshop（同时用作查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesWorkshop.class, reverseConvertGenerate = false)
public class MesWorkshopBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "车间 ID 不能为空", groups = OnUpdate.class)
    private Long workshopId;

    /**
     * 所属工厂
     */
    @NotNull(message = "所属工厂不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long factoryId;

    @NotBlank(message = "车间编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "车间编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String workshopCode;

    @NotBlank(message = "车间名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "车间名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String workshopName;

    /**
     * 对应的组织（部门）
     */
    private Long deptId;

    @Size(max = 64, message = "负责人不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String manager;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
