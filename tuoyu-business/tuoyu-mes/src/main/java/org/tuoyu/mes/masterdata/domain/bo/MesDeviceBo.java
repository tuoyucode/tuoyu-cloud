package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesDevice;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备业务对象 mes_device（同时用作查询条件）。
 * <p>
 * 所属位置只需填到某一级（工厂、车间、产线或工位），服务端校验它们属于同一条链并补齐上级。
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesDevice.class, reverseConvertGenerate = false)
public class MesDeviceBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "设备 ID 不能为空", groups = OnUpdate.class)
    private Long deviceId;

    @NotBlank(message = "设备编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "设备编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "设备名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String deviceName;

    @Size(max = 32, message = "设备类型不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String deviceType;

    @Size(max = 128, message = "型号不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String model;

    @Size(max = 128, message = "制造商不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String manufacturer;

    private Long factoryId;

    private Long workshopId;

    private Long lineId;

    private Long stationId;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
