package org.tuoyu.mes.andon.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.andon.domain.MesAndonRule;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 安灯升级规则视图对象 mes_andon_rule
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesAndonRule.class)
public class MesAndonRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long andonRuleId;

    private String ruleName;

    private String eventType;

    private String eventLevel;

    private Integer responseMinutes;

    private Integer resolveMinutes;

    private String escalateLevel;

    /**
     * 超时提醒的用户 ID，逗号分隔
     */
    private String notifyUsers;

    /**
     * 超时提醒的用户昵称，逗号分隔
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "notifyUsers")
    private String notifyUserNames;

    private String status;

    private String remark;

    private LocalDateTime createTime;

}
