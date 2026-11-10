package org.tuoyu.mes.trace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 编码流水号 mes_code_sequence
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_code_sequence")
public class MesCodeSequence extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "sequence_id")
    private Long sequenceId;

    /**
     * 规则编码
     */
    private String ruleCode;

    /**
     * 周期（按重置周期取日期，例如 20261108；不重置时为 all）
     */
    private String periodKey;

    /**
     * 当前流水号
     */
    private Long currentValue;

}
