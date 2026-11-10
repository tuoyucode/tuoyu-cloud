package org.tuoyu.mes.andon.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;

import java.io.Serial;
import java.io.Serializable;

/**
 * 停机原因业务对象 mes_downtime_reason
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesDowntimeReason.class, reverseConvertGenerate = false)
public class MesDowntimeReasonBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "停机原因 ID 不能为空", groups = OnUpdate.class)
    private Long reasonId;

    @NotBlank(message = "原因编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "原因编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String reasonCode;

    @NotBlank(message = "原因名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "原因名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String reasonName;

    /**
     * 原因分类，见 DowntimeReasonType
     */
    @NotBlank(message = "原因分类不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String reasonType;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
