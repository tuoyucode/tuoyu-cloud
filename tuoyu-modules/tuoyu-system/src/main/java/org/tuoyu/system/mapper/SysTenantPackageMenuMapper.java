package org.tuoyu.system.mapper;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysTenantPackageMenu;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 租户套餐与菜单关联 数据层
 *
 * @author tuoyu
 */
public interface SysTenantPackageMenuMapper extends BaseMapper<SysTenantPackageMenu> {

    /**
     * @param packageId 套餐
     * @return 套餐中的菜单
     */
    default Set<Long> selectMenuIds(Long packageId) {
        List<Object> ids = this.selectObjs(QueryBuilder.lambda(SysTenantPackageMenu.class)
            .select(SysTenantPackageMenu::getMenuId)
            .eq(SysTenantPackageMenu::getPackageId, packageId)
            .build());
        return ids.stream().map(Convert::toLong).collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * @param packageId 套餐
     * @return 删除的行数
     */
    default long deleteByPackageId(Long packageId) {
        return this.delete(QueryBuilder.lambda(SysTenantPackageMenu.class).eq(SysTenantPackageMenu::getPackageId, packageId).build());
    }

}
