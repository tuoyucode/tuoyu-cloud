package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 角色 sys_role
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    @TableId("role_id")
    private Long roleId;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色标识（逗号分隔的多项）
     */
    private String roleKey;

    /**
     * 显示顺序
     */
    private Integer roleSort;

    /**
     * 数据范围（1 ~ 6，见 DataScopeType）
     */
    private String dataScope;

    /**
     * 菜单树选择项是否父子联动
     */
    private Boolean menuCheckStrictly;

    /**
     * 部门树选择项是否父子联动
     */
    private Boolean deptCheckStrictly;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
