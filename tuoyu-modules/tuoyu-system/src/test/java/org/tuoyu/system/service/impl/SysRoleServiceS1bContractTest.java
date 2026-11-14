package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.CurrentRole;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.vo.SysMenuVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.ServiceFactory;
import org.tuoyu.system.support.SystemTestSupport;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S1 补充规则 2、3（角色侧）：全部数据范围只能由拥有全部数据权限的人设置；
 * 授权用户时角色的菜单不能超出操作者的菜单
 */
@DisplayName("S1 补充：角色的数据范围与授权用户")
class SysRoleServiceS1bContractTest {

    static {
        SystemTestSupport.init();
    }

    static final String ALL = "1";
    static final String DEPT = "3";

    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = SystemTestSupport.mapper(SysRoleMenuMapper.class);
    private final SysUserRoleMapper userRoleMapper = SystemTestSupport.mapper(SysUserRoleMapper.class);
    private final SysRoleDeptMapper roleDeptMapper = SystemTestSupport.mapper(SysRoleDeptMapper.class);
    private final TenantMenuScope scope = mock(TenantMenuScope.class);
    private final ISysMenuService menuService = mock(ISysMenuService.class);
    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysRoleServiceImpl service = ServiceFactory.create(SysRoleServiceImpl.class, roleMapper, roleMenuMapper,
        userRoleMapper, roleDeptMapper, scope, menuService, deptMapper, userMapper);
    private final CurrentUserStub login = CurrentUserStub.install();

    @BeforeEach
    void setUp() {
        when(scope.allowedMenuIds()).thenReturn(Optional.empty());
        when(roleMapper.insert(any(SysRole.class))).thenReturn(1);
        when(roleMapper.updateById(any(SysRole.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    /**
     * 操作者（用户 2）当前会话中的角色及其数据范围
     */
    private void operatorWithScopes(String... scopes) {
        login.userId(2L);
        List<CurrentRole> roles = new ArrayList<>();
        long id = 10;
        for (String s : scopes) {
            roles.add(new CurrentRole(id++, "r" + id, s));
        }
        login.current().setRoles(roles);
    }

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

    private static SysRoleBo permission(String dataScope) {
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleId(5L);
        bo.setDataScope(dataScope);
        bo.setMenuIds(new Long[0]);
        bo.setDeptIds(new Long[0]);
        return bo;
    }

    private static SysRoleBo newRole(String dataScope) {
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleName("r");
        bo.setRoleKey("r");
        bo.setRoleSort(1);
        bo.setDataScope(dataScope);
        return bo;
    }

    @Nested
    @DisplayName("规则 2：全部数据范围")
    class AllDataScope {

        private String savedScope() {
            ArgumentCaptor<SysRole> saved = ArgumentCaptor.forClass(SysRole.class);
            verify(roleMapper).insert(saved.capture());
            return saved.getValue().getDataScope();
        }

        private SysRole role(String dataScope) {
            SysRole role = new SysRole();
            role.setRoleId(5L);
            role.setDataScope(dataScope);
            return role;
        }

        @Test
        @DisplayName("没有全部数据权限的人明确提交全部数据范围时拒绝新增")
        void insertRejected() {
            operatorWithScopes(DEPT);
            assertThatThrownBy(() -> service.insertRole(newRole(ALL)))
                .isInstanceOf(ServiceException.class).hasMessage("没有全部数据权限，不能把角色的数据范围设为全部数据");
            verify(roleMapper, never()).insert(any(SysRole.class));
        }

        @Test
        @DisplayName("没有全部数据权限的人不提交数据范围：新角色为“仅本人”")
        void insertBlankDefaultsToSelf() {
            operatorWithScopes(DEPT);
            service.insertRole(newRole(null));
            assertThat(savedScope()).isEqualTo("5");
        }

        @Test
        @DisplayName("没有全部数据权限的人提交空白的数据范围：同样为“仅本人”")
        void insertEmptyDefaultsToSelf() {
            operatorWithScopes(DEPT);
            service.insertRole(newRole(" "));
            assertThat(savedScope()).isEqualTo("5");
        }

        @Test
        @DisplayName("拥有全部数据权限的人不提交数据范围：保持全部数据（写 1 或不写、由表的默认值得到 1）")
        void insertBlankKeepsAllForAllDataOperator() {
            operatorWithScopes(DEPT, ALL);
            service.insertRole(newRole(null));
            assertThat(savedScope()).isIn(null, ALL);
        }

        @Test
        @DisplayName("超级管理员不提交数据范围：保持全部数据")
        void insertBlankKeepsAllForSuperAdmin() {
            login.userId(SystemConstants.SUPER_ADMIN_USER_ID);
            login.current().setRoles(List.of());
            service.insertRole(newRole(null));
            assertThat(savedScope()).isIn(null, ALL);
        }

        @Test
        @DisplayName("没有全部数据权限的人把其他数据范围改为全部数据时拒绝，不改动任何数据")
        void updateRejected() {
            operatorWithScopes(DEPT, "5");
            when(roleMapper.selectById(5L)).thenReturn(role(DEPT));
            assertThatThrownBy(() -> service.updateRolePermission(permission(ALL)))
                .hasMessage("没有全部数据权限，不能把角色的数据范围设为全部数据");
            verify(roleMapper, never()).updateById(any(SysRole.class));
            verify(roleMenuMapper, never()).delete(any());
            verify(roleDeptMapper, never()).delete(any());
        }

        @Test
        @DisplayName("角色原来就是全部数据：没有全部数据权限的人原样提交（例如只改菜单）照常保存")
        void resubmitAllowed() {
            operatorWithScopes(DEPT);
            when(roleMapper.selectById(5L)).thenReturn(role(ALL));
            service.updateRolePermission(permission(ALL));
            verify(roleMapper).updateById(any(SysRole.class));
        }

        @Test
        @DisplayName("拥有全部数据权限的人、超级管理员可以；其他数据范围不受此限制；修改时不提交数据范围表示不修改")
        void allowed() {
            operatorWithScopes(DEPT, ALL);
            service.insertRole(newRole(ALL));
            service.updateRolePermission(permission(ALL));
            login.userId(SystemConstants.SUPER_ADMIN_USER_ID);
            login.current().setRoles(List.of());
            service.insertRole(newRole(null));
            service.updateRolePermission(permission(ALL));
            operatorWithScopes(DEPT);
            service.insertRole(newRole(DEPT));
            service.updateRolePermission(permission("2"));
            service.updateRolePermission(permission(null));
        }
    }

    @Nested
    @DisplayName("规则 3（角色侧）：批量授权用户")
    class AuthUsers {

        private Method menuIdsByRoles;

        @BeforeEach
        void stubUsers() throws Exception {
            when(roleMapper.selectCount(any())).thenReturn(1L);
            when(userMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> {
                List<SysUser> users = new ArrayList<>();
                if (inv.getArgument(0) instanceof AbstractWrapper<?, ?, ?> w) {
                    for (Object v : w.getParamNameValuePairs().values()) {
                        if (v instanceof Long id) {
                            SysUser u = new SysUser();
                            u.setUserId(id);
                            users.add(u);
                        }
                    }
                }
                return users;
            });
        }

        /**
         * 角色的菜单：SysRoleMapper#selectMenuIdsByRoleIds（按角色打桩，用反射取得方法）
         */
        private void roleMenus(Map<Long, Set<Long>> menusByRole) throws Exception {
            menuIdsByRoles = SysRoleMapper.class.getMethod("selectMenuIdsByRoleIds", Collection.class);
            Object stub = doAnswer(inv -> {
                Set<Long> result = new HashSet<>();
                Collection<?> roleIds = inv.getArgument(0);
                if (roleIds != null) {
                    roleIds.forEach(id -> result.addAll(menusByRole.getOrDefault((Long) id, Set.of())));
                }
                return result;
            }).when(roleMapper);
            menuIdsByRoles.invoke(stub, (Object) anyCollection());
        }

        @Test
        @DisplayName("角色的菜单超出操作者的菜单时整体拒绝，不写入")
        void rejectRoleWithMoreMenus() throws Exception {
            operatorWithScopes(DEPT);
            ownMenus(1L, 2L);
            roleMenus(Map.of(5L, Set.of(1L, 3L)));
            assertThatThrownBy(() -> service.insertAuthUsers(5L, new Long[]{100L, 101L}))
                .isInstanceOf(ServiceException.class).hasMessage("只能分配菜单权限不超过自己的角色");
            verify(userRoleMapper, never()).insert(anyCollection());
        }

        @Test
        @DisplayName("角色的菜单都在操作者的菜单内时照常授权；超级管理员不检查")
        void allowRoleWithinMenus() throws Exception {
            operatorWithScopes(DEPT);
            ownMenus(1L, 2L, 3L);
            roleMenus(Map.of(5L, Set.of(1L, 3L)));
            service.insertAuthUsers(5L, new Long[]{100L});
            login.userId(SystemConstants.SUPER_ADMIN_USER_ID);
            ownMenus();
            service.insertAuthUsers(5L, new Long[]{101L});
            verify(userRoleMapper, org.mockito.Mockito.times(2)).insert(anyCollection());
        }
    }
}
