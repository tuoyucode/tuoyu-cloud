package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.support.SystemTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("部门管理：修改部门时清空负责人")
class SysDeptServiceImplTest {

    static {
        SystemTestSupport.init();
    }

    private final SysDeptMapper deptMapper = mock(SysDeptMapper.class);

    private final SysDeptServiceImpl service = new SysDeptServiceImpl(deptMapper, mock(SysRoleMapper.class),
        mock(SysUserMapper.class));

    private static SysDept dept(Long leader) {
        SysDept dept = new SysDept();
        dept.setDeptId(100L);
        dept.setLeader(leader);
        return dept;
    }

    @Test
    @DisplayName("原来有负责人、提交时未选负责人：按部门 ID 把负责人更新为空")
    @SuppressWarnings("unchecked")
    void clearLeader() {
        service.clearLeaderIfRemoved(dept(1L), dept(null));

        ArgumentCaptor<Wrapper<SysDept>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(deptMapper).update(captor.capture());
        LambdaUpdateWrapper<SysDept> wrapper = (LambdaUpdateWrapper<SysDept>) captor.getValue();
        assertThat(wrapper.getSqlSet()).contains("leader");
        assertThat(wrapper.getSqlSegment()).contains("dept_id");
        assertThat(wrapper.getParamNameValuePairs()).containsValue(100L);
    }

    @Test
    @DisplayName("更换负责人或原来就没有负责人：不额外更新")
    void keepOrChangeLeader() {
        service.clearLeaderIfRemoved(dept(1L), dept(2L));
        service.clearLeaderIfRemoved(dept(null), dept(null));

        verify(deptMapper, never()).update(any(Wrapper.class));
    }
}
