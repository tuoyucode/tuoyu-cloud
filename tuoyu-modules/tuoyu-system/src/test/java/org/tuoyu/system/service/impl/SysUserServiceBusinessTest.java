package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("用户管理：业务规则")
class SysUserServiceBusinessTest {

    static {
        SystemTestSupport.init();
    }

    private static final Long ADMIN = SystemConstants.SUPER_ADMIN_USER_ID;
    private static final Long ADMIN_ROLE = SystemConstants.SUPER_ADMIN_ROLE_ID;

    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysPostMapper postMapper = SystemTestSupport.mapper(SysPostMapper.class);
    private final SysUserRoleMapper userRoleMapper = SystemTestSupport.mapper(SysUserRoleMapper.class);
    private final SysUserPostMapper userPostMapper = SystemTestSupport.mapper(SysUserPostMapper.class);
    private final TenantAccountQuota quota = mock(TenantAccountQuota.class);

    private final SysUserServiceImpl service = new SysUserServiceImpl(userMapper, deptMapper, roleMapper, postMapper,
        userRoleMapper, userPostMapper, quota);

    private final MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class, CALLS_REAL_METHODS);

    @AfterEach
    void closeStatic() {
        login.close();
    }

    private void loginAs(Long userId) {
        login.when(LoginHelper::getUserId).thenReturn(userId);
    }

    private static SysUser user(Long id, String name) {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setUserName(name);
        user.setNickName("昵称" + id);
        user.setPhoneNumber("1380000000" + id);
        user.setEmail(name + "@example.com");
        user.setPassword("hashed");
        return user;
    }

    @SuppressWarnings("unchecked")
    private static <T> Wrapper<T> captured(ArgumentCaptor<Wrapper> captor) {
        return captor.getValue();
    }

    @Nested
    @DisplayName("新增与注册")
    class Insert {

        @Test
        @DisplayName("新增用户先检查租户用户数上限，再写入用户、岗位、角色")
        void insertUserWritesPostsAndRoles() {
            loginAs(ADMIN);
            when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
                inv.<SysUser>getArgument(0).setUserId(100L);
                return 1;
            });
            when(postMapper.selectPostCount(anyList())).thenReturn(2L);
            when(roleMapper.selectRoleCount(anyList())).thenReturn(1L);
            SysUserBo bo = new SysUserBo();
            bo.setUserName("alice");
            bo.setNickName("Alice");
            bo.setPostIds(new Long[]{3L, 4L});
            bo.setRoleIds(new Long[]{2L});

            assertEquals(1, service.insertUser(bo));

            verify(quota).checkBeforeAdd();
            assertEquals(100L, bo.getUserId());
            ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
            verify(userMapper).insert(saved.capture());
            assertEquals("alice", saved.getValue().getUserName());
            assertEquals("Alice", saved.getValue().getNickName());

            ArgumentCaptor<Collection<SysUserPost>> posts = ArgumentCaptor.captor();
            verify(userPostMapper).insertBatch(posts.capture());
            assertThat(posts.getValue()).extracting(SysUserPost::getPostId).containsExactly(3L, 4L);
            assertThat(posts.getValue()).allMatch(p -> p.getUserId() == 100L);

            ArgumentCaptor<Collection<SysUserRole>> roles = ArgumentCaptor.captor();
            verify(userRoleMapper).insertBatch(roles.capture());
            assertThat(roles.getValue()).extracting(SysUserRole::getRoleId).containsExactly(2L);
            // 新增时不清理旧的关联
            verify(userRoleMapper, never()).deleteByUserId(any());
        }

        @Test
        @DisplayName("超出上限时拒绝，不写入任何数据")
        void quotaRejects() {
            org.mockito.Mockito.doThrow(new ServiceException("已满")).when(quota).checkBeforeAdd();

            assertThrows(ServiceException.class, () -> service.insertUser(new SysUserBo()));
            verify(userMapper, never()).insert(any(SysUser.class));
        }

        @Test
        @DisplayName("岗位不在数据权限范围内时拒绝")
        void rejectPostsOutOfScope() {
            when(userMapper.insert(any(SysUser.class))).thenReturn(1);
            when(postMapper.selectPostCount(anyList())).thenReturn(1L);
            SysUserBo bo = new SysUserBo();
            bo.setPostIds(new Long[]{3L, 4L});

            ServiceException ex = assertThrows(ServiceException.class, () -> service.insertUser(bo));
            assertTrue(ex.getMessage().contains("岗位"));
            verify(userPostMapper, never()).insertBatch(any());
        }

        @Test
        @DisplayName("普通用户只选了超级管理员角色时拒绝")
        void rejectOnlySuperAdminRole() {
            when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
                inv.<SysUser>getArgument(0).setUserId(100L);
                return 1;
            });
            SysUserBo bo = new SysUserBo();
            bo.setRoleIds(new Long[]{ADMIN_ROLE});

            ServiceException ex = assertThrows(ServiceException.class, () -> service.insertUser(bo));
            assertTrue(ex.getMessage().contains("超级管理员"));
            verify(userRoleMapper, never()).insertBatch(any());
        }

        @Test
        @DisplayName("普通用户的角色中去掉超级管理员角色，其余照常分配")
        void dropSuperAdminRoleForNormalUser() {
            when(roleMapper.selectRoleCount(anyList())).thenReturn(1L);

            service.insertUserAuth(100L, new Long[]{ADMIN_ROLE, 5L});

            ArgumentCaptor<Collection<SysUserRole>> roles = ArgumentCaptor.captor();
            verify(userRoleMapper).insertBatch(roles.capture());
            assertThat(roles.getValue()).extracting(SysUserRole::getRoleId).containsExactly(5L);
            verify(userRoleMapper).deleteByUserId(100L);
        }

        @Test
        @DisplayName("超级管理员本人可以保留超级管理员角色")
        void superAdminKeepsSuperAdminRole() {
            when(roleMapper.selectRoleCount(anyList())).thenReturn(1L);

            service.insertUserAuth(ADMIN, new Long[]{ADMIN_ROLE});

            ArgumentCaptor<Collection<SysUserRole>> roles = ArgumentCaptor.captor();
            verify(userRoleMapper).insertBatch(roles.capture());
            assertThat(roles.getValue()).extracting(SysUserRole::getRoleId).containsExactly(ADMIN_ROLE);
        }

        @Test
        @DisplayName("角色不在数据权限范围内时拒绝，原有角色不动")
        void rejectRolesOutOfScope() {
            when(roleMapper.selectRoleCount(anyList())).thenReturn(0L);

            assertThrows(ServiceException.class, () -> service.insertUserAuth(100L, new Long[]{5L}));
            verify(userRoleMapper, never()).deleteByUserId(any());
        }

        @Test
        @DisplayName("注册用户：创建人、修改人记为 0，并检查用户数上限")
        void registerUser() {
            when(userMapper.insert(any(SysUser.class))).thenReturn(1);
            SysUserBo bo = new SysUserBo();
            bo.setUserName("bob");

            assertTrue(service.registerUser(bo));

            verify(quota).checkBeforeAdd();
            ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
            verify(userMapper).insert(saved.capture());
            assertEquals(0L, saved.getValue().getCreateBy());
            assertEquals(0L, saved.getValue().getUpdateBy());
        }
    }

    @Nested
    @DisplayName("修改与删除")
    class UpdateAndDelete {

        @Test
        @DisplayName("修改用户：影响行数为 0 时报错（防止误删关联后静默成功）")
        void updateUserFailsWhenNoRow() {
            SysUserBo bo = new SysUserBo();
            bo.setUserId(100L);
            bo.setUserName("alice");
            when(userMapper.updateById(any(SysUser.class))).thenReturn(0);

            assertThrows(ServiceException.class, () -> service.updateUser(bo));
        }

        @Test
        @DisplayName("修改用户：带角色、岗位时先清除旧关联再写入")
        void updateUserReplacesRelations() {
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
            when(postMapper.selectPostCount(anyList())).thenReturn(1L);
            when(roleMapper.selectRoleCount(anyList())).thenReturn(1L);
            when(userPostMapper.delete(any())).thenReturn(1);
            SysUserBo bo = new SysUserBo();
            bo.setUserId(100L);
            bo.setRoleIds(new Long[]{2L});
            bo.setPostIds(new Long[]{3L});

            assertEquals(1, service.updateUser(bo));

            verify(userRoleMapper).deleteByUserId(100L);
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(userPostMapper).delete(wrapper.capture());
            assertThat(captured(wrapper).getSqlSegment()).contains("user_id");
            verify(userRoleMapper).insertBatch(any());
            verify(userPostMapper).insertBatch(any());
        }

        @Test
        @DisplayName("删除单个用户：先删角色、岗位关联；用户不存在时报错")
        void deleteUserById() {
            when(userMapper.deleteById(100L)).thenReturn(1);
            assertEquals(1, service.deleteUserById(100L));
            verify(userRoleMapper).delete(any());
            verify(userPostMapper).delete(any());

            when(userMapper.deleteById(101L)).thenReturn(0);
            assertThrows(ServiceException.class, () -> service.deleteUserById(101L));
        }

        @Test
        @DisplayName("批量删除：包含超级管理员时拒绝，不删除任何数据")
        void deleteUsersRejectsSuperAdmin() {
            loginAs(ADMIN);

            assertThrows(ServiceException.class, () -> service.deleteUserByIds(new Long[]{100L, ADMIN}));
            verify(userMapper, never()).deleteByIds(anyList());
        }

        @Test
        @DisplayName("批量删除：非超级管理员操作数据权限外的用户时拒绝")
        void deleteUsersRejectsOutOfScope() {
            loginAs(2L);
            when(userMapper.selectCount(any())).thenReturn(0L);

            ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteUserByIds(new Long[]{100L}));
            assertTrue(ex.getMessage().contains("没有权限"));
            verify(userMapper, never()).deleteByIds(anyList());
        }

        @Test
        @DisplayName("批量删除：通过检查后删除用户及关联；没有删除任何行时报错")
        void deleteUsers() {
            loginAs(ADMIN);
            when(userMapper.deleteByIds(anyList())).thenReturn(2);
            assertEquals(2, service.deleteUserByIds(new Long[]{100L, 101L}));
            verify(userRoleMapper).delete(any());
            verify(userPostMapper).delete(any());

            when(userMapper.deleteByIds(anyList())).thenReturn(0);
            assertThrows(ServiceException.class, () -> service.deleteUserByIds(new Long[]{102L}));
        }

        @Test
        @DisplayName("修改状态、个人资料、重置密码都按用户 ID 更新")
        void updateFields() {
            when(userMapper.update(any(Wrapper.class))).thenReturn(1);

            assertEquals(1, service.updateUserStatus(100L, "1"));
            SysUserBo profile = new SysUserBo();
            profile.setUserId(100L);
            profile.setNickName("新昵称");
            assertEquals(1, service.updateUserProfile(profile));
            assertEquals(1, service.resetUserPwd(100L, "hash"));

            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(userMapper, org.mockito.Mockito.times(3)).update(wrapper.capture());
            List<Wrapper> all = wrapper.getAllValues();
            assertThat(all.get(0).getSqlSet()).contains("status");
            assertThat(all.get(1).getSqlSet()).contains("nick_name").doesNotContain("email");
            assertThat(all.get(2).getSqlSet()).contains("password");
            all.forEach(w -> assertThat(w.getSqlSegment()).contains("user_id"));
        }
    }

    @Nested
    @DisplayName("权限检查")
    class Checks {

        @Test
        @DisplayName("不允许操作超级管理员；ID 为空时不检查")
        void checkUserAllowed() {
            assertThrows(ServiceException.class, () -> service.checkUserAllowed(ADMIN));
            service.checkUserAllowed(2L);
            service.checkUserAllowed(null);
        }

        @Test
        @DisplayName("数据权限：超级管理员与空 ID 直接通过，其他人按带数据权限的计数判断")
        void checkUserDataScope() {
            service.checkUserDataScope(null);
            verify(userMapper, never()).selectCount(any());

            loginAs(ADMIN);
            service.checkUserDataScope(100L);
            verify(userMapper, never()).selectCount(any());

            loginAs(2L);
            when(userMapper.selectCount(any())).thenReturn(1L);
            service.checkUserDataScope(100L);
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(userMapper).selectCount(wrapper.capture());
            assertThat(captured(wrapper).getSqlSegment()).contains("user_id");

            when(userMapper.selectCount(any())).thenReturn(0L);
            assertThrows(ServiceException.class, () -> service.checkUserDataScope(100L));
        }

        @Test
        @DisplayName("用户名、手机号、邮箱唯一性：存在其他同值用户时不唯一")
        void uniqueness() {
            SysUserBo bo = new SysUserBo();
            bo.setUserId(100L);
            bo.setUserName("alice");
            bo.setPhoneNumber("13800000000");
            bo.setEmail("a@example.com");

            when(userMapper.exists(any())).thenReturn(false);
            assertTrue(service.checkUserNameUnique(bo));
            assertTrue(service.checkPhoneUnique(bo));
            assertTrue(service.checkEmailUnique(bo));

            when(userMapper.exists(any())).thenReturn(true);
            assertFalse(service.checkUserNameUnique(bo));
            assertFalse(service.checkPhoneUnique(bo));
            assertFalse(service.checkEmailUnique(bo));

            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(userMapper, org.mockito.Mockito.atLeastOnce()).exists(wrapper.capture());
            // 修改时排除自己
            assertThat(wrapper.getAllValues().get(0).getSqlSegment()).contains("user_name").contains("<>");
        }
    }

    @Nested
    @DisplayName("查询")
    class Queries {

        @Test
        @DisplayName("分页列表：按部门查询时包含子部门，排除指定用户")
        void pageList() {
            doReturn(List.of(10L, 11L)).when(deptMapper).selectDeptAndChildById(10L);
            when(userMapper.selectList(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any()))
                .thenReturn(List.of(user(100L, "alice")));
            SysUserBo bo = new SysUserBo();
            bo.setDeptId(10L);
            bo.setUserName("ali");
            bo.setExcludeUserIds("1,2");

            PageResult<SysUserVo> result = service.selectPageUserList(bo, new PageQuery());

            assertEquals(1, result.getRows().size());
            assertEquals("alice", result.getRows().iterator().next().getUserName());
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(userMapper).selectList(any(com.baomidou.mybatisplus.core.metadata.IPage.class), wrapper.capture());
            String sql = captured(wrapper).getSqlSegment();
            assertThat(sql).contains("dept_id IN").contains("user_name LIKE").contains("NOT IN");
        }

        @Test
        @DisplayName("按 ID 查询时带出角色；不存在时返回 null")
        void selectUserById() {
            when(userMapper.selectById(100L)).thenReturn(user(100L, "alice"));
            SysRoleVo role = new SysRoleVo();
            role.setRoleName("管理员");
            when(roleMapper.selectRolesByUserId(100L)).thenReturn(List.of(role));

            SysUserVo vo = service.selectUserById(100L);

            assertEquals("alice", vo.getUserName());
            assertEquals(1, vo.getRoles().size());
            assertNull(service.selectUserById(999L));
        }

        @Test
        @DisplayName("按用户名、手机号查询单个用户")
        void selectByNameOrPhone() {
            when(userMapper.selectOne(any(), anyBoolean())).thenReturn(user(100L, "alice"));

            assertEquals(100L, service.selectUserByUserName("alice").getUserId());
            assertEquals(100L, service.selectUserByPhoneNumber("13800000000").getUserId());
        }

        @Test
        @DisplayName("角色组、岗位组拼成逗号分隔的名称；没有时为空串")
        void roleAndPostGroup() {
            SysRoleVo r1 = new SysRoleVo();
            r1.setRoleName("A");
            SysRoleVo r2 = new SysRoleVo();
            r2.setRoleName("B");
            when(roleMapper.selectRolesByUserId(100L)).thenReturn(List.of(r1, r2));
            SysPostVo p = new SysPostVo();
            p.setPostName("经理");
            when(postMapper.selectPostsByUserId(100L)).thenReturn(List.of(p));

            assertEquals("A,B", service.selectUserRoleGroup(100L));
            assertEquals("经理", service.selectUserPostGroup(100L));
            assertEquals("", service.selectUserRoleGroup(200L));
            assertEquals("", service.selectUserPostGroup(200L));
        }

        @Test
        @DisplayName("按角色查用户 ID：角色为空时不查库")
        void userIdsByRoles() {
            assertTrue(service.selectUserIdsByRoleIds(List.of()).isEmpty());
            verify(userRoleMapper, never()).selectList(any());

            SysUserRole ur = new SysUserRole();
            ur.setUserId(100L);
            ur.setRoleId(2L);
            when(userRoleMapper.selectList(any())).thenReturn(List.of(ur));
            assertEquals(List.of(100L), service.selectUserIdsByRoleIds(List.of(2L)));
        }

        @Test
        @DisplayName("按 ID 查用户名、昵称、手机、邮箱；不存在时为 null")
        void singleFields() {
            when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(100L, "alice"));
            assertEquals("alice", service.selectUserNameById(100L));
            assertEquals("昵称100", service.selectNicknameById(100L));
            assertEquals("1380000000100", service.selectPhonenumberById(100L));
            assertEquals("alice@example.com", service.selectEmailById(100L));

            when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
            assertNull(service.selectUserNameById(100L));
        }

        @Test
        @DisplayName("多个昵称拼接时跳过不存在的用户")
        void nicknames() {
            SystemTestSupport.bean(service);
            when(userMapper.selectOne(any(Wrapper.class)))
                .thenReturn(user(1L, "a"))
                .thenReturn(null)
                .thenReturn(user(3L, "c"));

            assertEquals("昵称1,昵称3", service.selectNicknameByIds("1,2,3"));
        }

        @Test
        @DisplayName("全部用户 ID、部门下用户、按 ID 列表查询")
        void lists() {
            when(userMapper.selectObjs(any())).thenReturn(List.of(1L, 2L));
            assertEquals(List.of(1L, 2L), service.selectAllUserIds());

            when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(user(100L, "alice")));
            assertEquals(1, service.selectUserListByDept(10L).size());
            assertEquals(1, service.selectUserByIds(List.of(100L), 10L).size());
        }

        @Test
        @DisplayName("已分配、未分配角色的用户列表；未分配时排除已有该角色的用户")
        void allocated() {
            Page<SysUserVo> page = new Page<>(1, 10);
            page.setRecords(List.of(new SysUserVo()));
            page.setTotal(1);
            doReturn(page).when(userMapper).selectJoinPage(any(), eq(SysUserVo.class), any());
            SysUserBo bo = new SysUserBo();
            bo.setRoleId(2L);
            bo.setUserName("a");

            assertEquals(1, service.selectAllocatedList(bo, new PageQuery()).getTotal());

            when(userRoleMapper.selectUserIdsByRoleId(2L)).thenReturn(List.of(100L));
            assertEquals(1, service.selectUnallocatedList(bo, new PageQuery()).getTotal());
        }

        @Test
        @DisplayName("导出与导出统计：按部门查询时包含子部门")
        void export() {
            doReturn(List.of(10L, 11L)).when(deptMapper).selectDeptAndChildById(10L);
            Page<Object> counted = new Page<>();
            counted.setTotal(7);
            doReturn(counted).when(userMapper).selectJoinPage(any(), any(), any());
            SysUserBo bo = new SysUserBo();
            bo.setDeptId(10L);

            assertEquals(7, service.countUserExportList(bo));
            service.selectUserExportList(bo);
            verify(deptMapper, org.mockito.Mockito.times(2)).selectDeptAndChildById(10L);
        }
    }
}
