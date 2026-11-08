package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysTenantPackageBo;
import org.tuoyu.system.domain.vo.SysTenantPackageVo;

import java.util.Collection;
import java.util.List;

/**
 * 租户套餐 服务层
 *
 * @author tuoyu
 */
public interface ISysTenantPackageService {

    PageResult<SysTenantPackageVo> selectPageList(SysTenantPackageBo bo, PageParam pageParam);

    /**
     * @return 套餐（含菜单）
     */
    SysTenantPackageVo selectById(Long packageId);

    /**
     * @return 启用的套餐（新增、修改租户时选择）
     */
    List<SysTenantPackageVo> selectOptions();

    void insertPackage(SysTenantPackageBo bo);

    /**
     * 修改套餐；菜单变化后同步使用该套餐的租户的管理员角色
     */
    void updatePackage(SysTenantPackageBo bo);

    /**
     * 删除套餐；仍有租户使用时拒绝
     */
    void deleteByIds(Collection<Long> packageIds);

}
