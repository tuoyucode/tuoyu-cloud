package org.tuoyu.system.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.DataScopeProbe;
import org.tuoyu.system.support.ServiceFactory;
import org.tuoyu.system.support.SystemTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S1 补充规则 1：新增部门时上级部门必须在操作者的数据权限内（规格 S1b 第 2 节）
 */
@DisplayName("S1 补充：新增部门的上级部门数据权限")
class SysDeptServiceS1bContractTest {

    static {
        SystemTestSupport.init();
    }

    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysDeptServiceImpl service = ServiceFactory.create(SysDeptServiceImpl.class, deptMapper, roleMapper, userMapper);
    private final CurrentUserStub login = CurrentUserStub.install();

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysDept dept(Long id) {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(1L);
        dept.setAncestors("0,1");
        dept.setStatus(SystemConstants.NORMAL);
        dept.setDeptName("部门" + id);
        return dept;
    }

    private static SysDeptBo bo(Long parentId) {
        SysDeptBo bo = new SysDeptBo();
        bo.setParentId(parentId);
        bo.setDeptName("新部门");
        bo.setOrderNum(1);
        return bo;
    }

    @Test
    @DisplayName("上级部门不在数据权限内：拒绝，不写入（不论上级是否存在，都不透露）")
    void parentOutOfScope() {
        // 现有实现不满足：新增部门只检查上级存在且正常，不检查数据权限，可以在范围外的部门下建部门
        login.userId(2L);
        when(deptMapper.selectById(7L)).thenReturn(dept(7L));
        when(deptMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.insertDept(bo(7L))).hasMessage("没有权限访问部门数据！");
        assertThatThrownBy(() -> service.insertDept(bo(99L))).hasMessage("没有权限访问部门数据！");
        verify(deptMapper, never()).insert(any(SysDept.class));
    }

    @Test
    @DisplayName("在范围内时照常新增；超级管理员不检查")
    void parentInScope() {
        DataScopeProbe<SysDeptMapper> probe = DataScopeProbe.of(SysDeptMapper.class)
            .answer("selectCount", a -> 1L)
            .answer("selectById", a -> dept(7L))
            .answer("insert", a -> 1);
        SysDeptServiceImpl scoped = ServiceFactory.create(SysDeptServiceImpl.class, probe.mapper(), roleMapper, userMapper);
        login.userId(2L);
        scoped.insertDept(bo(7L));
        assertThat(probe.calls("insert")).hasSize(1);
        probe.calls().clear();
        login.userId(SystemConstants.SUPER_ADMIN_USER_ID);
        scoped.insertDept(bo(7L));
        assertThat(probe.calls("selectCount")).isEmpty();
        assertThat(probe.calls("insert")).hasSize(1);
    }

    @Test
    @DisplayName("上级部门的检查经带数据权限的部门计数")
    void parentCheckUnderDataScope() {
        // 现有实现不满足：新增部门时没有数据权限检查

        DataScopeProbe<SysDeptMapper> probe = DataScopeProbe.of(SysDeptMapper.class)
            .answer("selectCount", a -> 1L)
            .answer("selectById", a -> dept(7L))
            .answer("insert", a -> 1);
        SysDeptServiceImpl scoped = ServiceFactory.create(SysDeptServiceImpl.class, probe.mapper(), roleMapper, userMapper);
        login.userId(2L);
        scoped.insertDept(bo(7L));
        assertThat(probe.calls("selectCount")).singleElement().satisfies(c -> assertThat(c.permission()).isNotNull());
        assertThat(probe.calls("insert")).hasSize(1);

        probe.calls().clear();
        login.userId(SystemConstants.SUPER_ADMIN_USER_ID);
        scoped.insertDept(bo(7L));
        assertThat(probe.calls("selectCount")).isEmpty();
    }
}
