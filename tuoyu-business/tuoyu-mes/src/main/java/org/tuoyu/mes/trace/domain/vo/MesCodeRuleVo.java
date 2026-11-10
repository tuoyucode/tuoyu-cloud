package org.tuoyu.mes.trace.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.trace.domain.MesCodeRule;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 编码规则视图对象 mes_code_rule
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesCodeRule.class)
public class MesCodeRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long ruleId;

    private String ruleCode;

    private String ruleName;

    private String prefix;

    private String datePattern;

    private Integer serialLength;

    private String resetCycle;

    private String status;

    private String remark;

    private LocalDateTime createTime;

    /**
     * 是否为系统单据使用的内置规则（删除或停用后这些单据按默认格式生成编号）
     */
    private boolean builtin;

}
