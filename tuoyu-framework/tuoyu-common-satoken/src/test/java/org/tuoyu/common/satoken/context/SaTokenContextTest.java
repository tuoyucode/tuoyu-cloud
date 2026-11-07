package org.tuoyu.common.satoken.context;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.satoken.support.SaTokenTestEnv;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.api.model.RoleDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sa-Token 上下文：当前用户与当前接口的权限要求")
class SaTokenContextTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @SaCheckRole("class-role")
    static class AnnotatedController {

        @SaCheckPermission(value = {"system:user:list", " "}, orRole = "auditor")
        public void list() {
        }

        public void classLevelOnly() {
        }

        @SaCheckRole({"admin", ""})
        public void roleOnly() {
        }

    }

    static class PlainController {

        public void open() {
        }

    }

    private static AccessRequirement requirementOf(Object bean, String method) throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new HandlerMethod(bean, method));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return new SaTokenPermissionContext().currentRequirement();
    }

    @Test
    @DisplayName("读取接口上的权限码与角色（含 orRole）；方法上的注解优先于类上的；空白值忽略")
    void readsAnnotations() throws Exception {
        AccessRequirement list = requirementOf(new AnnotatedController(), "list");
        assertThat(list.permissions()).containsExactly("system:user:list");
        // 方法上没有 @SaCheckRole 时取类上的
        assertThat(list.roleKeys()).containsExactlyInAnyOrder("auditor", "class-role");
        assertThat(list.constrained()).isTrue();

        AccessRequirement classLevel = requirementOf(new AnnotatedController(), "classLevelOnly");
        assertThat(classLevel.permissions()).isEmpty();
        assertThat(classLevel.roleKeys()).containsExactly("class-role");

        AccessRequirement roleOnly = requirementOf(new AnnotatedController(), "roleOnly");
        assertThat(roleOnly.roleKeys()).containsExactly("admin");
    }

    @Test
    @DisplayName("没有注解、没有命中接口或不在请求中时没有权限要求")
    void noRequirement() throws Exception {
        assertThat(requirementOf(new PlainController(), "open").constrained()).isFalse();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET", "/x")));
        assertThat(new SaTokenPermissionContext().currentRequirement()).isEqualTo(AccessRequirement.NONE);

        RequestContextHolder.resetRequestAttributes();
        assertThat(new SaTokenPermissionContext().currentRequirement()).isEqualTo(AccessRequirement.NONE);
    }

    @Test
    @DisplayName("当前用户由登录用户转换：角色、数据权限、超级管理员标记；未登录时为空")
    void currentUser() {
        try (SaTokenTestEnv.FakeRedis ignored = SaTokenTestEnv.enter()) {
            SaTokenUserContext context = new SaTokenUserContext();
            assertThat(context.currentUser()).isEmpty();

            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(SystemConstants.SUPER_ADMIN_USER_ID);
            loginUser.setUserType("sys_user");
            loginUser.setUsername("admin");
            loginUser.setDeptId(100L);
            loginUser.setDeptName("总部");
            loginUser.setDeptCategory("hq");
            loginUser.setNickname("管理员");
            loginUser.setTenantId("acme");
            loginUser.setClientKey("pc");
            loginUser.setDeviceType("pc");
            loginUser.setBrowser("Chrome");
            loginUser.setOs("Windows");
            loginUser.setMenuPermission(Set.of("system:user:list"));
            loginUser.setRolePermission(Set.of("common"));
            RoleDTO role = new RoleDTO();
            role.setRoleId(2L);
            role.setRoleKey("common");
            role.setDataScope("5");
            loginUser.setRoles(List.of(role));
            loginUser.setDataScopeRoleMap(Map.of("5", List.of(2L)));
            LoginHelper.login(loginUser, null);

            CurrentUser user = context.currentUser().orElseThrow();
            assertThat(user.getUserId()).isEqualTo(SystemConstants.SUPER_ADMIN_USER_ID);
            assertThat(user.getUserName()).isEqualTo("admin");
            assertThat(user.getDeptId()).isEqualTo(100L);
            assertThat(user.getDeptName()).isEqualTo("总部");
            assertThat(user.getTenantId()).isEqualTo("acme");
            assertThat(user.getUserType()).isEqualTo("sys_user");
            assertThat(user.getNickname()).isEqualTo("管理员");
            assertThat(user.getDeptCategory()).isEqualTo("hq");
            assertThat(user.getClientKey()).isEqualTo("pc");
            assertThat(user.getDeviceType()).isEqualTo("pc");
            assertThat(user.getBrowser()).isEqualTo("Chrome");
            assertThat(user.getOs()).isEqualTo("Windows");
            assertThat(user.getPermissions()).containsExactly("system:user:list");
            assertThat(user.getRoleKeys()).containsExactly("common");
            assertThat(user.isSuperAdmin()).isTrue();
            assertThat(user.getRoles()).singleElement().satisfies(r -> {
                assertThat(r.getRoleId()).isEqualTo(2L);
                assertThat(r.getRoleKey()).isEqualTo("common");
                assertThat(r.getDataScope()).isEqualTo("5");
            });
            assertThat(user.getDataScopeRoleMap()).containsEntry("5", List.of(2L));
        }
    }

    @Test
    @DisplayName("登录用户没有角色与数据权限时转换为空集合，不是 null")
    void currentUserWithoutRoles() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(9L);
        CurrentUser user = SaTokenUserContext.toCurrentUser(loginUser);
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.getRoleKeys()).isEmpty();
        assertThat(user.getPermissions()).isEmpty();
        assertThat(user.getDataScopeRoleMap()).isEmpty();
        assertThat(user.isSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("不在请求中（没有 Sa-Token 上下文，例如后台线程）时当前用户为空，不抛出异常")
    void currentUserOutsideRequest() {
        assertThat(new SaTokenUserContext().currentUser()).isEmpty();
    }

}
