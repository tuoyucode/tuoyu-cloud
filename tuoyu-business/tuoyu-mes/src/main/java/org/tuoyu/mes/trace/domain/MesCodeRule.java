package org.tuoyu.mes.trace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 编码规则 mes_code_rule
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_code_rule")
public class MesCodeRule extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "rule_id")
    private Long ruleId;

    /**
     * 规则编码（业务标识，例如 WORKORDER）
     */
    private String ruleCode;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 前缀
     */
    private String prefix;

    /**
     * 日期格式（yyyyMMdd、yyMMdd、yyyyMM，为空表示不带日期）
     */
    private String datePattern;

    /**
     * 流水号位数
     */
    private Integer serialLength;

    /**
     * 流水号重置周期（none 不重置、day 每天、month 每月、year 每年）
     */
    private String resetCycle;

    /**
     * 状态（0正常 1停用）
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
