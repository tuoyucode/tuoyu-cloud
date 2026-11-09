package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesStation;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工位业务对象 mes_station（同时用作查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesStation.class, reverseConvertGenerate = false)
public class MesStationBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工位 ID 不能为空", groups = OnUpdate.class)
    private Long stationId;

    /**
     * 所属工厂：只用于查询；保存时由所属产线带出
     */
    private Long factoryId;

    /**
     * 所属车间：只用于查询；保存时由所属产线带出
     */
    private Long workshopId;

    @NotNull(message = "所属产线不能为空", groups = {OnCreate.class, OnUpdate.class})
    private Long lineId;

    @NotBlank(message = "工位编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "工位编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String stationCode;

    @NotBlank(message = "工位名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "工位名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String stationName;

    /**
     * 工位类型（与工序的建议工位类型对应）
     */
    @Size(max = 32, message = "工位类型不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String stationType;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
