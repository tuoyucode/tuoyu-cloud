package org.tuoyu.mes.trace.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.trace.domain.MesCodeRule;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编码规则业务对象 mes_code_rule
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesCodeRule.class, reverseConvertGenerate = false)
public class MesCodeRuleBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "规则 ID 不能为空", groups = OnUpdate.class)
    private Long ruleId;

    /**
     * 规则编码：大写字母、数字与下划线，例如 WORKORDER
     */
    @NotBlank(message = "规则编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "规则编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    @Pattern(regexp = "[A-Z0-9_]*", message = "规则编码只能包含大写字母、数字与下划线", groups = {OnCreate.class, OnUpdate.class})
    private String ruleCode;

    @NotBlank(message = "规则名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "规则名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String ruleName;

    /**
     * 前缀，可为空
     */
    @Size(max = 32, message = "前缀不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    @Pattern(regexp = "[A-Za-z0-9_\\-]*", message = "前缀只能包含字母、数字、下划线与连字符", groups = {OnCreate.class, OnUpdate.class})
    private String prefix;

    /**
     * 日期格式：为空表示不带日期
     */
    @Size(max = 16, message = "日期格式不能超过 16 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String datePattern;

    /**
     * 流水号位数（1 ~ 12）
     */
    @NotNull(message = "流水号位数不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Min(value = 1, message = "流水号位数应在 1 ~ 12 之间", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = 12, message = "流水号位数应在 1 ~ 12 之间", groups = {OnCreate.class, OnUpdate.class})
    private Integer serialLength;

    /**
     * 重置周期，见 ResetCycle
     */
    @NotBlank(message = "重置周期不能为空", groups = {OnCreate.class, OnUpdate.class})
    private String resetCycle;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
