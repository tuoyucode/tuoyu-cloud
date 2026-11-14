package org.tuoyu.system.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.ServiceFactory;
import org.tuoyu.system.support.SystemTestSupport;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S1 补充规则 3（用户侧）与规则 4：给用户分配的角色，菜单不能超出操作者的菜单；
 * 角色的已分配、未分配用户列表先检查角色的数据权限
 */
@DisplayName("S1 补充：用户的角色分配与角色的用户列表")
class SysUserServiceS1bContractTest {

    static {
        SystemTestSupport.init();
    }

    private static final Long OPERATOR = 2L;

    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysPostMapper postMapper = SystemTestSupport.mapper(SysPostMapper.class);
    private final SysUserRoleMapper userRoleMapper = SystemTestSupport.mapper(SysUserRoleMapper.class);
    private final SysUserPostMapper userPostMapper = SystemTestSupport.mapper(SysUserPostMapper.class);
    private final TenantAccountQuota quota = mock(TenantAccountQuota.class);
    private final SysUserServiceImpl service = ServiceFactory.create(SysUserServiceImpl.class, userMapper, deptMapper, roleMapper,
        postMapper, userRoleMapper, userPostMapper, quota);
    private final CurrentUserStub login = CurrentUserStub.install();

    @AfterEach
    void tearDown() {
        login.close();
    }

    @Nested
    @DisplayName("规则 3（用户侧）：分配角色")
    class AssignRoles {

        @BeforeEach
        void setUp() throws Exception {
            login.userId(OPERATOR);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            when(userMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
                inv.<SysUser>getArgument(0).setUserId(100L);
                return 1;
            });
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
            SysRoleVo own = new SysRoleVo();
            own.setRoleId(10L);
            own.setStatus(SystemConstants.NORMAL);
            SysRoleVo disabled = new SysRoleVo();
            disabled.setRoleId(11L);
            disabled.setStatus(SystemConstants.DISABLE);
            doReturn(List.of(own, disabled)).when(roleMapper).selectRolesByUserId(eq(OPERATOR));
            // 操作者的正常角色 10 有菜单 1、2；停用的角色 11 的菜单 3 不算；角色 5 有菜单 1、3，角色 6 只有菜单 1
            roleMenus(Map.of(10L, Set.of(1L, 2L), 11L, Set.of(3L), 5L, Set.of(1L, 3L), 6L, Set.of(1L)));
        }

        /**
         * 角色的菜单：SysRoleMapper#selectMenuIdsByRoleIds（按角色打桩，用反射取得方法）
         */
        private void roleMenus(Map<Long, Set<Long>> menusByRole) throws Exception {
            Method method = SysRoleMapper.class.getMethod("selectMenuIdsByRoleIds", Collection.class);
            Object stub = doAnswer(inv -> {
                Set<Long> result = new HashSet<>();
                Collection<?> roleIds = inv.getArgument(0);
                if (roleIds != null) {
                    roleIds.forEach(id -> result.addAll(menusByRole.getOrDefault((Long) id, Set.of())));
                }
                return result;
            }).when(roleMapper);
            method.invoke(stub, (Object) anyCollection());
        }

        private SysUserBo user(Long... roleIds) {
            SysUserBo bo = new SysUserBo();
            bo.setUserId(100L);
            bo.setUserName("alice");
            bo.setNickName("Alice");
            bo.setRoleIds(roleIds);
            return bo;
        }

        @Test
        @DisplayName("新增用户、修改用户、分配角色：有一个角色的菜单超出操作者的菜单就整体拒绝，原有角色不动")
        void rejectRolesWithMoreMenus() {
            String message = "只能分配菜单权限不超过自己的角色";
            assertThatThrownBy(() -> service.insertUser(user(6L, 5L))).isInstanceOf(ServiceException.class).hasMessage(message);
            assertThatThrownBy(() -> service.updateUser(user(6L, 5L))).hasMessage(message);
            assertThatThrownBy(() -> service.insertUserAuth(100L, new Long[]{5L})).hasMessage(message);
            verify(userRoleMapper, never()).deleteByUserId(any());
            verify(userRoleMapper, never()).insert(anyCollection());
        }

        @Test
        @DisplayName("角色的菜单都在操作者的菜单内时照常分配；超级管理员不检查")
        void allowRolesWithinMenus() {
            service.insertUserAuth(100L, new Long[]{6L});
            login.userId(SystemConstants.SUPER_ADMIN_USER_ID);
            service.insertUserAuth(100L, new Long[]{5L});
            verify(userRoleMapper, org.mockito.Mockito.times(2)).insert(anyCollection());
        }
    }

    @Nested
    @DisplayName("规则 4：角色的已分配、未分配用户列表")
    class AllocatedLists {

        private SysUserBo query() {
            SysUserBo bo = new SysUserBo();
            bo.setRoleId(5L);
            return bo;
        }

        @Test
        @DisplayName("角色不在操作者的数据权限内时拒绝，不查询用户")
        void rejectRoleOutOfScope() {
            login.userId(OPERATOR);
            when(roleMapper.selectCount(any())).thenReturn(0L);
            assertThatThrownBy(() -> service.selectAllocatedList(query(), new PageParam())).hasMessage("没有权限访问部分角色数据！");
            assertThatThrownBy(() -> service.selectUnallocatedList(query(), new PageParam())).hasMessage("没有权限访问部分角色数据！");
            verify(userMapper, never()).selectJoinPage(any(), any(), any());
        }

        @Test
        @DisplayName("角色在范围内、超级管理员、没有指定角色时照常查询")
        void allowed() {
            doReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>()).when(userMapper)
                .selectJoinPage(any(), any(), any());
            login.userId(OPERATOR);
            when(roleMapper.selectCount(any())).thenReturn(1L);
            service.selectAllocatedList(query(), new PageParam());
            service.selectUnallocatedList(query(), new PageParam());
            login.userId(SystemConstants.SUPER_ADMIN_USER_ID);
            when(roleMapper.selectCount(any())).thenReturn(0L);
            service.selectAllocatedList(query(), new PageParam());
            service.selectAllocatedList(new SysUserBo(), new PageParam());
            verify(userMapper, org.mockito.Mockito.times(4)).selectJoinPage(any(), any(), any());
        }
    }
}
