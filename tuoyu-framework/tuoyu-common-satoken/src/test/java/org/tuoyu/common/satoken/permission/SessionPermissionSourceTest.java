package org.tuoyu.common.satoken.permission;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.service.PermissionService;
import org.tuoyu.common.satoken.support.SaTokenTestEnv;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.model.LoginUser;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Sa-Token 权限来源：当前会话取会话中的快照，其他登录对象经 PermissionService 查询")
class SessionPermissionSourceTest {

    private SaTokenTestEnv.FakeRedis redis;

    private final PermissionService service = mock(PermissionService.class);

    @BeforeEach
    void setUp() {
        redis = SaTokenTestEnv.enter();
    }

    @AfterEach
    void tearDown() {
        redis.close();
    }

    private static ObjectProvider<PermissionService> provider(PermissionService service) {
        return new StaticListableBeanFactory(Map.of("permissionService", service)).getBeanProvider(PermissionService.class);
    }

    private static ObjectProvider<PermissionService> none() {
        return new StaticListableBeanFactory().getBeanProvider(PermissionService.class);
    }

    private static void login(long userId, Set<String> menus, Set<String> roles) {
        LoginUser user = new LoginUser();
        user.setUserType("sys_user");
        user.setUserId(userId);
        user.setMenuPermission(menus);
        user.setRolePermission(roles);
        LoginHelper.login(user, null);
    }

    @Test
    @DisplayName("当前会话的登录对象：直接返回会话中的菜单与角色权限（可修改的副本），不查询权限服务")
    void currentSession() {
        login(7L, Set.of("system:user:list", "system:user:add"), Set.of());
        SessionPermissionSource source = new SessionPermissionSource(provider(service));

        List<String> menus = source.getPermissionList("sys_user-7", "login");
        List<String> roles = source.getRoleList("sys_user-7", "login");

        assertThat(menus).containsExactlyInAnyOrder("system:user:list", "system:user:add");
        assertThat(roles).isEmpty();
        roles.add("temporary");
        menus.clear();
        assertThat(source.getPermissionList("sys_user-7", "login")).hasSize(2);
        assertThat(source.getRoleList("sys_user-7", "login")).isEmpty();
        verify(service, never()).getMenuPermission(anyLong());
        verify(service, never()).getRolePermission(anyLong());
    }

    @Test
    @DisplayName("会话中的权限为 null 时返回空列表")
    void nullPermissionsInSession() {
        login(8L, null, null);
        SessionPermissionSource source = new SessionPermissionSource(provider(service));

        assertThat(source.getPermissionList("sys_user-8", "login")).isEmpty();
        assertThat(source.getRoleList("sys_user-8", "login")).isEmpty();
    }

    @Test
    @DisplayName("其他登录对象（或当前没有登录）：从登录 ID 末尾取用户 ID，委托权限服务")
    void otherLoginId() {
        when(service.getMenuPermission(9L)).thenReturn(Set.of("system:dept:list"));
        when(service.getRolePermission(9L)).thenReturn(Set.of("common"));
        SessionPermissionSource source = new SessionPermissionSource(provider(service));

        assertThat(source.getPermissionList("sys_user-9", "login")).containsExactly("system:dept:list");
        assertThat(source.getRoleList("app_user-9", "login")).containsExactly("common");

        login(7L, Set.of("system:user:list"), Set.of());
        assertThat(source.getPermissionList("sys_user-9", "login")).containsExactly("system:dept:list");
        verify(service, times(2)).getMenuPermission(9L);
    }

    @Test
    @DisplayName("登录 ID 格式不正确时明确失败：没有分隔符、用户 ID 为空或不是数字")
    void malformedLoginId() {
        SessionPermissionSource source = new SessionPermissionSource(provider(service));

        assertThatThrownBy(() -> source.getPermissionList("invalid", "login")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> source.getPermissionList("sys_user-", "login")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> source.getPermissionList("sys_user-abc", "login")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> source.getRoleList("sys_user:9", "login")).isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("没有权限服务时明确失败，而不是返回空权限导致静默拒绝")
    void missingPermissionService() {
        SessionPermissionSource source = new SessionPermissionSource(none());

        assertThatThrownBy(() -> source.getRoleList("sys_user-9", "login"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("PermissionService 实现类不存在");
    }

}
