package org.tuoyu.system.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("用户管理：分配角色")
class SysUserServiceImplTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserPostMapper userPostMapper = mock(SysUserPostMapper.class);

    private final SysUserServiceImpl service = new SysUserServiceImpl(userMapper, mock(SysDeptMapper.class),
        roleMapper, mock(SysPostMapper.class), userRoleMapper, userPostMapper, mock(TenantAccountQuota.class));

    @Test
    @DisplayName("缺少角色列表时拒绝，原有角色不动")
    void rejectMissingRoleIds() {
        assertThrows(ServiceException.class, () -> service.insertUserAuth(100L, null));
        verifyNoInteractions(roleMapper, userRoleMapper);
    }

    @Test
    @DisplayName("空列表表示清空全部角色")
    void clearWithEmptyRoleIds() {
        service.insertUserAuth(100L, new Long[0]);

        verify(userRoleMapper).deleteByUserId(100L);
        verifyNoMoreInteractions(userRoleMapper);
        verifyNoInteractions(roleMapper);
    }

    @Test
    @DisplayName("修改用户：角色、岗位为空数组时清空，未提交（null）时不修改")
    void updateUserClearsEmptyRolesAndPosts() {
        SysUserBo bo = new SysUserBo();
        bo.setUserId(100L);
        bo.setRoleIds(new Long[0]);
        bo.setPostIds(new Long[0]);

        service.replaceRolesAndPosts(bo);

        verify(userRoleMapper).deleteByUserId(100L);
        verify(userPostMapper).deleteByUserId(100L);

        SysUserBo untouched = new SysUserBo();
        untouched.setUserId(101L);
        service.replaceRolesAndPosts(untouched);
        verify(userRoleMapper, never()).deleteByUserId(101L);
        verify(userPostMapper, never()).deleteByUserId(101L);
    }

}
