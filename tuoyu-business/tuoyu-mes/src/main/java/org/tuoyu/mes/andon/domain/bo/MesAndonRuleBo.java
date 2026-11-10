package org.tuoyu.mes.andon.domain.bo;

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
import org.tuoyu.mes.andon.domain.MesAndonRule;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 安灯升级规则业务对象 mes_andon_rule：新增、修改的字段与列表的查询条件
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesAndonRule.class, reverseConvertGenerate = false)
public class MesAndonRuleBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 时限的上限：7 天
     */
    public static final int MAX_MINUTES = 10080;

    /**
     * 一条规则最多提醒的人数
     */
    public static final int MAX_NOTIFY_USERS = 20;

    @NotNull(message = "升级规则 ID 不能为空", groups = OnUpdate.class)
    private Long andonRuleId;

    @NotBlank(message = "规则名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "规则名称不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String ruleName;

    /**
     * 适用的异常类型，见 AndonType；为空表示全部类型（查询时为条件）
     */
    private String eventType;

    /**
     * 适用的紧急程度，见 AndonLevel；为空表示全部级别（查询时为条件）
     */
    private String eventLevel;

    /**
     * 响应时限（分钟）：上报后多久内必须开始处理，为空表示不检查
     */
    @Min(value = 1, message = "响应时限至少 1 分钟", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = MAX_MINUTES, message = "响应时限不能超过 " + MAX_MINUTES + " 分钟", groups = {OnCreate.class, OnUpdate.class})
    private Integer responseMinutes;

    /**
     * 解决时限（分钟）：上报后多久内必须解决，为空表示不检查
     */
    @Min(value = 1, message = "解决时限至少 1 分钟", groups = {OnCreate.class, OnUpdate.class})
    @Max(value = MAX_MINUTES, message = "解决时限不能超过 " + MAX_MINUTES + " 分钟", groups = {OnCreate.class, OnUpdate.class})
    private Integer resolveMinutes;

    /**
     * 超时后升级到的紧急程度，见 AndonLevel；为空表示只提醒
     */
    private String escalateLevel;

    /**
     * 超时提醒的用户；空数组表示只提醒当前处理人。修改时不传表示不修改
     */
    @Size(max = MAX_NOTIFY_USERS, message = "提醒人员不能超过 " + MAX_NOTIFY_USERS + " 人", groups = {OnCreate.class, OnUpdate.class})
    private List<Long> notifyUserIds;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
