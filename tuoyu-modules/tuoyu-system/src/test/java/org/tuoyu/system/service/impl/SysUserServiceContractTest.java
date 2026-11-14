package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.mybatis.core.page.PageParam;
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
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.DataScopeProbe;
import org.tuoyu.system.support.SystemTestSupport;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户服务的行为契约
 */
@DisplayName("用户服务契约")
class SysUserServiceContractTest {

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
    private final CurrentUserStub login = CurrentUserStub.install();

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysUser user(Long id, String name) {
        SysUser user = new SysUser();
        user.setUserId(id);
        user.setUserName(name);
        user.setNickName("昵称" + id);
        return user;
    }

    private static List<CacheEvict> evictions(Method method) {
        List<CacheEvict> all = new ArrayList<>();
        CacheEvict single = method.getAnnotation(CacheEvict.class);
        if (single != null) {
            all.add(single);
        }
        Caching caching = method.getAnnotation(Caching.class);
        if (caching != null) {
            all.addAll(List.of(caching.evict()));
        }
        return all;
    }

    @Nested
    @DisplayName("新增与注册")
    class Insert {

        @Test
        @DisplayName("先检查租户用户数上限；写入用户后回填 ID，再写岗位与角色关联")
        void insertWritesUserPostsRoles() {
            login.userId(ADMIN);
            when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
                inv.<SysUser>getArgument(0).setUserId(100L);
                return 1;
            });
            when(postMapper.selectCount(any())).thenReturn(2L);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            SysUserBo bo = new SysUserBo();
            bo.setUserName("alice");
            bo.setNickName("Alice");
            bo.setPostIds(new Long[]{3L, 4L});
            bo.setRoleIds(new Long[]{2L});

            service.insertUser(bo);

            verify(quota).checkBeforeAdd();
            assertEquals(100L, bo.getUserId());
            ArgumentCaptor<Collection<SysUserPost>> posts = ArgumentCaptor.captor();
            verify(userPostMapper).insert(posts.capture());
            assertThat(posts.getValue()).extracting(SysUserPost::getPostId).containsExactly(3L, 4L);
            assertThat(posts.getValue()).extracting(SysUserPost::getUserId).containsOnly(100L);
            ArgumentCaptor<Collection<SysUserRole>> roles = ArgumentCaptor.captor();
            verify(userRoleMapper).insert(roles.capture());
            assertThat(roles.getValue()).extracting(SysUserRole::getRoleId).containsExactly(2L);
        }

        @Test
        @DisplayName("超出用户数上限时不写入")
        void quotaRejects() {
            doThrow(new ServiceException("已满")).when(quota).checkBeforeAdd();
            assertThatThrownBy(() -> service.insertUser(new SysUserBo())).isInstanceOf(ServiceException.class);
            verify(userMapper, never()).insert(any(SysUser.class));
        }

        @Test
        @DisplayName("岗位、角色不在数据权限范围内时拒绝")
        void relationsOutOfScope() {
            when(userMapper.insert(any(SysUser.class))).thenReturn(1);
            when(postMapper.selectCount(any())).thenReturn(1L);
            SysUserBo bo = new SysUserBo();
            bo.setPostIds(new Long[]{3L, 4L});
            assertThatThrownBy(() -> service.insertUser(bo)).hasMessage("没有权限访问岗位的数据");

            SysUserBo other = new SysUserBo();
            other.setRoleIds(new Long[]{5L});
            when(roleMapper.selectCount(any())).thenReturn(0L);
            assertThatThrownBy(() -> service.insertUser(other)).hasMessage("没有权限访问角色的数据");
        }

        @Test
        @DisplayName("普通用户只选了超级管理员角色时拒绝")
        void onlySuperAdminRole() {
            when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
                inv.<SysUser>getArgument(0).setUserId(100L);
                return 1;
            });
            SysUserBo bo = new SysUserBo();
            bo.setRoleIds(new Long[]{ADMIN_ROLE});
            assertThatThrownBy(() -> service.insertUser(bo))
                .hasMessage("不允许为普通用户分配超级管理员角色，请至少选择一个其他角色");
            verify(userRoleMapper, never()).insert(anyCollection());
        }

        @Test
        @DisplayName("请求中的创建人、修改人不写入（由审计填充决定）")
        void auditFieldsFromRequestIgnored() {
            when(userMapper.insert(any(SysUser.class))).thenReturn(1);
            SysUserBo bo = new SysUserBo();
            bo.setUserName("alice");
            bo.setCreateBy(5L);
            bo.setUpdateBy(5L);

            service.insertUser(bo);

            ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
            verify(userMapper).insert(saved.capture());
            assertNull(saved.getValue().getCreateBy());
            assertNull(saved.getValue().getUpdateBy());
        }

        @Test
        @DisplayName("注册：检查上限，创建人与修改人记为 0；没有写入时抛用户异常")
        void register() {
            when(userMapper.insert(any(SysUser.class))).thenReturn(1);
            SysUserBo bo = new SysUserBo();
            bo.setUserName("bob");
            service.registerUser(bo);
            verify(quota).checkBeforeAdd();
            ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
            verify(userMapper).insert(saved.capture());
            assertEquals(0L, saved.getValue().getCreateBy());
            assertEquals(0L, saved.getValue().getUpdateBy());

            when(userMapper.insert(any(SysUser.class))).thenReturn(0);
            assertThatThrownBy(() -> service.registerUser(new SysUserBo())).isInstanceOf(UserException.class);
        }
    }

    @Nested
    @DisplayName("修改")
    class Update {

        @Test
        @DisplayName("用户不存在（更新 0 行）时报错，消息带账号")
        void noRow() {
            when(userMapper.updateById(any(SysUser.class))).thenReturn(0);
            SysUserBo bo = new SysUserBo();
            bo.setUserId(100L);
            bo.setUserName("alice");
            assertThatThrownBy(() -> service.updateUser(bo)).hasMessage("修改用户alice信息失败");
        }

        @Test
        @DisplayName("角色、岗位：null 不修改，空数组清空，有值时先删后建")
        void relations() {
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
            SysUserBo keep = new SysUserBo();
            keep.setUserId(100L);
            service.updateUser(keep);
            verify(userRoleMapper, never()).deleteByUserId(any());
            verify(userPostMapper, never()).deleteByUserId(any());
            verify(userRoleMapper, never()).insert(anyCollection());

            SysUserBo clear = new SysUserBo();
            clear.setUserId(100L);
            clear.setRoleIds(new Long[0]);
            clear.setPostIds(new Long[0]);
            service.updateUser(clear);
            verify(userRoleMapper).deleteByUserId(100L);
            verify(userPostMapper).deleteByUserId(100L);
            verify(userRoleMapper, never()).insert(anyCollection());
            verify(userPostMapper, never()).insert(anyCollection());
        }

        @Test
        @DisplayName("修改用户不写入口令与审计字段（改口令只能走重置密码）")
        void passwordNotWritten() {
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
            SysUserBo bo = new SysUserBo();
            bo.setUserId(100L);
            bo.setUserName("alice");
            bo.setPassword("$2a$10$knownhash");
            bo.setCreateBy(5L);

            service.updateUser(bo);

            ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
            verify(userMapper).updateById(saved.capture());
            assertNull(saved.getValue().getPassword());
            assertNull(saved.getValue().getCreateBy());
            assertEquals("alice", saved.getValue().getUserName());
        }

        @Test
        @DisplayName("修改用户后清除该用户的账号与昵称缓存")
        void evictsNameCaches() throws Exception {
            Method method = SysUserServiceImpl.class.getMethod("updateUser", SysUserBo.class);
            List<String> names = evictions(method).stream().flatMap(e -> List.of(e.cacheNames()).stream()).toList();
            assertThat(names).contains(CacheNames.SYS_NICKNAME, CacheNames.SYS_USER_NAME);
            assertThat(evictions(method)).allMatch(e -> "#user.userId".equals(e.key()));
        }

        @Test
        @DisplayName("修改个人资料也清除昵称缓存")
        void profileEvictsNickname() throws Exception {
            Method method = SysUserServiceImpl.class.getMethod("updateUserProfile", SysUserBo.class);
            assertThat(evictions(method)).anyMatch(e -> List.of(e.cacheNames()).contains(CacheNames.SYS_NICKNAME));
        }

        @Test
        @DisplayName("分配角色：null 拒绝，空数组清空，普通用户去掉超级管理员角色")
        void authRoles() {
            assertThatThrownBy(() -> service.insertUserAuth(100L, null))
                .hasMessage("分配角色时必须提供角色列表（可以为空列表）");
            verify(userRoleMapper, never()).deleteByUserId(any());

            service.insertUserAuth(100L, new Long[0]);
            verify(userRoleMapper).deleteByUserId(100L);

            when(roleMapper.selectCount(any())).thenReturn(1L);
            service.insertUserAuth(101L, new Long[]{ADMIN_ROLE, 5L});
            ArgumentCaptor<Collection<SysUserRole>> roles = ArgumentCaptor.captor();
            verify(userRoleMapper).insert(roles.capture());
            assertThat(roles.getValue()).extracting(SysUserRole::getRoleId).containsExactly(5L);
            assertThat(roles.getValue()).extracting(SysUserRole::getUserId).containsOnly(101L);
        }

        @Test
        @DisplayName("超级管理员本人可以保留超级管理员角色；角色超出数据权限时原有角色不动")
        void authRolesSuperAdmin() {
            when(roleMapper.selectCount(any())).thenReturn(1L);
            service.insertUserAuth(ADMIN, new Long[]{ADMIN_ROLE});
            ArgumentCaptor<Collection<SysUserRole>> roles = ArgumentCaptor.captor();
            verify(userRoleMapper).insert(roles.capture());
            assertThat(roles.getValue()).extracting(SysUserRole::getRoleId).containsExactly(ADMIN_ROLE);

            when(roleMapper.selectCount(any())).thenReturn(0L);
            assertThatThrownBy(() -> service.insertUserAuth(100L, new Long[]{5L})).hasMessage("没有权限访问角色的数据");
            verify(userRoleMapper, never()).deleteByUserId(100L);
        }

        @Test
        @DisplayName("按字段更新：影响 0 行时给出各自的提示")
        void fieldUpdates() {
            when(userMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
            assertThatThrownBy(() -> service.updateUserStatus(100L, "1")).hasMessage("修改用户状态失败，用户不存在");
            SysUserBo profile = new SysUserBo();
            profile.setUserId(100L);
            profile.setNickName("n");
            assertThatThrownBy(() -> service.updateUserProfile(profile)).hasMessage("修改个人信息异常，请联系管理员");
            assertThatThrownBy(() -> service.updateUserAvatar(100L, 9L)).hasMessage("修改头像失败，用户不存在");
            assertThatThrownBy(() -> service.resetUserPwd(100L, "h")).hasMessage("修改用户密码失败，用户不存在");
        }

        @Test
        @DisplayName("个人资料只更新提交了的字段；空串表示清空")
        void profileOnlySubmittedFields() {
            when(userMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            SysUserBo profile = new SysUserBo();
            profile.setUserId(100L);
            profile.setNickName("新昵称");
            profile.setPhoneNumber("");
            service.updateUserProfile(profile);
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(userMapper).update(isNull(), wrapper.capture());
            assertThat(wrapper.getValue().getSqlSet()).contains("nick_name").contains("phone_number")
                .doesNotContain("email").doesNotContain("gender").doesNotContain("password");
            assertThat(wrapper.getValue().getSqlSegment()).contains("user_id");
        }

        @Test
        @DisplayName("状态、资料、头像、口令的更新都在数据权限下执行")
        void fieldUpdatesUnderDataScope() {
            DataScopeProbe<SysUserMapper> probe = DataScopeProbe.of(SysUserMapper.class).answer("update", a -> 1);
            SysUserServiceImpl scoped = new SysUserServiceImpl(probe.mapper(), deptMapper, roleMapper, postMapper,
                userRoleMapper, userPostMapper, quota);
            scoped.updateUserStatus(100L, "1");
            SysUserBo profile = new SysUserBo();
            profile.setUserId(100L);
            profile.setNickName("n");
            scoped.updateUserProfile(profile);
            scoped.updateUserAvatar(100L, 9L);
            scoped.resetUserPwd(100L, "h");
            assertThat(probe.calls("update")).hasSize(4)
                .allSatisfy(c -> assertThat(c.columns()).containsEntry("deptName", "dept_id").containsEntry("userName", "create_by"));
        }
    }

    @Nested
    @DisplayName("删除与检查")
    class DeleteAndChecks {

        @Test
        @DisplayName("批量删除：包含超级管理员、超出数据权限时拒绝；都通过后删除关联与用户")
        void deleteUsers() {
            login.userId(ADMIN);
            assertThatThrownBy(() -> service.deleteUserByIds(new Long[]{100L, ADMIN})).hasMessage("不允许操作超级管理员用户");
            verify(userMapper, never()).deleteByIds(anyList());

            login.userId(2L);
            when(userMapper.selectCount(any())).thenReturn(0L);
            assertThatThrownBy(() -> service.deleteUserByIds(new Long[]{100L})).hasMessage("没有权限访问用户数据！");

            login.userId(ADMIN);
            when(userMapper.deleteByIds(anyList())).thenReturn(1);
            service.deleteUserByIds(new Long[]{100L});
            verify(userRoleMapper).delete(any());
            verify(userPostMapper).delete(any());

            when(userMapper.deleteByIds(anyList())).thenReturn(0);
            assertThatThrownBy(() -> service.deleteUserByIds(new Long[]{101L})).hasMessage("删除用户失败!");
        }

        @Test
        @DisplayName("不允许操作超级管理员；空 ID 不检查")
        void allowed() {
            assertThatThrownBy(() -> service.checkUserAllowed(ADMIN)).hasMessage("不允许操作超级管理员用户");
            service.checkUserAllowed(2L);
            service.checkUserAllowed(null);
        }

        @Test
        @DisplayName("用户数据权限：按带数据权限的计数判断，超级管理员与空 ID 跳过")
        void dataScope() {
            DataScopeProbe<SysUserMapper> probe = DataScopeProbe.of(SysUserMapper.class).answer("selectCount", a -> 1L);
            SysUserServiceImpl scoped = new SysUserServiceImpl(probe.mapper(), deptMapper, roleMapper, postMapper,
                userRoleMapper, userPostMapper, quota);
            scoped.checkUserDataScope(null);
            login.userId(ADMIN);
            scoped.checkUserDataScope(100L);
            assertThat(probe.calls()).isEmpty();

            login.userId(2L);
            scoped.checkUserDataScope(100L);
            assertThat(probe.calls("selectCount")).singleElement()
                .satisfies(c -> assertThat(c.permission()).isNotNull());

            probe.answer("selectCount", a -> 0L);
            assertThatThrownBy(() -> scoped.checkUserDataScope(100L)).hasMessage("没有权限访问用户数据！");
        }

        @Test
        @DisplayName("账号、手机号、邮箱唯一性：存在其他同值用户时不唯一")
        void uniqueness() {
            SysUserBo bo = new SysUserBo();
            bo.setUserId(100L);
            bo.setUserName("alice");
            bo.setPhoneNumber("13800000000");
            bo.setEmail("a@example.com");
            when(userMapper.exists(any())).thenReturn(false);
            when(userMapper.selectCount(any())).thenReturn(0L);
            assertThat(service.checkUserNameUnique(bo)).isTrue();
            assertThat(service.checkPhoneUnique(bo)).isTrue();
            assertThat(service.checkEmailUnique(bo)).isTrue();
            when(userMapper.exists(any())).thenReturn(true);
            when(userMapper.selectCount(any())).thenReturn(1L);
            assertThat(service.checkUserNameUnique(bo)).isFalse();
            assertThat(service.checkPhoneUnique(bo)).isFalse();
            assertThat(service.checkEmailUnique(bo)).isFalse();
        }
    }

    @Nested
    @DisplayName("查询")
    class Queries {

        @Test
        @DisplayName("分页、按 ID 列表、部门下用户、导出与导出统计都在数据权限下执行")
        void listsUnderDataScope() {
            DataScopeProbe<SysUserMapper> probe = DataScopeProbe.of(SysUserMapper.class);
            SysUserServiceImpl scoped = new SysUserServiceImpl(probe.mapper(), deptMapper, roleMapper, postMapper,
                userRoleMapper, userPostMapper, quota);
            scoped.selectPageUserList(new SysUserBo(), new PageParam());
            scoped.selectUserByIds(List.of(1L), null);
            assertThat(probe.calls()).isNotEmpty().allSatisfy(c -> assertThat(c.permission()).isNotNull());

            probe.calls().clear();
            probe.answer("selectJoinPage", a -> a[0]);
            scoped.countUserExportList(new SysUserBo());
            scoped.selectUserExportList(new SysUserBo());
            assertThat(probe.calls()).isNotEmpty().allSatisfy(c -> assertThat(c.columns())
                .containsEntry("deptName", "d.dept_id").containsEntry("userName", "u.create_by"));
        }

        @Test
        @DisplayName("部门下的用户按数据权限过滤")
        void usersOfDeptUnderDataScope() {
            DataScopeProbe<SysUserMapper> probe = DataScopeProbe.of(SysUserMapper.class);
            SysUserServiceImpl scoped = new SysUserServiceImpl(probe.mapper(), deptMapper, roleMapper, postMapper,
                userRoleMapper, userPostMapper, quota);
            scoped.selectUserListByDept(10L);
            assertThat(probe.calls()).isNotEmpty().allSatisfy(c -> assertThat(c.permission()).isNotNull());
        }

        @Test
        @DisplayName("选择用户：只查正常用户、只取识别列，只能按 ID、账号、昵称排序")
        void options() {
            when(userMapper.selectList(any(com.baomidou.mybatisplus.core.metadata.IPage.class), any())).thenReturn(List.of());
            SysUserBo bo = new SysUserBo();
            bo.setExcludeUserIds("1,2");
            service.selectUserOptionPage(bo, new PageParam());
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(userMapper).selectList(any(com.baomidou.mybatisplus.core.metadata.IPage.class), wrapper.capture());
            assertThat(wrapper.getValue().getSqlSelect()).contains("user_id").contains("user_name")
                .contains("nick_name").contains("dept_id").doesNotContain("phone").doesNotContain("email").doesNotContain("password");
            assertThat(wrapper.getValue().getSqlSegment()).contains("status").contains("NOT IN");

            PageParam phone = new PageParam();
            phone.setSort(List.of("phoneNumber"));
            assertThatThrownBy(() -> service.selectUserOptionPage(new SysUserBo(), phone)).isInstanceOf(ServiceException.class);
        }

        @Test
        @DisplayName("按 ID 查询带出角色；不存在时为 null")
        void byId() {
            when(userMapper.selectById(100L)).thenReturn(user(100L, "alice"));
            SysRoleVo role = new SysRoleVo();
            role.setRoleName("r");
            when(roleMapper.selectRolesByUserId(100L)).thenReturn(List.of(role));
            SysUserVo vo = service.selectUserById(100L);
            assertEquals("alice", vo.getUserName());
            assertThat(vo.getRoles()).hasSize(1);
            assertNull(service.selectUserById(999L));
        }

        @Test
        @DisplayName("角色组、岗位组：逗号连接名称，没有时为空串")
        void groups() {
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
        @DisplayName("按 ID 查账号、昵称带缓存（键为用户 ID）；多个昵称拼接时跳过不存在的")
        void namesAndCaches() throws Exception {
            Cacheable userName = SysUserServiceImpl.class.getMethod("selectUserNameById", Long.class).getAnnotation(Cacheable.class);
            assertThat(userName.cacheNames()).containsExactly(CacheNames.SYS_USER_NAME);
            assertEquals("#userId", userName.key());
            Cacheable nick = SysUserServiceImpl.class.getMethod("selectNicknameById", Long.class).getAnnotation(Cacheable.class);
            assertThat(nick.cacheNames()).containsExactly(CacheNames.SYS_NICKNAME);

            SystemTestSupport.bean(service);
            doReturn(user(1L, "a")).doReturn(null).doReturn(user(3L, "c")).when(userMapper).selectOne(any(Wrapper.class));
            assertEquals("昵称1,昵称3", service.selectNicknameByIds("1,2,3"));
        }

        @Test
        @DisplayName("按角色查用户 ID：角色为空时返回空列表")
        void userIdsByRoles() {
            assertThat(service.selectUserIdsByRoleIds(List.of())).isEmpty();
            SysUserRole ur = new SysUserRole();
            ur.setUserId(100L);
            ur.setRoleId(2L);
            when(userRoleMapper.selectList(any())).thenReturn(List.of(ur));
            assertEquals(List.of(100L), service.selectUserIdsByRoleIds(List.of(2L)));
        }
    }
}
