package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.annotation.CacheEvict;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleDept;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.vo.SysMenuVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.DataScopeProbe;
import org.tuoyu.system.support.ServiceFactory;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色服务的行为契约
 */
@DisplayName("角色服务契约")
class SysRoleServiceContractTest {

    static {
        SystemTestSupport.init();
    }

    private static final Long ADMIN = SystemConstants.SUPER_ADMIN_USER_ID;
    private static final Long ADMIN_ROLE = SystemConstants.SUPER_ADMIN_ROLE_ID;

    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = SystemTestSupport.mapper(SysRoleMenuMapper.class);
    private final SysUserRoleMapper userRoleMapper = SystemTestSupport.mapper(SysUserRoleMapper.class);
    private final SysRoleDeptMapper roleDeptMapper = SystemTestSupport.mapper(SysRoleDeptMapper.class);
    private final TenantMenuScope scope = mock(TenantMenuScope.class);
    private final ISysMenuService menuService = mock(ISysMenuService.class);
    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysRoleServiceImpl service = create(roleMapper);
    private final CurrentUserStub login = CurrentUserStub.install();

    @BeforeEach
    void setUp() {
        when(scope.allowedMenuIds()).thenReturn(Optional.empty());
        SystemTestSupport.events().clear();
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysRoleVo roleVo(Long id, String key, String status) {
        SysRoleVo vo = new SysRoleVo();
        vo.setRoleId(id);
        vo.setRoleKey(key);
        vo.setRoleName("角色" + id);
        vo.setStatus(status);
        return vo;
    }

    private static SysRole role(Long id, String key, String name) {
        SysRole role = new SysRole();
        role.setRoleId(id);
        role.setRoleKey(key);
        role.setRoleName(name);
        return role;
    }

    /**
     * 构造器参数（实现可以只用其中一部分依赖）
     */
    private SysRoleServiceImpl create(SysRoleMapper mapper) {
        return ServiceFactory.create(SysRoleServiceImpl.class, mapper, roleMenuMapper, userRoleMapper, roleDeptMapper, scope,
            menuService, deptMapper, userMapper);
    }

    /**
     * 条件中的 Long 参数值（IN 列表等）
     */
    private static List<Long> longParams(Object wrapper) {
        List<Long> values = new ArrayList<>();
        if (wrapper instanceof AbstractWrapper<?, ?, ?> w) {
            w.getParamNameValuePairs().values().forEach(v -> {
                if (v instanceof Long l) {
                    values.add(l);
                }
            });
        }
        return values;
    }

    /**
     * 操作者数据权限内的用户：带数据权限的用户查询只返回问到的、且在其中的用户
     */
    private void usersInScope(Long... ids) {
        List<Long> scoped = List.of(ids);
        when(userMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> longParams(inv.getArgument(0)).stream()
            .filter(scoped::contains).map(id -> {
                SysUser user = new SysUser();
                user.setUserId(id);
                return user;
            }).toList());
    }

    /**
     * 操作者数据权限内的部门
     */
    private void deptsInScope(Long... ids) {
        List<Long> scoped = List.of(ids);
        when(deptMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> longParams(inv.getArgument(0)).stream()
            .filter(scoped::contains).map(id -> {
                SysDept dept = new SysDept();
                dept.setDeptId(id);
                return dept;
            }).toList());
    }

    /**
     * 操作者自己拥有的菜单
     */
    private void ownMenus(Long... ids) {
        List<SysMenuVo> menus = new ArrayList<>();
        for (Long id : ids) {
            SysMenuVo vo = new SysMenuVo();
            vo.setMenuId(id);
            menus.add(vo);
        }
        when(menuService.selectMenuList(nullable(Long.class))).thenReturn(menus);
        when(menuService.selectMenuList(any(), nullable(Long.class))).thenReturn(menus);
    }

    private List<OnlineUserCleanEvent> cleanEvents() {
        return SystemTestSupport.events(OnlineUserCleanEvent.class);
    }

    @Nested
    @DisplayName("查询")
    class Queries {

        @Test
        @DisplayName("角色标识：逗号分隔的多项逐项去掉空格，空项丢弃")
        void rolePermissions() {
            when(roleMapper.selectRolesByUserId(100L)).thenReturn(List.of(
                roleVo(1L, "a, b", "0"), roleVo(2L, " ,c,", "0"), roleVo(3L, "", "0"), roleVo(4L, null, "0")));
            assertThat(service.selectRolePermissionByUserId(100L)).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        @DisplayName("停用的角色不提供角色标识")
        void disabledRoleGivesNoKey() {
            when(roleMapper.selectRolesByUserId(100L)).thenReturn(List.of(roleVo(1L, "ok", "0"), roleVo(2L, "off", "1")));
            assertThat(service.selectRolePermissionByUserId(100L)).containsExactly("ok");
        }

        @Test
        @DisplayName("授权页的角色列表：用户已有的角色打上标记")
        void authRoles() {
            when(roleMapper.selectRolesByUserId(100L)).thenReturn(List.of(roleVo(2L, "b", "0")));
            when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role(1L, "a", "A"), role(2L, "b", "B")));
            List<SysRoleVo> roles = service.selectRolesAuthByUserId(100L);
            assertThat(roles).extracting(SysRoleVo::getRoleId).containsExactly(1L, 2L);
            assertThat(roles).extracting(SysRoleVo::isFlag).containsExactly(false, true);
            assertEquals(List.of(2L), service.selectRoleListByUserId(100L));
        }

        @Test
        @DisplayName("列表、分页、统计、按 ID 查询、按 ID 列表查询都在数据权限下执行（创建部门、创建人）")
        void dataScope() {
            DataScopeProbe<SysRoleMapper> probe = DataScopeProbe.of(SysRoleMapper.class)
                .answer("selectPage", a -> a[0]);
            SysRoleServiceImpl scoped = create(probe.mapper());
            scoped.selectRoleList(new SysRoleBo());
            scoped.selectPageRoleList(new SysRoleBo(), new PageParam());
            scoped.countRoleList(new SysRoleBo());
            scoped.selectRoleById(1L);
            scoped.selectRoleByIds(List.of(1L));
            assertThat(probe.calls()).isNotEmpty().allSatisfy(c -> assertThat(c.columns())
                .containsEntry("deptName", "create_dept").containsEntry("userName", "create_by"));
        }

        @Test
        @DisplayName("名称、标识唯一性")
        void uniqueness() {
            SysRoleBo bo = new SysRoleBo();
            bo.setRoleId(1L);
            bo.setRoleName("n");
            bo.setRoleKey("k");
            when(roleMapper.exists(any())).thenReturn(true);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            assertThat(service.checkRoleNameUnique(bo)).isFalse();
            assertThat(service.checkRoleKeyUnique(bo)).isFalse();
            when(roleMapper.exists(any())).thenReturn(false);
            when(roleMapper.selectCount(any())).thenReturn(0L);
            assertThat(service.checkRoleNameUnique(bo)).isTrue();
            assertThat(service.checkRoleKeyUnique(bo)).isTrue();
        }
    }

    @Nested
    @DisplayName("检查")
    class Checks {

        @Test
        @DisplayName("不能操作超级管理员角色；不能新建或改成内置管理员标识，也不能改掉它")
        void allowed() {
            SysRoleBo admin = new SysRoleBo();
            admin.setRoleId(ADMIN_ROLE);
            assertThatThrownBy(() -> service.checkRoleAllowed(admin)).hasMessage("不允许操作超级管理员角色");

            SysRoleBo create = new SysRoleBo();
            create.setRoleKey(SystemConstants.SUPER_ADMIN_ROLE_KEY);
            assertThatThrownBy(() -> service.checkRoleAllowed(create)).hasMessage("不允许使用系统内置管理员角色标识符!");

            when(roleMapper.selectById(5L)).thenReturn(role(5L, "normal", "N"));
            SysRoleBo rename = new SysRoleBo();
            rename.setRoleId(5L);
            rename.setRoleKey(SystemConstants.SUPER_ADMIN_ROLE_KEY);
            assertThatThrownBy(() -> service.checkRoleAllowed(rename)).hasMessage("不允许使用系统内置管理员角色标识符!");

            when(roleMapper.selectById(6L)).thenReturn(role(6L, SystemConstants.SUPER_ADMIN_ROLE_KEY, "S"));
            SysRoleBo change = new SysRoleBo();
            change.setRoleId(6L);
            change.setRoleKey("other");
            assertThatThrownBy(() -> service.checkRoleAllowed(change)).hasMessage("不允许修改系统内置管理员角色标识符!");

            SysRoleBo same = new SysRoleBo();
            same.setRoleId(5L);
            same.setRoleKey("normal");
            service.checkRoleAllowed(same);
        }

        @Test
        @DisplayName("修改不存在的角色：404")
        void allowedMissingRole() {
            SysRoleBo missing = new SysRoleBo();
            missing.setRoleId(77L);
            missing.setRoleKey("x");
            assertThatThrownBy(() -> service.checkRoleAllowed(missing))
                .isInstanceOf(ServiceException.class).hasMessage("角色不存在")
                .satisfies(e -> assertEquals(404, ((ServiceException) e).getCode()));
        }

        @Test
        @DisplayName("角色数据权限：超级管理员与空参数放行，数量不一致时拒绝")
        void dataScope() {
            service.checkRoleDataScope((Long) null);
            service.checkRoleDataScope(List.of());
            login.userId(ADMIN);
            service.checkRoleDataScope(List.of(1L, 2L));
            verify(roleMapper, never()).selectCount(any());

            login.userId(2L);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            service.checkRoleDataScope(1L);
            assertThatThrownBy(() -> service.checkRoleDataScope(List.of(1L, 2L))).hasMessage("没有权限访问部分角色数据！");
        }
    }

    @Nested
    @DisplayName("增删改")
    class Writes {

        @Test
        @DisplayName("新增：写入角色与菜单；未传菜单时不写；租户内只能分配套餐中的菜单")
        void insert() {
            login.userId(ADMIN);
            when(roleMapper.insert(any(SysRole.class))).thenAnswer(inv -> {
                inv.<SysRole>getArgument(0).setRoleId(9L);
                return 1;
            });
            SysRoleBo bo = new SysRoleBo();
            bo.setRoleName("r");
            bo.setRoleKey("r");
            bo.setMenuIds(new Long[]{1L, 2L});
            service.insertRole(bo);
            assertEquals(9L, bo.getRoleId());
            ArgumentCaptor<Collection<SysRoleMenu>> menus = ArgumentCaptor.captor();
            verify(roleMenuMapper).insert(menus.capture());
            assertThat(menus.getValue()).extracting(SysRoleMenu::getMenuId).containsExactly(1L, 2L);
            assertThat(menus.getValue()).extracting(SysRoleMenu::getRoleId).containsOnly(9L);

            SysRoleBo noMenus = new SysRoleBo();
            service.insertRole(noMenus);
            verify(roleMenuMapper).insert(anyCollection());

            when(scope.allowedMenuIds()).thenReturn(Optional.of(Set.of(1L)));
            SysRoleBo outside = new SysRoleBo();
            outside.setMenuIds(new Long[]{1L, 2L});
            assertThatThrownBy(() -> service.insertRole(outside)).hasMessage("只能分配租户套餐中的菜单");
        }

        @Test
        @DisplayName("修改基础信息：已分配的角色不能停用；不存在时报错")
        void updateBase() {
            when(userRoleMapper.selectCount(any())).thenReturn(2L);
            SysRoleBo disable = new SysRoleBo();
            disable.setRoleId(5L);
            disable.setStatus(SystemConstants.DISABLE);
            assertThatThrownBy(() -> service.updateRoleBaseInfo(disable)).hasMessage("角色已分配，不能禁用!");
            assertThatThrownBy(() -> service.updateRoleStatus(5L, SystemConstants.DISABLE)).hasMessage("角色已分配，不能禁用!");

            when(roleMapper.updateById(any(SysRole.class))).thenReturn(0);
            SysRoleBo missing = new SysRoleBo();
            missing.setRoleId(5L);
            missing.setRoleName("运维");
            assertThatThrownBy(() -> service.updateRoleBaseInfo(missing)).hasMessage("修改角色'运维'失败，角色不存在");

            when(roleMapper.update(any(), any(Wrapper.class))).thenReturn(0);
            assertThatThrownBy(() -> service.updateRoleStatus(5L, SystemConstants.NORMAL)).hasMessage("修改角色状态失败，角色不存在");
        }

        @Test
        @DisplayName("修改权限：菜单或部门为 null 时拒绝；否则先删后建，清除该角色的自定义部门缓存")
        void updatePermission() throws Exception {
            login.userId(ADMIN);
            SysRoleBo noMenus = new SysRoleBo();
            noMenus.setRoleId(5L);
            noMenus.setDeptIds(new Long[0]);
            assertThatThrownBy(() -> service.updateRolePermission(noMenus))
                .hasMessage("修改角色权限时必须提供菜单与部门列表（可以为空列表）");
            SysRoleBo noDepts = new SysRoleBo();
            noDepts.setRoleId(5L);
            noDepts.setMenuIds(new Long[0]);
            assertThatThrownBy(() -> service.updateRolePermission(noDepts))
                .hasMessage("修改角色权限时必须提供菜单与部门列表（可以为空列表）");
            verify(roleMenuMapper, never()).delete(any());

            SysRoleBo bo = new SysRoleBo();
            bo.setRoleId(5L);
            bo.setDataScope("2");
            bo.setMenuIds(new Long[]{1L});
            bo.setDeptIds(new Long[]{10L, 11L});
            service.updateRolePermission(bo);
            verify(roleMenuMapper).delete(any());
            verify(roleDeptMapper).delete(any());
            ArgumentCaptor<Collection<SysRoleDept>> depts = ArgumentCaptor.captor();
            verify(roleDeptMapper).insert(depts.capture());
            assertThat(depts.getValue()).extracting(SysRoleDept::getDeptId).containsExactly(10L, 11L);

            CacheEvict evict = SysRoleServiceImpl.class.getMethod("updateRolePermission", SysRoleBo.class).getAnnotation(CacheEvict.class);
            assertThat(evict.cacheNames()).containsExactly(CacheNames.SYS_ROLE_CUSTOM);
            assertEquals("#bo.roleId", evict.key());
        }

        @Test
        @DisplayName("修改权限：非超级管理员只能分配自己拥有的菜单，有一个不属于自己就整体拒绝")
        void permissionMenusLimitedToOwn() {
            login.userId(2L);
            ownMenus(1L, 2L);
            deptsInScope(10L);
            SysRoleBo mixed = new SysRoleBo();
            mixed.setRoleId(5L);
            mixed.setMenuIds(new Long[]{1L, 3L});
            mixed.setDeptIds(new Long[0]);
            assertThatThrownBy(() -> service.updateRolePermission(mixed)).isInstanceOf(ServiceException.class)
                .hasMessage("只能分配自己拥有的菜单");
            verify(roleMenuMapper, never()).delete(any());
            verify(roleMapper, never()).updateById(any(SysRole.class));

            SysRoleBo own = new SysRoleBo();
            own.setRoleId(5L);
            own.setMenuIds(new Long[]{1L, 2L});
            own.setDeptIds(new Long[]{10L});
            service.updateRolePermission(own);
            verify(roleMenuMapper).insert(anyCollection());
            verify(roleDeptMapper).insert(anyCollection());
        }

        @Test
        @DisplayName("修改权限：非超级管理员只能分配自己数据权限内的部门，有一个超出就整体拒绝")
        void permissionDeptsLimitedToScope() {
            login.userId(2L);
            ownMenus(1L);
            deptsInScope(10L);
            SysRoleBo mixed = new SysRoleBo();
            mixed.setRoleId(5L);
            mixed.setDataScope("2");
            mixed.setMenuIds(new Long[]{1L});
            mixed.setDeptIds(new Long[]{10L, 20L});
            assertThatThrownBy(() -> service.updateRolePermission(mixed)).isInstanceOf(ServiceException.class)
                .hasMessage("只能分配数据权限范围内的部门");
            verify(roleDeptMapper, never()).delete(any());
            verify(roleMenuMapper, never()).delete(any());
        }

        @Test
        @DisplayName("新增角色：非超级管理员只能带自己拥有的菜单，校验在写入角色之前")
        void insertMenusLimitedToOwn() {
            login.userId(2L);
            ownMenus(1L);
            SysRoleBo bo = new SysRoleBo();
            bo.setRoleName("r");
            bo.setRoleKey("r");
            bo.setMenuIds(new Long[]{1L, 3L});
            assertThatThrownBy(() -> service.insertRole(bo)).hasMessage("只能分配自己拥有的菜单");
            verify(roleMapper, never()).insert(any(SysRole.class));
        }

        @Test
        @DisplayName("超级管理员分配菜单与部门不受限制")
        void superAdminPermissionUnrestricted() {
            login.userId(ADMIN);
            SysRoleBo bo = new SysRoleBo();
            bo.setRoleId(5L);
            bo.setMenuIds(new Long[]{99L});
            bo.setDeptIds(new Long[]{999L});
            service.updateRolePermission(bo);
            verify(roleMenuMapper).insert(anyCollection());
            verify(roleDeptMapper).insert(anyCollection());
        }

        @Test
        @DisplayName("批量删除：超级管理员角色、已分配的角色不能删；都通过后删除关联与角色")
        void delete() throws Exception {
            login.userId(ADMIN);
            when(roleMapper.selectByIds(anyList())).thenReturn(List.of(role(ADMIN_ROLE, "superadmin", "超管")));
            assertThatThrownBy(() -> service.deleteRoleByIds(List.of(ADMIN_ROLE))).hasMessage("不允许操作超级管理员角色");

            SysRole used = role(5L, "u", "运维");
            when(roleMapper.selectByIds(anyList())).thenReturn(List.of(used));
            when(roleMapper.selectById(5L)).thenReturn(used);
            when(userRoleMapper.selectCount(any())).thenReturn(1L);
            assertThatThrownBy(() -> service.deleteRoleByIds(List.of(5L))).hasMessage("运维已分配，不能删除!");

            when(userRoleMapper.selectCount(any())).thenReturn(0L);
            when(roleMapper.deleteByIds(anyList())).thenReturn(1);
            service.deleteRoleByIds(List.of(5L));
            verify(roleMenuMapper).delete(any());
            verify(roleDeptMapper).delete(any());

            when(roleMapper.deleteByIds(anyList())).thenReturn(0);
            assertThatThrownBy(() -> service.deleteRoleByIds(List.of(5L))).hasMessage("删除角色失败，角色不存在");

            CacheEvict evict = SysRoleServiceImpl.class.getMethod("deleteRoleByIds", List.class).getAnnotation(CacheEvict.class);
            assertThat(evict.cacheNames()).containsExactly(CacheNames.SYS_ROLE_CUSTOM);
            assertThat(evict.allEntries()).isTrue();
        }
    }

    @Nested
    @DisplayName("授权用户")
    class AuthUsers {

        @Test
        @DisplayName("取消授权：不能改自己的角色；用户没有该角色时报错；成功后清理这些用户的在线会话")
        void cancel() {
            login.userId(2L);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            usersInScope(100L, 101L);
            SysUserRole self = new SysUserRole();
            self.setUserId(2L);
            self.setRoleId(5L);
            assertThatThrownBy(() -> service.deleteAuthUser(self)).hasMessage("不允许修改当前用户角色!");
            assertThatThrownBy(() -> service.deleteAuthUsers(5L, new Long[]{100L, 2L})).hasMessage("不允许修改当前用户角色!");

            SysUserRole other = new SysUserRole();
            other.setUserId(100L);
            other.setRoleId(5L);
            when(userRoleMapper.delete(any())).thenReturn(0);
            assertThatThrownBy(() -> service.deleteAuthUser(other)).hasMessage("取消授权失败，用户没有该角色");

            when(userRoleMapper.delete(any())).thenReturn(1);
            service.deleteAuthUser(other);
            service.deleteAuthUsers(5L, new Long[]{100L, 101L});
            assertThat(cleanEvents()).extracting(e -> List.copyOf(e.userIds()))
                .containsExactly(List.of(100L), List.of(100L, 101L));
        }

        @Test
        @DisplayName("批量授权：不能包含自己；写入后清理这些用户的在线会话")
        void select() {
            login.userId(2L);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            usersInScope(100L, 101L);
            assertThatThrownBy(() -> service.insertAuthUsers(5L, new Long[]{2L})).hasMessage("不允许修改当前用户角色!");
            verify(userRoleMapper, never()).insert(anyCollection());

            service.insertAuthUsers(5L, new Long[]{100L, 101L});
            ArgumentCaptor<Collection<SysUserRole>> rows = ArgumentCaptor.captor();
            verify(userRoleMapper).insert(rows.capture());
            assertThat(rows.getValue()).extracting(SysUserRole::getUserId).containsExactly(100L, 101L);
            assertThat(rows.getValue()).extracting(SysUserRole::getRoleId).containsOnly(5L);
            assertThat(cleanEvents()).singleElement().satisfies(e -> assertThat(e.userIds()).containsExactly(100L, 101L));
        }

        @Test
        @DisplayName("授权与取消授权都检查角色的数据权限")
        void authUsersCheckRoleScope() {
            login.userId(2L);
            when(roleMapper.selectCount(any())).thenReturn(0L);
            SysUserRole other = new SysUserRole();
            other.setUserId(100L);
            other.setRoleId(5L);
            when(userRoleMapper.delete(any())).thenReturn(1);
            assertThatThrownBy(() -> service.deleteAuthUser(other)).hasMessage("没有权限访问部分角色数据！");
            assertThatThrownBy(() -> service.deleteAuthUsers(5L, new Long[]{100L})).hasMessage("没有权限访问部分角色数据！");
            assertThatThrownBy(() -> service.insertAuthUsers(5L, new Long[]{100L})).hasMessage("没有权限访问部分角色数据！");
            verify(userRoleMapper, never()).delete(any());
        }

        @Test
        @DisplayName("授权与取消授权：名单中有一个用户不在操作者的数据权限内就整体拒绝")
        void authUsersCheckUserScope() {
            login.userId(2L);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            when(userRoleMapper.delete(any())).thenReturn(1);
            usersInScope(100L);
            SysUserRole outside = new SysUserRole();
            outside.setUserId(101L);
            outside.setRoleId(5L);
            assertThatThrownBy(() -> service.insertAuthUsers(5L, new Long[]{100L, 101L})).hasMessage("没有权限访问用户数据！");
            assertThatThrownBy(() -> service.deleteAuthUsers(5L, new Long[]{100L, 101L})).hasMessage("没有权限访问用户数据！");
            assertThatThrownBy(() -> service.deleteAuthUser(outside)).hasMessage("没有权限访问用户数据！");
            verify(userRoleMapper, never()).insert(anyCollection());
            verify(userRoleMapper, never()).delete(any());
            assertThat(cleanEvents()).isEmpty();
        }

        @Test
        @DisplayName("超级管理员授权不检查用户的数据权限")
        void superAdminAuthUsersUnrestricted() {
            login.userId(ADMIN);
            service.insertAuthUsers(5L, new Long[]{100L, 101L});
            verify(userRoleMapper).insert(anyCollection());
        }

        @Test
        @DisplayName("批量授权不能授予超级管理员角色")
        void selectRejectsSuperAdminRole() {
            login.userId(2L);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            usersInScope(100L);
            assertThatThrownBy(() -> service.insertAuthUsers(ADMIN_ROLE, new Long[]{100L}))
                .isInstanceOf(ServiceException.class).hasMessage("不允许操作超级管理员角色");
            verify(userRoleMapper, never()).insert(anyCollection());
        }

        @Test
        @DisplayName("批量授权只写入还没有该角色的用户")
        void selectSkipsExisting() {
            login.userId(2L);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            usersInScope(100L, 101L);
            SysUserRole existing = new SysUserRole();
            existing.setUserId(100L);
            existing.setRoleId(5L);
            when(userRoleMapper.selectObjs(any())).thenReturn(List.of(100L));
            when(userRoleMapper.selectList(any())).thenReturn(List.of(existing));
            service.insertAuthUsers(5L, new Long[]{100L, 101L});
            ArgumentCaptor<Collection<SysUserRole>> rows = ArgumentCaptor.captor();
            verify(userRoleMapper).insert(rows.capture());
            assertThat(rows.getValue()).extracting(SysUserRole::getUserId).containsExactly(101L);
        }
    }
}
