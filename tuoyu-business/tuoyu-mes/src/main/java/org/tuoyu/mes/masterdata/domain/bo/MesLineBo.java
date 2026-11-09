package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesLine;

import java.io.Serial;
import java.io.Serializable;

/**
 * 产线业务对象 mes_line（同时用作查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesLine.class, reverseConvertGenerate = false)
public class MesLineBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "产线 ID 不能为空", groups = OnUpdate.class)
    private Long lineId;

    /**
     * 所属工厂：只用于查询；保存时由所属车间带出
     */
    private Long factoryId;

    @NotNull(message = "所属车间不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long workshopId;

    @NotBlank(message = "产线编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "产线编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String lineCode;

    @NotBlank(message = "产线名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "产线名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String lineName;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
