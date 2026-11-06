package org.tuoyu.system.mapper;

import cn.hutool.core.convert.Convert;
import org.tuoyu.common.mybatis.core.mapper.BaseMapperPlus;
import org.tuoyu.system.domain.SysTenantPackageMenu;

import java.util.HashSet;
import java.util.Set;

/**
 * 租户套餐与菜单关联 数据层
 *
 * @author tuoyu
 */
public interface SysTenantPackageMenuMapper extends BaseMapperPlus<SysTenantPackageMenu, SysTenantPackageMenu> {

    /**
     * @param packageId 套餐
     * @return 套餐中的菜单
     */
    default Set<Long> selectMenuIds(Long packageId) {
        return new HashSet<>(this.lambda()
            .select(SysTenantPackageMenu::getMenuId)
            .eq(SysTenantPackageMenu::getPackageId, packageId)
            .objs(Convert::toLong));
    }

    /**
     * @param packageId 套餐
     * @return 删除的行数
     */
    default long deleteByPackageId(Long packageId) {
        return this.lambda().eq(SysTenantPackageMenu::getPackageId, packageId).deleteCount();
    }

}
