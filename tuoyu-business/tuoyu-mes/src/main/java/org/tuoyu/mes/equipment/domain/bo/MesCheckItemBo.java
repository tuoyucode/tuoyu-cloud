package org.tuoyu.mes.equipment.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.equipment.domain.MesCheckItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 点检项目业务对象 mes_check_item（新增、修改与查询条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesCheckItem.class, reverseConvertGenerate = false)
public class MesCheckItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "点检项目 ID 不能为空", groups = OnUpdate.class)
    private Long checkItemId;

    @NotBlank(message = "项目名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "项目名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String itemName;

    /**
     * 适用设备：与适用设备类型二选一，都填时以设备为准
     */
    private Long deviceId;

    @Size(max = 32, message = "设备类型不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String deviceType;

    @Size(max = 255, message = "点检方法不能超过 255 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String checkMethod;

    @Size(max = 255, message = "点检标准不能超过 255 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String checkStandard;

    /**
     * 结果类型（judge 判定、numeric 数值）
     */
    @NotBlank(message = "结果类型不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String resultType;

    @Digits(integer = 12, fraction = 6, message = "下限最多 12 位整数、6 位小数", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal lowerLimit;

    @Digits(integer = 12, fraction = 6, message = "上限最多 12 位整数、6 位小数", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal upperLimit;

    @Size(max = 32, message = "单位不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String unit;

    /**
     * 是否必检（Y 是 N 否），为空时为必检
     */
    @Pattern(regexp = "[YN]", message = "是否必检只能是 Y 或 N", groups = {OnCreate.class, OnUpdate.class})
    private String mandatory;

    @Min(value = 0, message = "排序号不能小于 0", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 9999, message = "排序号不能大于 9999", groups = {OnCreate.class, OnUpdate.class})
    private Integer sortNo;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
