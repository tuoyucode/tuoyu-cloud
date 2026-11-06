package org.tuoyu.system.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.mybatis.core.mapper.LambdaCrudChainWrapper;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("部门管理：修改部门时清空负责人")
class SysDeptServiceImplTest {

    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);

    @SuppressWarnings("unchecked")
    private final LambdaCrudChainWrapper<SysDept, Object> chain = mock(LambdaCrudChainWrapper.class, RETURNS_SELF);

    private final SysDeptServiceImpl service = new SysDeptServiceImpl(deptMapper, mock(SysRoleMapper.class),
        mock(SysUserMapper.class));

    @SuppressWarnings({"unchecked", "rawtypes"})
    SysDeptServiceImplTest() {
        when(deptMapper.lambda()).thenReturn((LambdaCrudChainWrapper) chain);
        // set / eq 的返回类型是泛型，RETURNS_SELF 不会生效，单独桩成返回自身
        doReturn(chain).when(chain).set(any(), any());
        doReturn(chain).when(chain).eq(any(), any());
    }

    private static SysDept dept(Long leader) {
        SysDept dept = new SysDept();
        dept.setDeptId(100L);
        dept.setLeader(leader);
        return dept;
    }

    @Test
    @DisplayName("原来有负责人、提交时未选负责人：清空负责人")
    void clearLeader() {
        service.clearLeaderIfRemoved(dept(1L), dept(null));

        verify(chain).set(any(), isNull());
        verify(chain).eq(any(), eq(100L));
        verify(chain).update();
    }

    @Test
    @DisplayName("更换负责人或原来就没有负责人：不额外更新")
    void keepOrChangeLeader() {
        service.clearLeaderIfRemoved(dept(1L), dept(2L));
        service.clearLeaderIfRemoved(dept(null), dept(null));

        verify(deptMapper, never()).lambda();
    }
}
