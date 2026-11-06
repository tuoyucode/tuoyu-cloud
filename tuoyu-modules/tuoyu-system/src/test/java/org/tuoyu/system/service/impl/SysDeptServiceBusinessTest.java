package org.tuoyu.system.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("部门管理：业务规则")
class SysDeptServiceBusinessTest {

    static {
        SystemTestSupport.init();
    }

    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysDeptServiceImpl service = new SysDeptServiceImpl(deptMapper, roleMapper, userMapper);

    private final MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class, CALLS_REAL_METHODS);
    private final MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class);

    @AfterEach
    void tearDown() {
        login.close();
        cache.close();
    }

    private static SysDept dept(Long id, Long parentId, String ancestors, String status) {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDeptName("部门" + id);
        dept.setStatus(status);
        dept.setOrderNum(id.intValue());
        return dept;
    }

    @Test
    @DisplayName("列表与树：按所属部门搜索时包含子部门；停用部门在树中标为不可选")
    void listAndTree() {
        doReturn(List.of(dept(11L, 10L, "0,10", "0"), dept(12L, 10L, "0,10", "1")))
            .when(deptMapper).selectList(any(Wrapper.class));
        SysDeptBo bo = new SysDeptBo();
        bo.setDeptName("研发");
        bo.setBelongDeptId(10L);

        List<SysDeptVo> list = service.selectDeptList(bo);
        assertEquals(2, list.size());

        List<Tree<Long>> tree = service.selectDeptTreeList(bo);
        assertEquals(2, tree.size());
        assertEquals(Boolean.TRUE, tree.get(1).get("disabled"));
        assertEquals(Boolean.FALSE, tree.get(0).get("disabled"));
        assertTrue(service.buildDeptTreeSelect(List.of()).isEmpty());

        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(deptMapper, times(4)).selectList(wrapper.capture());
        // 查询子部门（FIND_IN_SET）与主查询
        assertThat(wrapper.getAllValues()).anySatisfy(w -> assertThat(w.getSqlSegment()).contains("dept_name LIKE").contains("dept_id IN"));
    }

    @Test
    @DisplayName("分页列表")
    void page() {
        when(deptMapper.selectList(any(IPage.class), any())).thenReturn(List.of(dept(11L, 10L, "0,10", "0")));
        assertEquals(1, service.selectPageDeptList(new SysDeptBo(), new PageQuery()).getRows().size());
    }

    @Test
    @DisplayName("按 ID 查询时带出上级部门名称；不存在时返回 null")
    void byId() {
        when(deptMapper.selectById(11L)).thenReturn(dept(11L, 10L, "0,10", "0"));
        when(deptMapper.selectOne(any(), anyBoolean())).thenReturn(dept(10L, 0L, "0", "0"));

        SysDeptVo vo = service.selectDeptById(11L);
        assertEquals("部门10", vo.getParentName());
        assertNull(service.selectDeptById(99L));
    }

    @Test
    @DisplayName("多个部门名称拼接时跳过不存在的部门")
    void names() {
        SystemTestSupport.bean(service);
        when(deptMapper.selectById(11L)).thenReturn(dept(11L, 10L, "0,10", "0"));
        assertEquals("部门11", service.selectDeptNameByIds("11,99"));
    }

    @Test
    @DisplayName("角色的部门：严格联动时去掉父部门")
    void deptsOfRole() {
        SysRole role = new SysRole();
        role.setRoleId(9L);
        role.setDeptCheckStrictly(true);
        when(roleMapper.selectById(9L)).thenReturn(role);
        doReturn(List.of(dept(10L, 0L, "0", "0"), dept(11L, 10L, "0,10", "0")))
            .when(deptMapper).selectJoinList(eq(SysDept.class), any());

        assertEquals(List.of(11L), service.selectDeptListByRoleId(9L));
        role.setDeptCheckStrictly(false);
        assertEquals(List.of(10L, 11L), service.selectDeptListByRoleId(9L));
    }

    @Test
    @DisplayName("子部门、用户、名称唯一性检查")
    void checks() {
        when(deptMapper.exists(any())).thenReturn(true);
        assertTrue(service.hasChildByDeptId(10L));
        SysDeptBo bo = new SysDeptBo();
        bo.setDeptName("研发");
        bo.setParentId(10L);
        assertFalse(service.checkDeptNameUnique(bo));
        when(userMapper.exists(any())).thenReturn(false);
        assertFalse(service.checkDeptExistUser(10L));
        when(deptMapper.selectCount(any())).thenReturn(2L);
        assertEquals(2L, service.selectNormalChildrenDeptById(10L));
        when(deptMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertTrue(service.selectDeptByIds(List.of(10L)).isEmpty());
    }

    @Test
    @DisplayName("部门数据权限：超级管理员放行，其他人看不到时拒绝")
    void dataScope() {
        service.checkDeptDataScope(null);
        login.when(LoginHelper::getUserId).thenReturn(SystemConstants.SUPER_ADMIN_USER_ID);
        service.checkDeptDataScope(10L);
        verify(deptMapper, never()).selectCount(any());

        login.when(LoginHelper::getUserId).thenReturn(100L);
        when(deptMapper.selectCount(any())).thenReturn(0L);
        assertThrows(ServiceException.class, () -> service.checkDeptDataScope(10L));
        when(deptMapper.selectCount(any())).thenReturn(1L);
        service.checkDeptDataScope(10L);
    }

    @Test
    @DisplayName("新增部门：上级不存在或已停用时拒绝；祖级为上级祖级加上级 ID")
    void insert() {
        SysDeptBo bo = new SysDeptBo();
        bo.setParentId(10L);
        bo.setDeptName("新部门");
        assertThrows(ServiceException.class, () -> service.insertDept(bo));

        when(deptMapper.selectById(10L)).thenReturn(dept(10L, 0L, "0", "1"));
        assertThrows(ServiceException.class, () -> service.insertDept(bo));

        when(deptMapper.selectById(10L)).thenReturn(dept(10L, 0L, "0", "0"));
        when(deptMapper.insert(any(SysDept.class))).thenReturn(1);
        assertEquals(1, service.insertDept(bo));
        ArgumentCaptor<SysDept> saved = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).insert(saved.capture());
        assertEquals("0,10", saved.getValue().getAncestors());
    }

    @Test
    @DisplayName("修改部门：部门不存在时拒绝")
    void updateMissing() {
        SysDeptBo bo = new SysDeptBo();
        bo.setDeptId(11L);
        bo.setParentId(10L);
        assertThrows(ServiceException.class, () -> service.updateDept(bo));
    }

    @Test
    @DisplayName("修改部门：更换上级时改写自己与子部门的祖级，并启用上级部门")
    void updateMovesChildren() {
        login.when(LoginHelper::getUserId).thenReturn(SystemConstants.SUPER_ADMIN_USER_ID);
        when(deptMapper.selectById(11L)).thenReturn(dept(11L, 10L, "0,10", "0"));
        when(deptMapper.selectById(20L)).thenReturn(dept(20L, 0L, "0", "0"));
        when(deptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dept(12L, 11L, "0,10,11", "0")));
        when(deptMapper.updateBatchById(any())).thenReturn(true);
        when(deptMapper.updateById(any(SysDept.class))).thenReturn(1);
        SysDeptBo bo = new SysDeptBo();
        bo.setDeptId(11L);
        bo.setParentId(20L);
        bo.setDeptName("部门11");
        bo.setStatus("0");

        assertEquals(1, service.updateDept(bo));

        ArgumentCaptor<SysDept> updated = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).updateById(updated.capture());
        assertEquals("0,20", updated.getValue().getAncestors());
        ArgumentCaptor<Collection<SysDept>> children = ArgumentCaptor.captor();
        verify(deptMapper).updateBatchById(children.capture());
        assertThat(children.getValue()).extracting(SysDept::getAncestors).containsExactly("0,20,11");
        cache.verify(() -> CacheUtils.evict(any(), eq(12L)));
        // 启用上级部门
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(deptMapper).update(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSet()).contains("status");
    }

    @Test
    @DisplayName("修改部门：上级不变时保留原祖级；停用时不启用上级")
    void updateKeepsAncestors() {
        when(deptMapper.selectById(11L)).thenReturn(dept(11L, 10L, "0,10", "0"));
        when(deptMapper.updateById(any(SysDept.class))).thenReturn(1);
        SysDeptBo bo = new SysDeptBo();
        bo.setDeptId(11L);
        bo.setParentId(10L);
        bo.setStatus("1");

        service.updateDept(bo);

        ArgumentCaptor<SysDept> updated = ArgumentCaptor.forClass(SysDept.class);
        verify(deptMapper).updateById(updated.capture());
        assertEquals("0,10", updated.getValue().getAncestors());
        verify(deptMapper, never()).update(any(Wrapper.class));
        when(deptMapper.deleteById(11L)).thenReturn(1);
        assertEquals(1, service.deleteDeptById(11L));
    }
}
