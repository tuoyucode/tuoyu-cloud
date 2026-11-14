package org.tuoyu.system.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.system.api.model.RoleDTO;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限计算的行为契约
 */
@DisplayName("权限计算契约")
class SysPermissionServiceContractTest {

    static {
        SystemTestSupport.init();
    }

    private final ISysRoleService roleService = mock(ISysRoleService.class);
    private final ISysMenuService menuService = mock(ISysMenuService.class);
    private final SysPermissionServiceImpl service = new SysPermissionServiceImpl(roleService, menuService);

    @Test
    @DisplayName("超级管理员：角色标识只有 superadmin，权限码只有 *:*:*，不查库")
    void superAdmin() {
        Long admin = SystemConstants.SUPER_ADMIN_USER_ID;
        assertThat(service.getRolePermission(admin)).containsExactly(SystemConstants.SUPER_ADMIN_ROLE_KEY);
        assertThat(service.getMenuPermission(admin)).containsExactly("*:*:*");
        verify(roleService, never()).selectRolePermissionByUserId(admin);
        verify(menuService, never()).selectMenuPermsByUserId(admin);
    }

    @Test
    @DisplayName("其他用户：角色标识与权限码来自角色服务与菜单服务；结果是可修改的新集合")
    void normalUser() {
        when(roleService.selectRolePermissionByUserId(7L)).thenReturn(Set.of("r1"));
        when(menuService.selectMenuPermsByUserId(7L)).thenReturn(Set.of("a:b:c"));
        Set<String> roles = service.getRolePermission(7L);
        Set<String> perms = service.getMenuPermission(7L);
        assertThat(roles).containsExactly("r1");
        assertThat(perms).containsExactly("a:b:c");
        roles.add("x");
        perms.add("y");
    }

    @Test
    @DisplayName("权限码到角色的映射：每个权限码对应拥有它的角色 ID；没有角色时为空且不查库")
    void dataScopeRoleMap() {
        assertThat(service.getDataScopeRoleMap(List.of())).isEmpty();
        assertThat(service.getDataScopeRoleMap(null)).isEmpty();
        verify(menuService, never()).selectMenuPermsByRoleIds(anyCollection());

        RoleDTO r1 = new RoleDTO();
        r1.setRoleId(1L);
        RoleDTO r2 = new RoleDTO();
        r2.setRoleId(2L);
        when(menuService.selectMenuPermsByRoleIds(anyCollection()))
            .thenReturn(Map.of(1L, Set.of("a:b:c", "d:e:f"), 2L, Set.of("a:b:c")));
        Map<String, List<Long>> map = service.getDataScopeRoleMap(List.of(r1, r2));
        assertThat(map.get("a:b:c")).containsExactlyInAnyOrder(1L, 2L);
        assertThat(map.get("d:e:f")).containsExactly(1L);
        assertThat(map).hasSize(2);
    }
}
