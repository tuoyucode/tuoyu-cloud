package org.tuoyu.system.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.LoginFailureKeys;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.domain.SysUserOnline;
import org.tuoyu.system.api.model.LoginUser;
import org.tuoyu.system.controller.monitor.SysUserOnlineController;
import org.tuoyu.system.controller.system.SysDeptController;
import org.tuoyu.system.controller.system.SysMenuController;
import org.tuoyu.system.controller.system.SysRoleController;
import org.tuoyu.system.controller.system.SysUserController;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.bo.SysMenuBo;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.OnlineSessionId;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysUserInfoVo;
import org.tuoyu.system.domain.vo.SysUserOnlineVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.domain.vo.UserInfoVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysMenuService;
import org.tuoyu.system.service.ISysPostService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysUserService;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("管理接口：参数校验与权限分支")
class SystemControllerTest {

    static {
        SystemTestSupport.init();
    }

    private static final Long ADMIN = SystemConstants.SUPER_ADMIN_USER_ID;

    private final ISysUserService userService = mock(ISysUserService.class);
    private final ISysRoleService roleService = mock(ISysRoleService.class);
    private final ISysPostService postService = mock(ISysPostService.class);
    private final ISysDeptService deptService = mock(ISysDeptService.class);
    private final ISysMenuService menuService = mock(ISysMenuService.class);

    private MockedStatic<LoginHelper> login;

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginHelper.class, CALLS_REAL_METHODS);
        login.when(LoginHelper::getUserId).thenReturn(100L);
        SystemTestSupport.events().clear();
    }

    @AfterEach
    void tearDown() {
        login.close();
        Mockito.reset(SystemTestSupport.redisson());
    }

    private static boolean ok(R<?> r) {
        return R.isSuccess(r);
    }

    private static SysRoleVo role(Long id) {
        SysRoleVo role = new SysRoleVo();
        role.setRoleId(id);
        return role;
    }

    @Nested
    @DisplayName("用户管理")
    class Users {

        private final SysUserController controller = new SysUserController(userService, roleService, postService,
            deptService, new PasswordHasher(PasswordHasher.MIN_COST));

        private SysUserBo user() {
            SysUserBo bo = new SysUserBo();
            bo.setUserId(200L);
            bo.setDeptId(10L);
            bo.setUserName("alice");
            bo.setPhoneNumber("13800000000");
            bo.setEmail("a@example.com");
            bo.setPassword("Secret#2026");
            return bo;
        }

        @Test
        @DisplayName("新增：账号、手机号、邮箱重复时依次拒绝；通过后密码哈希保存")
        void add() {
            when(userService.checkUserNameUnique(any())).thenReturn(false);
            assertThat(controller.add(user()).getMsg()).contains("登录账号已存在");
            when(userService.checkUserNameUnique(any())).thenReturn(true);
            when(userService.checkPhoneUnique(any())).thenReturn(false);
            assertThat(controller.add(user()).getMsg()).contains("手机号码已存在");
            when(userService.checkPhoneUnique(any())).thenReturn(true);
            when(userService.checkEmailUnique(any())).thenReturn(false);
            assertThat(controller.add(user()).getMsg()).contains("邮箱账号已存在");
            verify(userService, never()).insertUser(any());

            when(userService.checkEmailUnique(any())).thenReturn(true);
            when(userService.insertUser(any())).thenReturn(1);
            assertTrue(ok(controller.add(user())));
            ArgumentCaptor<SysUserBo> saved = ArgumentCaptor.forClass(SysUserBo.class);
            verify(userService).insertUser(saved.capture());
            assertTrue(new PasswordHasher(PasswordHasher.MIN_COST).matches("Secret#2026", saved.getValue().getPassword()));
            // 新增前检查部门的数据权限
            verify(deptService, Mockito.atLeastOnce()).checkDeptDataScope(10L);
        }

        @Test
        @DisplayName("新增：没有部门数据权限时拒绝")
        void addOutOfScope() {
            doThrow(new ServiceException("没有权限")).when(deptService).checkDeptDataScope(10L);
            assertThrows(ServiceException.class, () -> controller.add(user()));
            verify(userService, never()).insertUser(any());
        }

        @Test
        @DisplayName("修改：先检查是否超级管理员与数据权限，再检查唯一性")
        void edit() {
            when(userService.checkUserNameUnique(any())).thenReturn(false);
            assertThat(controller.edit(user()).getMsg()).contains("登录账号已存在");
            verify(userService).checkUserAllowed(200L);
            verify(userService).checkUserDataScope(200L);
            when(userService.checkUserNameUnique(any())).thenReturn(true);
            when(userService.checkPhoneUnique(any())).thenReturn(false);
            assertThat(controller.edit(user()).getMsg()).contains("手机号码已存在");
            when(userService.checkPhoneUnique(any())).thenReturn(true);
            when(userService.checkEmailUnique(any())).thenReturn(false);
            assertThat(controller.edit(user()).getMsg()).contains("邮箱账号已存在");
            when(userService.checkEmailUnique(any())).thenReturn(true);
            when(userService.updateUser(any())).thenReturn(1);
            assertTrue(ok(controller.edit(user())));

            doThrow(new ServiceException("不允许操作超级管理员用户")).when(userService).checkUserAllowed(ADMIN);
            SysUserBo admin = user();
            admin.setUserId(ADMIN);
            assertThrows(ServiceException.class, () -> controller.edit(admin));
        }

        @Test
        @DisplayName("删除：不能删除自己")
        void remove() {
            assertFalse(ok(controller.remove(new Long[]{200L, 100L})));
            verify(userService, never()).deleteUserByIds(any());
            when(userService.deleteUserByIds(any())).thenReturn(1);
            assertTrue(ok(controller.remove(new Long[]{200L})));
        }

        @Test
        @DisplayName("重置密码与修改状态：检查后执行，密码哈希后保存")
        void resetAndStatus() {
            when(userService.resetUserPwd(any(), anyString())).thenReturn(1);
            assertTrue(ok(controller.resetPwd(user())));
            ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
            verify(userService).resetUserPwd(Mockito.eq(200L), hash.capture());
            assertTrue(hash.getValue().startsWith("$2"));

            SysUserBo status = new SysUserBo();
            status.setUserId(200L);
            status.setStatus("1");
            when(userService.updateUserStatus(200L, "1")).thenReturn(1);
            assertTrue(ok(controller.changeStatus(status)));
            verify(userService, Mockito.times(2)).checkUserAllowed(200L);
        }

        @Test
        @DisplayName("解锁：清除账号、手机号、邮箱三种登录失败计数；用户不存在时失败")
        void unlock() {
            assertFalse(ok(controller.unlock(200L)));

            SysUserVo vo = new SysUserVo();
            vo.setUserName("alice");
            vo.setPhoneNumber("13800000000");
            vo.setEmail("a@example.com");
            when(userService.selectUserById(200L)).thenReturn(vo);
            RBucket<Object> bucket = mock(RBucket.class);
            when(SystemTestSupport.redisson().getBucket(anyString())).thenReturn(bucket);

            assertTrue(ok(controller.unlock(200L)));
            verify(SystemTestSupport.redisson()).getBucket(LoginFailureKeys.account("alice"));
            verify(SystemTestSupport.redisson()).getBucket(LoginFailureKeys.account("13800000000"));
            verify(SystemTestSupport.redisson()).getBucket(LoginFailureKeys.account("a@example.com"));
            verify(bucket, Mockito.times(3)).delete();
        }

        @Test
        @DisplayName("详情：非超级管理员看不到超级管理员角色；带出部门下的岗位")
        void detail() {
            SysUserVo vo = new SysUserVo();
            vo.setUserId(200L);
            vo.setDeptId(10L);
            when(userService.selectUserById(200L)).thenReturn(vo);
            when(roleService.selectRoleList(any())).thenReturn(List.of(role(SystemConstants.SUPER_ADMIN_ROLE_ID), role(2L)));
            when(postService.selectPostListByUserId(200L)).thenReturn(List.of(3L));

            SysUserInfoVo info = controller.getInfo(200L).getData();
            assertThat(info.getRoles()).extracting(SysRoleVo::getRoleId).containsExactly(2L);
            assertEquals(List.of(3L), info.getPostIds());
            verify(userService).checkUserDataScope(200L);

            when(userService.selectUserById(ADMIN)).thenReturn(new SysUserVo());
            SysUserInfoVo adminInfo = controller.getInfo(ADMIN).getData();
            assertEquals(2, adminInfo.getRoles().size());
            SysUserInfoVo empty = controller.getInfo((Long) null).getData();
            assertNull(empty.getUser());

            // 超级管理员不受数据权限限制，查询不存在的用户时返回明确的提示，不抛空指针
            when(userService.selectUserById(999L)).thenReturn(null);
            assertEquals("用户不存在", controller.getInfo(999L).getMsg());
        }

        @Test
        @DisplayName("当前用户信息：看不到自己的数据时失败；带出权限与角色")
        void currentUser() {
            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(100L);
            loginUser.setMenuPermission(Set.of("system:user:list"));
            loginUser.setRolePermission(Set.of("common"));
            login.when(LoginHelper::getLoginUser).thenReturn(loginUser);
            assertFalse(ok(controller.getInfo()));

            SysUserVo vo = new SysUserVo();
            vo.setUserId(100L);
            when(userService.selectUserById(100L)).thenReturn(vo);
            UserInfoVo info = controller.getInfo().getData();
            assertEquals(Set.of("system:user:list"), info.getPermissions());
            assertEquals(Set.of("common"), info.getRoles());
        }

        @Test
        @DisplayName("分配角色：先检查数据权限；授权页面过滤超级管理员角色")
        void authRole() {
            assertTrue(ok(controller.insertAuthRole(200L, new Long[]{2L})));
            verify(userService).checkUserDataScope(200L);
            verify(userService).insertUserAuth(200L, new Long[]{2L});

            when(roleService.selectRolesAuthByUserId(200L)).thenReturn(List.of(role(SystemConstants.SUPER_ADMIN_ROLE_ID), role(2L)));
            assertEquals(1, controller.authRole(200L).getData().getRoles().size());
        }

        @Test
        @DisplayName("列表、下拉、部门树、按部门查询转发给服务")
        void lists() {
            when(userService.selectPageUserList(any(), any())).thenReturn(PageResult.build(List.of(new SysUserVo()), 1));
            assertEquals(1, controller.list(new SysUserBo(), new PageQuery()).getData().getTotal());
            controller.optionselect(new Long[0], 10L);
            verify(userService).selectUserByIds(null, 10L);
            controller.optionselect(new Long[]{1L}, null);
            verify(userService).selectUserByIds(List.of(1L), null);
            controller.deptTree(new SysDeptBo());
            verify(deptService).selectDeptTreeList(any());
            controller.listByDept(10L);
            verify(userService).selectUserListByDept(10L);
        }
    }

    @Nested
    @DisplayName("角色管理")
    class Roles {

        private final SysRoleController controller = new SysRoleController(roleService, userService, deptService);

        private SysRoleBo bo() {
            SysRoleBo bo = new SysRoleBo();
            bo.setRoleId(9L);
            bo.setRoleName("运维");
            bo.setRoleKey("ops");
            return bo;
        }

        @Test
        @DisplayName("新增与修改：名称、权限字符重复时拒绝")
        void addAndEdit() {
            when(roleService.checkRoleNameUnique(any())).thenReturn(false);
            assertThat(controller.add(bo()).getMsg()).contains("角色名称已存在");
            assertThat(controller.edit(bo()).getMsg()).contains("角色名称已存在");
            when(roleService.checkRoleNameUnique(any())).thenReturn(true);
            when(roleService.checkRoleKeyUnique(any())).thenReturn(false);
            assertThat(controller.add(bo()).getMsg()).contains("角色权限已存在");
            assertThat(controller.edit(bo()).getMsg()).contains("角色权限已存在");
            when(roleService.checkRoleKeyUnique(any())).thenReturn(true);
            when(roleService.insertRole(any())).thenReturn(1);
            assertTrue(ok(controller.add(bo())));
        }

        @Test
        @DisplayName("修改基础信息、权限、状态：成功后通知清理该角色的在线会话，失败时提示")
        void changesNotifyOnlineClean() {
            when(roleService.checkRoleNameUnique(any())).thenReturn(true);
            when(roleService.checkRoleKeyUnique(any())).thenReturn(true);
            when(roleService.updateRoleBaseInfo(any())).thenReturn(1);
            when(roleService.updateRolePermission(any())).thenReturn(1);
            when(roleService.updateRoleStatus(any(), any())).thenReturn(1);
            assertTrue(ok(controller.edit(bo())));
            assertTrue(ok(controller.editPermission(bo())));
            assertTrue(ok(controller.changeStatus(bo())));
            List<OnlineUserCleanEvent> events = SystemTestSupport.events(OnlineUserCleanEvent.class);
            assertEquals(3, events.size());
            assertThat(events).allMatch(e -> e.roleId() == 9L);
            verify(roleService, Mockito.times(3)).checkRoleDataScope(9L);

            when(roleService.updateRoleBaseInfo(any())).thenReturn(0);
            when(roleService.updateRolePermission(any())).thenReturn(0);
            when(roleService.updateRoleStatus(any(), any())).thenReturn(0);
            assertFalse(ok(controller.edit(bo())));
            assertFalse(ok(controller.editPermission(bo())));
            assertFalse(ok(controller.changeStatus(bo())));
        }

        @Test
        @DisplayName("修改超级管理员角色时拒绝")
        void rejectAdminRole() {
            doThrow(new ServiceException("不允许操作超级管理员角色")).when(roleService).checkRoleAllowed(any());
            assertThrows(ServiceException.class, () -> controller.editPermission(bo()));
            verify(roleService, never()).updateRolePermission(any());
        }

        @Test
        @DisplayName("授权用户、详情、列表、下拉、部门树转发给服务")
        void delegates() {
            when(roleService.insertAuthUsers(9L, new Long[]{1L})).thenReturn(1);
            assertTrue(ok(controller.selectAuthUserAll(9L, new Long[]{1L})));
            verify(roleService).checkRoleDataScope(9L);
            when(roleService.deleteAuthUsers(9L, new Long[]{1L})).thenReturn(1);
            assertTrue(ok(controller.cancelAuthUserAll(9L, new Long[]{1L})));
            when(roleService.deleteAuthUser(any(SysUserRole.class))).thenReturn(1);
            assertTrue(ok(controller.cancelAuthUser(new SysUserRole())));
            when(roleService.deleteRoleByIds(List.of(9L))).thenReturn(1);
            assertTrue(ok(controller.remove(new Long[]{9L})));

            when(roleService.selectRoleById(9L)).thenReturn(role(9L));
            assertEquals(9L, controller.getInfo(9L).getData().getRoleId());
            controller.list(new SysRoleBo(), new PageQuery());
            controller.optionselect(null);
            verify(roleService).selectRoleByIds(null);
            controller.allocatedList(new SysUserBo(), new PageQuery());
            controller.unallocatedList(new SysUserBo(), new PageQuery());
            when(deptService.selectDeptListByRoleId(9L)).thenReturn(List.of(10L));
            assertEquals(List.of(10L), controller.roleDeptTreeselect(9L).getData().checkedKeys());
        }
    }

    @Nested
    @DisplayName("菜单管理")
    class Menus {

        private final SysMenuController controller = new SysMenuController(menuService, roleService);

        private SysMenuBo bo() {
            SysMenuBo bo = new SysMenuBo();
            bo.setMenuId(11L);
            bo.setParentId(10L);
            bo.setMenuName("用户管理");
            bo.setIsFrame(SystemConstants.NO);
            bo.setPath("user");
            return bo;
        }

        @Test
        @DisplayName("新增与修改：名称重复、外链地址不是 http(s)、上级选自己、路由冲突时拒绝")
        void validate() {
            when(menuService.checkMenuNameUnique(any())).thenReturn(false);
            assertThat(controller.add(bo()).getMsg()).contains("菜单名称已存在");
            assertThat(controller.edit(bo()).getMsg()).contains("菜单名称已存在");

            when(menuService.checkMenuNameUnique(any())).thenReturn(true);
            SysMenuBo frame = bo();
            frame.setIsFrame(SystemConstants.YES);
            assertThat(controller.add(frame).getMsg()).contains("http(s)://");
            assertThat(controller.edit(frame).getMsg()).contains("http(s)://");

            SysMenuBo self = bo();
            self.setParentId(11L);
            assertThat(controller.edit(self).getMsg()).contains("上级菜单不能选择自己");

            when(menuService.checkRouteConfigUnique(any())).thenReturn(false);
            assertThat(controller.add(bo()).getMsg()).contains("路由名称或地址已存在");
            assertThat(controller.edit(bo()).getMsg()).contains("路由名称或地址已存在");

            when(menuService.checkRouteConfigUnique(any())).thenReturn(true);
            when(menuService.insertMenu(any())).thenReturn(1);
            when(menuService.updateMenu(any())).thenReturn(1);
            assertTrue(ok(controller.add(bo())));
            assertTrue(ok(controller.edit(bo())));
        }

        @Test
        @DisplayName("删除：有子菜单或已分配给角色时不允许")
        void remove() {
            when(menuService.hasChildByMenuId(10L)).thenReturn(true);
            assertFalse(ok(controller.remove(10L)));
            when(menuService.hasChildByMenuId(10L)).thenReturn(false);
            when(menuService.checkMenuExistRole(10L)).thenReturn(true);
            assertFalse(ok(controller.remove(10L)));
            when(menuService.checkMenuExistRole(10L)).thenReturn(false);
            when(menuService.deleteMenuById(10L)).thenReturn(1);
            assertTrue(ok(controller.remove(10L)));

            when(menuService.hasChildByMenuId(List.of(10L, 11L))).thenReturn(true);
            assertFalse(ok(controller.remove(new Long[]{10L, 11L})));
            when(menuService.hasChildByMenuId(List.of(10L, 11L))).thenReturn(false);
            assertTrue(ok(controller.remove(new Long[]{10L, 11L})));
            verify(menuService).deleteMenuById(List.of(10L, 11L));
        }

        @Test
        @DisplayName("路由、列表、下拉树、角色菜单树都按当前用户查询")
        void currentUserQueries() {
            controller.getRouters();
            verify(menuService).selectMenuTreeByUserId(100L);
            controller.list(new SysMenuBo());
            controller.treeselect(new SysMenuBo());
            verify(menuService, Mockito.times(2)).selectMenuList(any(SysMenuBo.class), Mockito.eq(100L));
            when(menuService.selectMenuListByRoleId(9L)).thenReturn(List.of(11L));
            assertEquals(List.of(11L), controller.roleMenuTreeselect(9L).getData().checkedKeys());
            // 角色须在当前用户的数据范围内
            verify(roleService).checkRoleDataScope(9L);
            controller.getInfo(11L);
            verify(menuService).selectMenuById(11L);
        }
    }

    @Nested
    @DisplayName("部门管理")
    class Depts {

        private final SysDeptController controller = new SysDeptController(deptService, postService);

        private SysDeptBo bo() {
            SysDeptBo bo = new SysDeptBo();
            bo.setDeptId(11L);
            bo.setParentId(10L);
            bo.setDeptName("研发部");
            return bo;
        }

        @Test
        @DisplayName("修改：名称重复、上级是自己、停用时仍有正常子部门或用户时拒绝")
        void edit() {
            when(deptService.checkDeptNameUnique(any())).thenReturn(false);
            assertThat(controller.add(bo()).getMsg()).contains("部门名称已存在");
            assertThat(controller.edit(bo()).getMsg()).contains("部门名称已存在");

            when(deptService.checkDeptNameUnique(any())).thenReturn(true);
            SysDeptBo self = bo();
            self.setParentId(11L);
            assertThat(controller.edit(self).getMsg()).contains("上级部门不能是自己");

            SysDeptBo disable = bo();
            disable.setStatus(SystemConstants.DISABLE);
            when(deptService.selectNormalChildrenDeptById(11L)).thenReturn(1L);
            assertThat(controller.edit(disable).getMsg()).contains("未停用的子部门");
            when(deptService.selectNormalChildrenDeptById(11L)).thenReturn(0L);
            when(deptService.checkDeptExistUser(11L)).thenReturn(true);
            assertThat(controller.edit(disable).getMsg()).contains("已分配用户");

            when(deptService.updateDept(any())).thenReturn(1);
            when(deptService.insertDept(any())).thenReturn(1);
            assertTrue(ok(controller.edit(bo())));
            assertTrue(ok(controller.add(bo())));
            verify(deptService, Mockito.atLeastOnce()).checkDeptDataScope(11L);
        }

        @Test
        @DisplayName("删除：根部门、有下级、有用户、有岗位时不允许")
        void remove() {
            assertFalse(ok(controller.remove(0L)));
            when(deptService.hasChildByDeptId(11L)).thenReturn(true);
            assertFalse(ok(controller.remove(11L)));
            when(deptService.hasChildByDeptId(11L)).thenReturn(false);
            when(deptService.checkDeptExistUser(11L)).thenReturn(true);
            assertFalse(ok(controller.remove(11L)));
            when(deptService.checkDeptExistUser(11L)).thenReturn(false);
            when(postService.countPostByDeptId(11L)).thenReturn(1L);
            assertFalse(ok(controller.remove(11L)));
            when(postService.countPostByDeptId(11L)).thenReturn(0L);
            when(deptService.deleteDeptById(11L)).thenReturn(1);
            assertTrue(ok(controller.remove(11L)));
            verify(deptService).checkDeptDataScope(11L);
        }

        @Test
        @DisplayName("排除自己及下级的部门列表")
        void excludeChild() {
            SysDeptVo self = new SysDeptVo();
            self.setDeptId(11L);
            self.setAncestors("0,10");
            SysDeptVo child = new SysDeptVo();
            child.setDeptId(12L);
            child.setAncestors("0,10,11");
            SysDeptVo other = new SysDeptVo();
            other.setDeptId(20L);
            other.setAncestors("0,10");
            when(deptService.selectDeptList(any())).thenReturn(new ArrayList<>(List.of(self, child, other)));

            assertThat(controller.excludeChild(11L).getData()).extracting(SysDeptVo::getDeptId).containsExactly(20L);
            controller.list(new SysDeptBo());
            controller.getInfo(11L);
            controller.optionselect(new Long[]{11L});
            verify(deptService).selectDeptByIds(List.of(11L));
        }
    }

    @Nested
    @DisplayName("在线用户")
    class Online {

        private final SysUserOnlineController controller = new SysUserOnlineController();
        private StpLogic original;
        private final StpLogic logic = mock(StpLogic.class);

        @BeforeEach
        void installLogic() {
            original = StpUtil.stpLogic;
            StpUtil.stpLogic = logic;
        }

        @AfterEach
        void restoreLogic() {
            StpUtil.stpLogic = original;
        }

        private void onlineTokens(String... tokens) {
            RKeys keys = mock(RKeys.class);
            when(SystemTestSupport.redisson().getKeys()).thenReturn(keys);
            when(keys.getKeysStream(any())).thenAnswer(inv -> Stream.of(tokens).map(t -> CacheNames.ONLINE_TOKEN_KEY + t));
            for (String token : tokens) {
                SysUserOnline online = new SysUserOnline();
                online.setUserName(token.startsWith("a") ? "alice" : "bob");
                online.setIpaddr(token.startsWith("a") ? "10.0.0.1" : "10.0.0.2");
                RBucket<Object> bucket = mock(RBucket.class);
                when(bucket.get()).thenReturn(online);
                when(SystemTestSupport.redisson().getBucket(CacheNames.ONLINE_TOKEN_KEY + token)).thenReturn(bucket);
            }
        }

        @Test
        @DisplayName("列表只返回令牌的 SHA-256，不返回令牌原值；可按 IP、用户名过滤")
        void listReturnsSessionIdOnly() {
            onlineTokens("a-token", "b-token");
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                stp.when(StpUtil::getTokenValue).thenReturn("a-token");

                List<SysUserOnlineVo> all = new ArrayList<>(controller.list(null, null).getData().getRows());
                assertEquals(2, all.size());
                assertThat(all).extracting(SysUserOnlineVo::getSessionId)
                    .containsExactlyInAnyOrder(OnlineSessionId.of("a-token"), OnlineSessionId.of("b-token"))
                    .doesNotContain("a-token", "b-token");
                assertThat(all).filteredOn(SysUserOnlineVo::isCurrent).extracting(SysUserOnlineVo::getUserName)
                    .containsExactly("alice");

                assertEquals(1, controller.list("10.0.0.2", null).getData().getRows().size());
                assertEquals(1, controller.list(null, "alice").getData().getRows().size());
                assertEquals(0, controller.list("10.0.0.2", "alice").getData().getRows().size());
            }
        }

        @Test
        @DisplayName("已过期的令牌不显示")
        void skipExpired() {
            onlineTokens("a-token");
            when(logic.getTokenActiveTimeoutByToken("a-token")).thenReturn(-2L);
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                stp.when(StpUtil::getTokenValue).thenThrow(new IllegalStateException("未登录"));
                assertTrue(controller.list(null, null).getData().getRows().isEmpty());
            }
        }

        @Test
        @DisplayName("强退按会话标识定位令牌；未知标识不做任何事；本人设备按本人的令牌定位")
        void forceLogout() {
            onlineTokens("a-token", "b-token");
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                controller.forceLogout(OnlineSessionId.of("b-token"));
                stp.verify(() -> StpUtil.kickoutByTokenValue("b-token"));

                controller.forceLogout("b-token");
                controller.forceLogout("0".repeat(64));
                stp.verify(() -> StpUtil.kickoutByTokenValue(anyString()), Mockito.times(1));

                stp.when(StpUtil::getLoginIdAsString).thenReturn("sys_user:100");
                stp.when(() -> StpUtil.getTokenValueListByLoginId("sys_user:100")).thenReturn(List.of("a-token"));
                stp.when(() -> StpUtil.kickoutByTokenValue("a-token")).thenThrow(NotLoginException.class);
                assertTrue(ok(controller.remove(OnlineSessionId.of("a-token"))));
                // 别人的会话不能通过本人接口踢出
                controller.remove(OnlineSessionId.of("b-token"));
                stp.verify(() -> StpUtil.kickoutByTokenValue("b-token"), Mockito.times(1));

                assertEquals(1, controller.getInfo().getData().getRows().size());
            }
        }
    }
}
