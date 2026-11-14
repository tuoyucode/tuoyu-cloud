package org.tuoyu.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 用户详情：用户与可选、已选的角色和岗位
 *
 * @author tuoyu
 */
@Data
public class SysUserInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户
     */
    private SysUserVo user;

    /**
     * 用户已有的角色 ID
     */
    private List<Long> roleIds;

    /**
     * 可选的角色
     */
    private List<SysRoleVo> roles;

    /**
     * 用户已有的岗位 ID
     */
    private List<Long> postIds;

    /**
     * 可选的岗位
     */
    private List<SysPostVo> posts;

}
