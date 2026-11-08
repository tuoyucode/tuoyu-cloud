package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.tuoyu.common.mybatis.core.page.PageResults;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.domain.SysTenantPackage;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysTenantBo;
import org.tuoyu.system.domain.vo.SysTenantVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysTenantPackageMapper;
import org.tuoyu.system.mapper.SysTenantPackageMenuMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysTenantService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 租户 服务层实现。租户表平台共享；创建租户内的数据（部门、角色、账号）时在新租户中执行
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl implements ISysTenantService {

    private final SysTenantMapper tenantMapper;
    private final SysTenantPackageMapper packageMapper;
    private final SysTenantPackageMenuMapper packageMenuMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordHasher passwordHasher;
    private final TenantProperties tenantProperties;

    @Override
    public PageResult<SysTenantVo> selectPageList(SysTenantBo bo, PageParam pageParam) {
        return PageResults.of(tenantMapper.selectPage(pageParam.toPage(SysTenant.class), buildQueryWrapper(bo)), SysTenantVo.class);
    }

    private Wrapper<SysTenant> buildQueryWrapper(SysTenantBo bo) {
        return QueryBuilder.lambda(SysTenant.class)
            .likeIfText(SysTenant::getTenantId, bo.getTenantId())
            .likeIfText(SysTenant::getTenantName, bo.getTenantName())
            .likeIfText(SysTenant::getContactUser, bo.getContactUser())
            .eqIfText(SysTenant::getStatus, bo.getStatus())
            .orderByAsc(SysTenant::getCreateTime)
            .build();
    }

    @Override
    public SysTenantVo selectById(String tenantId) {
        return BeanMapper.map(tenantMapper.selectById(tenantId), SysTenantVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertTenant(SysTenantBo bo) {
        String tenantId = bo.getTenantId();
        if (tenantMapper.countIncludingDeleted(tenantId) > 0) {
            throw new ServiceException("租户编码 " + tenantId + " 已被使用（包括已删除的租户）");
        }
        checkDomainUnique(bo.getDomain(), tenantId);
        Set<Long> menuIds = packageMenus(bo.getPackageId());

        SysTenant tenant = BeanMapper.map(bo, SysTenant.class);
        tenant.setStatus(StringUtils.blankToDefault(bo.getStatus(), SystemConstants.NORMAL));
        tenantMapper.insert(tenant);

        // 租户内的数据在新租户中创建
        TenantHelper.dynamic(tenantId, () -> {
            SysDept dept = new SysDept();
            dept.setParentId(0L);
            dept.setAncestors("0");
            dept.setDeptName(bo.getTenantName());
            dept.setOrderNum(0);
            dept.setStatus(SystemConstants.NORMAL);
            deptMapper.insert(dept);

            SysRole role = new SysRole();
            role.setRoleName("租户管理员");
            role.setRoleKey(TENANT_ADMIN_ROLE_KEY);
            role.setRoleSort(1);
            role.setDataScope("1");
            role.setMenuCheckStrictly(true);
            role.setDeptCheckStrictly(true);
            role.setStatus(SystemConstants.NORMAL);
            roleMapper.insert(role);
            replaceRoleMenus(role.getRoleId(), menuIds);

            SysUser user = new SysUser();
            user.setDeptId(dept.getDeptId());
            user.setUserName(bo.getAdminUserName());
            user.setNickName("租户管理员");
            user.setUserType("sys_user");
            user.setPassword(passwordHasher.hash(bo.getAdminPassword()));
            user.setStatus(SystemConstants.NORMAL);
            userMapper.insert(user);

            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(user.getUserId());
            userRole.setRoleId(role.getRoleId());
            userRoleMapper.insert(userRole);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTenant(SysTenantBo bo) {
        SysTenant existing = tenantMapper.selectById(bo.getTenantId());
        if (existing == null) {
            throw new ServiceException("租户不存在");
        }
        checkDomainUnique(bo.getDomain(), bo.getTenantId());
        boolean platform = tenantProperties.getDefaultTenantId().equals(bo.getTenantId());
        if (platform) {
            // 默认租户（平台）不受套餐、用户数限制，不能停用、不会过期：忽略这几项。
            // 过期时间与用户数上限置空后由 clearRemovedLimits 清除已有的值，否则平台管理员可能被拒绝登录
            bo.setPackageId(null);
            bo.setStatus(null);
            bo.setExpireTime(null);
            bo.setAccountCount(null);
        } else if (bo.getPackageId() == null) {
            throw new ServiceException("租户套餐不能为空");
        } else {
            packageMenus(bo.getPackageId());
        }
        SysTenant tenant = BeanMapper.map(bo, SysTenant.class);
        tenantMapper.updateById(tenant);
        clearRemovedLimits(existing, bo);
        if (!platform && !Objects.equals(existing.getPackageId(), bo.getPackageId())) {
            syncPackage(bo.getTenantId());
        }
    }

    /**
     * 清除过期时间、用户数上限：全局更新策略为 NOT_NULL，updateById 不会写入 null。
     * 修改接口提交的是完整的编辑表单，原来有值、这次为空，表示改为“不过期”“不限”
     */
    void clearRemovedLimits(SysTenant existing, SysTenantBo bo) {
        boolean clearExpire = bo.getExpireTime() == null && existing.getExpireTime() != null;
        boolean clearCount = bo.getAccountCount() == null && existing.getAccountCount() != null;
        if (!clearExpire && !clearCount) {
            return;
        }
        tenantMapper.update(Wrappers.<SysTenant>lambdaUpdate()
            .set(clearExpire, SysTenant::getExpireTime, null)
            .set(clearCount, SysTenant::getAccountCount, null)
            .eq(SysTenant::getTenantId, bo.getTenantId()));
    }

    @Override
    public void updateStatus(String tenantId, String status) {
        if (tenantProperties.getDefaultTenantId().equals(tenantId)) {
            throw new ServiceException("默认租户不能停用");
        }
        SysTenant tenant = new SysTenant();
        tenant.setTenantId(tenantId);
        tenant.setStatus(status);
        tenantMapper.updateById(tenant);
        if (!SystemConstants.NORMAL.equals(status)) {
            // 停用后已登录的会话也不能继续使用
            SpringUtils.context().publishEvent(OnlineUserCleanEvent.byTenant(tenantId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<String> tenantIds) {
        if (tenantIds.contains(tenantProperties.getDefaultTenantId())) {
            throw new ServiceException("默认租户不能删除");
        }
        tenantMapper.deleteByIds(tenantIds);
        // 事务提交后清除这些租户的在线会话
        tenantIds.forEach(tenantId -> SpringUtils.context().publishEvent(OnlineUserCleanEvent.byTenant(tenantId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncPackage(String tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || tenant.getPackageId() == null) {
            return;
        }
        Set<Long> menuIds = packageMenuMapper.selectMenuIds(tenant.getPackageId());
        TenantHelper.dynamic(tenantId, () -> roleMapper.selectList(QueryBuilder.lambda(SysRole.class)
            .eq(SysRole::getRoleKey, TENANT_ADMIN_ROLE_KEY)
            .build())
            .forEach(role -> replaceRoleMenus(role.getRoleId(), menuIds)));
    }

    @Override
    public List<String> selectTenantIdsByPackage(Long packageId) {
        List<Object> ids = tenantMapper.selectObjs(QueryBuilder.lambda(SysTenant.class)
            .select(SysTenant::getTenantId)
            .eq(SysTenant::getPackageId, packageId)
            .build());
        return ids.stream().map(String::valueOf).toList();
    }

    @Override
    public List<SysTenantVo> selectLoginTenants(String domain) {
        List<SysTenantVo> active = activeTenants();
        if (StringUtils.isNotBlank(domain)) {
            List<SysTenantVo> bound = active.stream().filter(t -> domain.equalsIgnoreCase(t.getDomain())).toList();
            if (!bound.isEmpty()) {
                return bound;
            }
        }
        return active;
    }

    @Override
    public List<String> selectActiveTenantIds() {
        return activeTenants().stream().map(SysTenantVo::getTenantId).toList();
    }

    private List<SysTenantVo> activeTenants() {
        LocalDateTime now = LocalDateTime.now();
        return BeanMapper.mapList(tenantMapper.selectList(QueryBuilder.lambda(SysTenant.class)
            .eq(SysTenant::getStatus, SystemConstants.NORMAL)
            .orderByAsc(SysTenant::getCreateTime)
            .build()), SysTenantVo.class).stream()
            .filter(t -> t.getExpireTime() == null || t.getExpireTime().isAfter(now))
            .toList();
    }

    private void checkDomainUnique(String domain, String tenantId) {
        if (StringUtils.isBlank(domain)) {
            return;
        }
        boolean taken = tenantMapper.exists(QueryBuilder.lambda(SysTenant.class)
            .eq(SysTenant::getDomain, domain.trim())
            .ne(SysTenant::getTenantId, tenantId)
            .build());
        if (taken) {
            throw new ServiceException("域名 " + domain + " 已被其他租户绑定");
        }
    }

    /**
     * @return 套餐中的菜单；套餐不存在或已停用时拒绝
     */
    private Set<Long> packageMenus(Long packageId) {
        SysTenantPackage tenantPackage = packageMapper.selectById(packageId);
        if (tenantPackage == null || !SystemConstants.NORMAL.equals(tenantPackage.getStatus())) {
            throw new ServiceException("租户套餐不存在或已停用");
        }
        return packageMenuMapper.selectMenuIds(packageId);
    }

    private void replaceRoleMenus(Long roleId, Set<Long> menuIds) {
        roleMenuMapper.delete(QueryBuilder.lambda(SysRoleMenu.class).eq(SysRoleMenu::getRoleId, roleId).build());
        if (!menuIds.isEmpty()) {
            roleMenuMapper.insert(menuIds.stream().map(menuId -> {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                return roleMenu;
            }).toList());
        }
    }

}
