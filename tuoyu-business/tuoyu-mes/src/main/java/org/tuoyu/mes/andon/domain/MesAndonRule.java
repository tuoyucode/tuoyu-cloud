package org.tuoyu.mes.andon.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 安灯升级规则 mes_andon_rule：按异常类型、紧急程度配置响应与解决时限，超时后升级到哪个级别、提醒哪些人
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_andon_rule")
public class MesAndonRule extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "andon_rule_id")
    private Long andonRuleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 适用的异常类型（AndonType），为空表示全部类型
     */
    private String eventType;

    /**
     * 适用的紧急程度（AndonLevel），为空表示全部级别
     */
    private String eventLevel;

    /**
     * 响应时限（分钟，从上报到开始处理），为空表示不检查
     */
    private Integer responseMinutes;

    /**
     * 解决时限（分钟，从上报到解决），为空表示不检查
     */
    private Integer resolveMinutes;

    /**
     * 超时后升级到的紧急程度（AndonLevel），为空表示只提醒
     */
    private String escalateLevel;

    /**
     * 超时提醒的用户（用户 ID，逗号分隔）
     */
    private String notifyUsers;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 删除标志（0 存在 1 删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
