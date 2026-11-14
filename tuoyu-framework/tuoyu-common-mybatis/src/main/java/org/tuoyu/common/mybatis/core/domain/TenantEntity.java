package org.tuoyu.common.mybatis.core.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 按租户隔离的实体基类：表中有 {@code tenant_id} 的实体继承本类即可按租户隔离。
 * <p>
 * 租户在插入时由框架按当前租户填写；按实体更新时 UPDATE 语句中没有 {@code tenant_id}（更新策略 NEVER），
 * 防止把数据改到其他租户。不能靠更新填充把它置空来达到同样的效果：MyBatis-Plus 在填充之前就已按字段是否为空
 * 决定了 SET 子句，置空后反而会写入 null。
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 租户编号
     */
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private String tenantId;

}
