package org.tuoyu.common.mybatis.core.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 按租户隔离的实体：表中有 {@code tenant_id} 字段的实体按租户隔离，继承本类即可。
 * <p>
 * 不需要手工维护“哪些表要隔离”的清单：开启多租户后，框架按表是否有 {@code tenant_id} 字段决定。
 * 插入时不必设置 tenantId，由框架按当前租户补上；关闭多租户时取数据库默认值（默认租户）。
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属租户。插入时由框架按当前租户填写（实体中的值会被改写）；按实体更新时不会改动：
     * 更新策略为 NEVER，按实体生成的 UPDATE 语句中没有 tenant_id，防止把数据改到其他租户。
     * 不能靠更新填充把它置空：MyBatis-Plus 在填充之前就按字段是否为空决定了 SET 子句，置空只会写入 null
     */
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private String tenantId;

}
