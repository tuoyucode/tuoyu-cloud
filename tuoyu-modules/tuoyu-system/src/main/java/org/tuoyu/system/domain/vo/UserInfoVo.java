package org.tuoyu.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 当前登录用户的信息：用户、权限码与角色标识
 *
 * @author tuoyu
 */
@Data
public class UserInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户
     */
    private SysUserVo user;

    /**
     * 权限码
     */
    private Set<String> permissions;

    /**
     * 角色标识
     */
    private Set<String> roles;

}
