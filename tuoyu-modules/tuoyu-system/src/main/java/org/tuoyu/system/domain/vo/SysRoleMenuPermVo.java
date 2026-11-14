package org.tuoyu.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色与它的一个权限码（按角色批量查询权限码时的结果行）
 *
 * @author tuoyu
 */
@Data
public class SysRoleMenuPermVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色 ID
     */
    private Long roleId;

    /**
     * 菜单的权限码
     */
    private String perms;

}
