package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/**
 * 租户套餐：租户可以使用的菜单的集合（ADR 0006 第 7 节）
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant_package")
public class SysTenantPackage extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "package_id")
    private Long packageId;

    private String packageName;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    @TableLogic
    private String delFlag;

    private String remark;

}
