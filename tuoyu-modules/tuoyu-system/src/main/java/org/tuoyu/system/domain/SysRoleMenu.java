package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色与菜单关联 sys_role_menu
 *
 * @author tuoyu
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    @TableId(type = IdType.INPUT)
    private Long roleId;

    /**
     * 菜单 ID
     */
    private Long menuId;

}
