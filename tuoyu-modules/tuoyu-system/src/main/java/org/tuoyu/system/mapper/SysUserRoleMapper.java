package org.tuoyu.system.mapper;

import cn.hutool.core.convert.Convert;
import org.tuoyu.common.mybatis.core.mapper.BaseMapperPlus;
import org.tuoyu.system.domain.SysUserRole;

import java.util.List;

/**
 * 用户与角色关联表 数据层
 *
 * @author Lion Li
 */
public interface SysUserRoleMapper extends BaseMapperPlus<SysUserRole, SysUserRole> {

    /**
     * 根据角色ID查询关联的用户ID列表
     *
     * @param roleId 角色ID
     * @return 关联到指定角色的用户ID列表
     */
    default List<Long> selectUserIdsByRoleId(Long roleId) {
        return this.lambda()
            .select(SysUserRole::getUserId)
            .eq(SysUserRole::getRoleId, roleId)
            .objs(Convert::toLong);
    }

    /**
     * 删除用户的全部角色关联
     *
     * @param userId 用户ID
     * @return 删除的行数
     */
    default long deleteByUserId(Long userId) {
        return this.lambda().eq(SysUserRole::getUserId, userId).deleteCount();
    }

}
