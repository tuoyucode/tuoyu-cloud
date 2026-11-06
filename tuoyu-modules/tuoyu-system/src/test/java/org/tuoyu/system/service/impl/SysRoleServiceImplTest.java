package org.tuoyu.system.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.mapper.SysRoleDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysRoleMenuMapper;
import org.tuoyu.system.mapper.SysUserRoleMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("角色管理：修改权限")
class SysRoleServiceImplTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
    private final SysRoleDeptMapper roleDeptMapper = mock(SysRoleDeptMapper.class);

    private final SysRoleServiceImpl service = new SysRoleServiceImpl(roleMapper, roleMenuMapper,
        mock(SysUserRoleMapper.class), roleDeptMapper, mock(TenantMenuScope.class));

    @Test
    @DisplayName("缺少菜单列表时拒绝，原有权限不动")
    void rejectMissingMenuIds() {
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleId(100L);
        bo.setDeptIds(new Long[0]);

        assertThrows(ServiceException.class, () -> service.updateRolePermission(bo));
        verifyNoInteractions(roleMapper, roleMenuMapper, roleDeptMapper);
    }

    @Test
    @DisplayName("缺少部门列表时拒绝，原有权限不动")
    void rejectMissingDeptIds() {
        SysRoleBo bo = new SysRoleBo();
        bo.setRoleId(100L);
        bo.setMenuIds(new Long[]{1L});

        assertThrows(ServiceException.class, () -> service.updateRolePermission(bo));
        verifyNoInteractions(roleMapper, roleMenuMapper, roleDeptMapper);
    }
}
