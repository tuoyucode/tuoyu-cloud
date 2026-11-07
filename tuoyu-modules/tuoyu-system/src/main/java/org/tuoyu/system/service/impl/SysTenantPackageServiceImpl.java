package org.tuoyu.system.service.impl;

import org.tuoyu.common.mybatis.core.page.PageResults;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.domain.SysTenantPackage;
import org.tuoyu.system.domain.SysTenantPackageMenu;
import org.tuoyu.system.domain.bo.SysTenantPackageBo;
import org.tuoyu.system.domain.vo.SysTenantPackageVo;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysTenantPackageMapper;
import org.tuoyu.system.mapper.SysTenantPackageMenuMapper;
import org.tuoyu.system.service.ISysTenantPackageService;
import org.tuoyu.system.service.ISysTenantService;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 租户套餐 服务层实现
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysTenantPackageServiceImpl implements ISysTenantPackageService {

    private final SysTenantPackageMapper packageMapper;
    private final SysTenantPackageMenuMapper packageMenuMapper;
    private final SysTenantMapper tenantMapper;
    private final ISysTenantService tenantService;

    @Override
    public PageResult<SysTenantPackageVo> selectPageList(SysTenantPackageBo bo, PageParam pageParam) {
        return PageResults.of(packageMapper.selectPage(pageParam.toPage(SysTenantPackage.class), QueryBuilder.lambda(SysTenantPackage.class)
            .likeIfText(SysTenantPackage::getPackageName, bo.getPackageName())
            .eqIfText(SysTenantPackage::getStatus, bo.getStatus())
            .orderByAsc(SysTenantPackage::getCreateTime)
            .build()), SysTenantPackageVo.class);
    }

    @Override
    public SysTenantPackageVo selectById(Long packageId) {
        SysTenantPackageVo vo = BeanMapper.map(packageMapper.selectById(packageId), SysTenantPackageVo.class);
        if (vo != null) {
            vo.setMenuIds(List.copyOf(packageMenuMapper.selectMenuIds(packageId)));
        }
        return vo;
    }

    @Override
    public List<SysTenantPackageVo> selectOptions() {
        return BeanMapper.mapList(packageMapper.selectList(QueryBuilder.lambda(SysTenantPackage.class)
            .eq(SysTenantPackage::getStatus, SystemConstants.NORMAL)
            .orderByAsc(SysTenantPackage::getCreateTime)
            .build()), SysTenantPackageVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertPackage(SysTenantPackageBo bo) {
        SysTenantPackage tenantPackage = BeanMapper.map(bo, SysTenantPackage.class);
        tenantPackage.setStatus(StringUtils.blankToDefault(bo.getStatus(), SystemConstants.NORMAL));
        packageMapper.insert(tenantPackage);
        replaceMenus(tenantPackage.getPackageId(), bo.getMenuIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePackage(SysTenantPackageBo bo) {
        if (packageMapper.selectById(bo.getPackageId()) == null) {
            throw new ServiceException("租户套餐不存在");
        }
        packageMapper.updateById(BeanMapper.map(bo, SysTenantPackage.class));
        replaceMenus(bo.getPackageId(), bo.getMenuIds());
        // 使用该套餐的租户：管理员角色的菜单随之变化；其他角色超出套餐的菜单在读取时被过滤
        tenantService.selectTenantIdsByPackage(bo.getPackageId()).forEach(tenantService::syncPackage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> packageIds) {
        boolean inUse = tenantMapper.exists(QueryBuilder.lambda(SysTenant.class).in(SysTenant::getPackageId, packageIds).build());
        if (inUse) {
            throw new ServiceException("仍有租户使用该套餐，不能删除");
        }
        packageIds.forEach(packageMenuMapper::deleteByPackageId);
        packageMapper.deleteByIds(packageIds);
    }

    private void replaceMenus(Long packageId, Long[] menuIds) {
        packageMenuMapper.deleteByPackageId(packageId);
        if (menuIds != null && menuIds.length > 0) {
            packageMenuMapper.insert(Arrays.stream(menuIds).distinct()
                .map(menuId -> new SysTenantPackageMenu(packageId, menuId))
                .toList());
        }
    }

}
