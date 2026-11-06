package org.tuoyu.system.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.tenant.core.DynamicTenantStore;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.controller.system.SysProfileController;
import org.tuoyu.system.controller.system.SysTenantController;
import org.tuoyu.system.domain.bo.SysTenantBo;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.bo.SysUserProfileBo;
import org.tuoyu.system.domain.vo.SysTenantVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.service.ISysTenantService;
import org.tuoyu.system.service.ISysUserService;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("租户管理与个人中心接口：权限分支")
class TenantAndProfileControllerTest {

    static {
        SystemTestSupport.init();
    }

    private static final Long ADMIN = SystemConstants.SUPER_ADMIN_USER_ID;

    private final ISysUserService userService = mock(ISysUserService.class);
    private MockedStatic<LoginHelper> login;

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginHelper.class, CALLS_REAL_METHODS);
        login.when(LoginHelper::getUserId).thenReturn(100L);
    }

    @AfterEach
    void tearDown() {
        login.close();
        SystemTestSupport.tenant(false);
    }

    private static boolean ok(R<?> r) {
        return R.isSuccess(r);
    }

    @Nested
    @DisplayName("租户管理")
    class Tenants {

        private final ISysTenantService tenantService = mock(ISysTenantService.class);
        private SysTenantController controller;

        @BeforeEach
        void enable() {
            controller = new SysTenantController(tenantService, SystemTestSupport.tenant(true));
        }

        private void loginInTenant(String tenantId) {
            LoginUser user = new LoginUser();
            user.setUserId(100L);
            user.setTenantId(tenantId);
            login.when(LoginHelper::getLoginUser).thenReturn(user);
        }

        @Test
        @DisplayName("只有平台（默认租户）用户可以管理租户；未开启多租户时拒绝")
        void platformOnly() {
            loginInTenant("acme");
            assertThrows(ServiceException.class, () -> controller.list(new SysTenantBo(), new PageQuery()));
            assertThrows(ServiceException.class, () -> controller.add(new SysTenantBo()));
            verify(tenantService, never()).insertTenant(any());

            loginInTenant("default");
            assertTrue(ok(controller.list(new SysTenantBo(), new PageQuery())));
            assertTrue(ok(controller.getInfo("acme")));
            SysTenantBo bo = new SysTenantBo();
            bo.setTenantId("acme");
            bo.setStatus("1");
            assertTrue(ok(controller.add(bo)));
            assertTrue(ok(controller.edit(bo)));
            assertTrue(ok(controller.changeStatus(bo)));
            assertTrue(ok(controller.remove(new String[]{"acme"})));
            verify(tenantService).updateStatus("acme", "1");
            verify(tenantService).deleteByIds(List.of("acme"));

            login.when(LoginHelper::getLoginUser).thenReturn(null);
            assertThrows(ServiceException.class, () -> controller.getInfo("acme"));

            SystemTestSupport.tenant(false);
            loginInTenant("default");
            assertThrows(ServiceException.class, () -> controller.getInfo("acme"));
        }

        @Test
        @DisplayName("切换租户：只有超级管理员可以切换，目标租户必须存在")
        void switchTenant() {
            try (MockedStatic<DynamicTenantStore> store = mockStatic(DynamicTenantStore.class)) {
                assertThrows(ServiceException.class, () -> controller.switchTenant("acme"));
                assertThrows(ServiceException.class, () -> controller.clearSwitch());

                login.when(LoginHelper::getUserId).thenReturn(ADMIN);
                assertFalse(ok(controller.switchTenant("none")));
                SysTenantVo tenant = new SysTenantVo();
                tenant.setTenantId("acme");
                when(tenantService.selectById("acme")).thenReturn(tenant);
                assertTrue(ok(controller.switchTenant("acme")));
                store.verify(() -> DynamicTenantStore.set(ADMIN, "acme"));
                assertTrue(ok(controller.clearSwitch()));
                store.verify(() -> DynamicTenantStore.clear(ADMIN));

                store.when(() -> DynamicTenantStore.get(ADMIN)).thenReturn("acme");
                SysTenantController.CurrentTenant current =
                    TenantHelper.dynamic("acme", () -> controller.currentTenant().getData());
                assertTrue(current.enabled());
                assertTrue(current.switched());
                assertEquals("acme", current.tenantId());

                SystemTestSupport.tenant(false);
                assertFalse(controller.currentTenant().getData().enabled());
                assertThrows(ServiceException.class, () -> controller.switchTenant("acme"));
            }
        }
    }

    @Nested
    @DisplayName("个人中心")
    class Profile {

        private final PasswordHasher hasher = new PasswordHasher(PasswordHasher.MIN_COST);
        private final SysProfileController controller = new SysProfileController(userService, hasher);

        private void me(String rawPassword) {
            SysUserVo vo = new SysUserVo();
            vo.setUserId(100L);
            vo.setUserName("alice");
            vo.setPassword(hasher.hash(rawPassword));
            when(userService.selectUserById(100L)).thenReturn(vo);
        }

        @Test
        @DisplayName("修改密码：旧密码错误、新旧相同时拒绝；通过后保存新密码的哈希")
        void updatePwd() {
            me("Old#2026");
            assertThat(controller.updatePwd(new SysProfileController.SysUserPasswordBo("wrong", "New#2026")).getMsg())
                .contains("旧密码错误");
            assertThat(controller.updatePwd(new SysProfileController.SysUserPasswordBo("Old#2026", "Old#2026")).getMsg())
                .contains("不能与旧密码相同");
            verify(userService, never()).resetUserPwd(any(), any());

            when(userService.resetUserPwd(Mockito.eq(100L), anyString())).thenReturn(1);
            assertTrue(ok(controller.updatePwd(new SysProfileController.SysUserPasswordBo("Old#2026", "New#2026"))));
            ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
            verify(userService).resetUserPwd(Mockito.eq(100L), saved.capture());
            assertTrue(hasher.matches("New#2026", saved.getValue()));

            when(userService.resetUserPwd(Mockito.eq(100L), anyString())).thenReturn(0);
            assertFalse(ok(controller.updatePwd(new SysProfileController.SysUserPasswordBo("Old#2026", "Other#2026"))));
        }

        @Test
        @DisplayName("修改资料：只能改自己；手机号、邮箱重复时拒绝")
        void updateProfile() {
            login.when(LoginHelper::getUsername).thenReturn("alice");
            SysUserProfileBo profile = new SysUserProfileBo();
            profile.setNickName("新昵称");
            profile.setPhoneNumber("13800000000");
            profile.setEmail("a@example.com");

            when(userService.checkPhoneUnique(any())).thenReturn(false);
            assertThat(controller.updateProfile(profile).getMsg()).contains("手机号码已存在");
            when(userService.checkPhoneUnique(any())).thenReturn(true);
            when(userService.checkEmailUnique(any())).thenReturn(false);
            assertThat(controller.updateProfile(profile).getMsg()).contains("邮箱账号已存在");
            when(userService.checkEmailUnique(any())).thenReturn(true);
            when(userService.updateUserProfile(any())).thenReturn(0);
            assertFalse(ok(controller.updateProfile(profile)));
            when(userService.updateUserProfile(any())).thenReturn(1);
            assertTrue(ok(controller.updateProfile(profile)));

            ArgumentCaptor<SysUserBo> updated = ArgumentCaptor.forClass(SysUserBo.class);
            verify(userService, Mockito.times(2)).updateUserProfile(updated.capture());
            assertEquals(100L, updated.getValue().getUserId());
        }

        @Test
        @DisplayName("修改资料：手机号传空字符串表示清空，不做唯一性检查，原样交给服务层")
        void clearPhone() {
            login.when(LoginHelper::getUsername).thenReturn("alice");
            SysUserProfileBo profile = new SysUserProfileBo();
            profile.setPhoneNumber("");
            when(userService.updateUserProfile(any())).thenReturn(1);

            assertTrue(ok(controller.updateProfile(profile)));
            verify(userService, never()).checkPhoneUnique(any());
            ArgumentCaptor<SysUserBo> updated = ArgumentCaptor.forClass(SysUserBo.class);
            verify(userService).updateUserProfile(updated.capture());
            assertEquals("", updated.getValue().getPhoneNumber());
        }

        @Test
        @DisplayName("个人信息带出角色组与岗位组")
        void profile() {
            me("Old#2026");
            when(userService.selectUserRoleGroup(100L)).thenReturn("普通角色");
            when(userService.selectUserPostGroup(100L)).thenReturn("经理");
            SysProfileController.ProfileVo vo = controller.profile().getData();
            assertEquals("普通角色", vo.roleGroup());
            assertEquals("经理", vo.postGroup());
            assertEquals("alice", vo.user().getUserName());
        }
    }
}
