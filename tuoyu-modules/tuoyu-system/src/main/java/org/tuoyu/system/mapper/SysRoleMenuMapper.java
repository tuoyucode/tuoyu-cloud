package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysRoleMenu;

import java.util.Collection;

/**
 * 角色与菜单关联表 数据层
 *
 * @author Lion Li
 */
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 根据菜单ID串删除关联关系
     *
     * @param menuIds 菜单ID串
     * @return 结果
     */
    default int deleteByMenuIds(Collection<Long> menuIds) {
        return this.delete(QueryBuilder.lambda(SysRoleMenu.class).in(SysRoleMenu::getMenuId, menuIds).build());
    }

}
