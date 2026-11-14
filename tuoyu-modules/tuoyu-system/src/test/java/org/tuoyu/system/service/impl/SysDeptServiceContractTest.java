package org.tuoyu.system.service.impl;

import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.DataScopeProbe;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 部门服务的行为契约
 */
@DisplayName("部门服务契约")
class SysDeptServiceContractTest {

    static {
        SystemTestSupport.init();
    }

    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysRoleMapper roleMapper = SystemTestSupport.mapper(SysRoleMapper.class);
    private final SysUserMapper userMapper = SystemTestSupport.mapper(SysUserMapper.class);
    private final SysDeptServiceImpl service = new SysDeptServiceImpl(deptMapper, roleMapper, userMapper);
    private final CurrentUserStub login = CurrentUserStub.install().userId(SystemConstants.SUPER_ADMIN_USER_ID);

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysDept dept(Long id, Long parentId, String ancestors, String status) {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setStatus(status);
        dept.setDeptName("部门" + id);
        dept.setOrderNum(id.intValue());
        return dept;
    }

    private static SysDeptBo bo(Long id, Long parentId, String status) {
        SysDeptBo bo = new SysDeptBo();
        bo.setDeptId(id);
        bo.setParentId(parentId);
        bo.setDeptName("部门" + id);
        bo.setStatus(status);
        return bo;
    }

    /**
     * 按条件更新（update 的任一重载）收到的条件
     */
    private List<Wrapper<?>> updates() {
        List<Wrapper<?>> found = new ArrayList<>();
        for (org.mockito.invocation.Invocation inv : org.mockito.Mockito.mockingDetails(deptMapper).getInvocations()) {
            if ("update".equals(inv.getMethod().getName())) {
                for (Object arg : inv.getArguments()) {
                    if (arg instanceof Wrapper<?> w) {
                        found.add(w);
                    }
                }
            }
        }
        return found;
    }

    private static List<String> evictedCaches(String method, Class<?>... types) throws Exception {
        java.lang.reflect.Method m = SysDeptServiceImpl.class.getMethod(method, types);
        List<String> names = new ArrayList<>();
        CacheEvict single = m.getAnnotation(CacheEvict.class);
        if (single != null) {
            names.addAll(List.of(single.cacheNames()));
        }
        Caching caching = m.getAnnotation(Caching.class);
        if (caching != null) {
            for (CacheEvict e : caching.evict()) {
                names.addAll(List.of(e.cacheNames()));
            }
        }
        return names;
    }

    @Nested
    @DisplayName("新增")
    class Insert {

        @Test
        @DisplayName("上级不存在、已停用时拒绝；祖级为上级的祖级加上级 ID")
        void insert() throws Exception {
            assertThatThrownBy(() -> service.insertDept(bo(null, 9L, "0"))).hasMessage("父部门不存在");
            when(deptMapper.selectById(8L)).thenReturn(dept(8L, 1L, "0,1", "1"));
            assertThatThrownBy(() -> service.insertDept(bo(null, 8L, "0"))).hasMessage("部门停用，不允许新增");

            when(deptMapper.selectById(7L)).thenReturn(dept(7L, 1L, "0,1", "0"));
            service.insertDept(bo(null, 7L, "0"));
            ArgumentCaptor<SysDept> saved = ArgumentCaptor.forClass(SysDept.class);
            verify(deptMapper).insert(saved.capture());
            assertEquals("0,1,7", saved.getValue().getAncestors());
            assertThat(evictedCaches("insertDept", SysDeptBo.class)).contains(CacheNames.SYS_DEPT_AND_CHILD);
        }
    }

    @Nested
    @DisplayName("修改")
    class Update {

        @Test
        @DisplayName("部门不存在、更新 0 行时报错")
        void missing() {
            assertThatThrownBy(() -> service.updateDept(bo(10L, 1L, "0"))).hasMessage("部门不存在，无法修改");
            when(deptMapper.selectById(10L)).thenReturn(dept(10L, 1L, "0,1", "0"));
            try (MockedStatic<CacheUtils> ignored = mockStatic(CacheUtils.class)) {
                assertThatThrownBy(() -> service.updateDept(bo(10L, 1L, "0"))).hasMessage("修改部门'部门10'失败，部门不存在");
            }
        }

        @Test
        @DisplayName("更换上级：改写自己与子部门的祖级，清除子部门的缓存，并启用上级部门")
        void moveDept() throws Exception {
            when(deptMapper.selectById(10L)).thenReturn(dept(10L, 1L, "0,1", "0"));
            when(deptMapper.selectById(2L)).thenReturn(dept(2L, 1L, "0,1", "0"));
            when(deptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dept(11L, 10L, "0,1,10", "0")));
            when(deptMapper.updateById(any(SysDept.class))).thenReturn(1);
            try (MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class)) {
                service.updateDept(bo(10L, 2L, "0"));
                ArgumentCaptor<SysDept> self = ArgumentCaptor.forClass(SysDept.class);
                verify(deptMapper).updateById(self.capture());
                assertEquals("0,1,2", self.getValue().getAncestors());
                ArgumentCaptor<Collection<SysDept>> children = ArgumentCaptor.captor();
                verify(deptMapper).updateById(children.capture());
                assertThat(children.getValue()).singleElement().satisfies(c -> {
                    assertEquals(11L, c.getDeptId());
                    assertEquals("0,1,2,10", c.getAncestors());
                });
                cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DEPT, 11L));
                assertThat(updates()).singleElement().satisfies(w -> assertThat(w.getSqlSet()).contains("status"));
            }
            assertThat(evictedCaches("updateDept", SysDeptBo.class)).contains(CacheNames.SYS_DEPT, CacheNames.SYS_DEPT_AND_CHILD);
        }

        @Test
        @DisplayName("启用部门时一并启用的上级部门，清除它们的单个部门缓存")
        void enabledParentsEvicted() {
            when(deptMapper.selectById(10L)).thenReturn(dept(10L, 2L, "0,1,2", "1"));
            when(deptMapper.updateById(any(SysDept.class))).thenReturn(1);
            try (MockedStatic<CacheUtils> cache = mockStatic(CacheUtils.class)) {
                service.updateDept(bo(10L, 2L, "0"));
                cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DEPT, 2L));
                cache.verify(() -> CacheUtils.evict(CacheNames.SYS_DEPT, 1L));
            }
        }

        @Test
        @DisplayName("不能把部门移到自己的下级部门之下")
        void rejectMoveUnderDescendant() {
            when(deptMapper.selectById(10L)).thenReturn(dept(10L, 1L, "0,1", "0"));
            when(deptMapper.selectById(12L)).thenReturn(dept(12L, 11L, "0,1,10,11", "0"));
            try (MockedStatic<CacheUtils> ignored = mockStatic(CacheUtils.class)) {
                assertThatThrownBy(() -> service.updateDept(bo(10L, 12L, "0")))
                    .hasMessage("修改部门'部门10'失败，上级部门不能是自己的下级部门");
            }
            verify(deptMapper, never()).updateById(any(SysDept.class));
        }

        @Test
        @DisplayName("上级不变时保留原祖级；停用时不启用上级；去掉负责人时清空")
        void keepAncestorsAndClearLeader() {
            SysDept old = dept(10L, 1L, "0,1", "0");
            old.setLeader(100L);
            when(deptMapper.selectById(10L)).thenReturn(old);
            when(deptMapper.updateById(any(SysDept.class))).thenReturn(1);
            try (MockedStatic<CacheUtils> ignored = mockStatic(CacheUtils.class)) {
                service.updateDept(bo(10L, 1L, "1"));
            }
            ArgumentCaptor<SysDept> self = ArgumentCaptor.forClass(SysDept.class);
            verify(deptMapper).updateById(self.capture());
            assertEquals("0,1", self.getValue().getAncestors());
            assertThat(updates()).singleElement()
                .satisfies(w -> assertThat(w.getSqlSet()).contains("leader").doesNotContain("status"));
        }

        @Test
        @DisplayName("更换上级时检查新上级的数据权限")
        void newParentScope() {
            login.userId(2L);
            when(deptMapper.selectById(10L)).thenReturn(dept(10L, 1L, "0,1", "0"));
            when(deptMapper.selectCount(any())).thenReturn(0L);
            assertThatThrownBy(() -> service.updateDept(bo(10L, 2L, "0"))).hasMessage("没有权限访问部门数据！");
        }
    }

    @Nested
    @DisplayName("查询与检查")
    class Queries {

        @Test
        @DisplayName("按 ID 查询带出上级名称、结果缓存；不存在时为 null")
        void byId() throws Exception {
            when(deptMapper.selectById(10L)).thenReturn(dept(10L, 1L, "0,1", "0"));
            when(deptMapper.selectOne(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(dept(1L, 0L, "0", "0"));
            SysDeptVo vo = service.selectDeptById(10L);
            assertEquals("部门1", vo.getParentName());
            assertNull(service.selectDeptById(99L));
            Cacheable cacheable = SysDeptServiceImpl.class.getMethod("selectDeptById", Long.class).getAnnotation(Cacheable.class);
            assertThat(cacheable.cacheNames()).containsExactly(CacheNames.SYS_DEPT);
            assertEquals("#deptId", cacheable.key());
        }

        @Test
        @DisplayName("多个部门名称：逗号连接，跳过不存在的部门")
        void names() {
            SystemTestSupport.bean(service);
            when(deptMapper.selectById(1L)).thenReturn(dept(1L, 0L, "0", "0"));
            when(deptMapper.selectById(3L)).thenReturn(dept(3L, 0L, "0", "0"));
            assertEquals("部门1,部门3", service.selectDeptNameByIds("1,2,3"));
        }

        @Test
        @DisplayName("列表、分页、树、按 ID 列表与数据权限检查都在数据权限下执行（只按部门列）")
        void dataScope() {
            DataScopeProbe<SysDeptMapper> probe = DataScopeProbe.of(SysDeptMapper.class).answer("selectCount", a -> 1L);
            SysDeptServiceImpl scoped = new SysDeptServiceImpl(probe.mapper(), roleMapper, userMapper);
            scoped.selectDeptList(new SysDeptBo());
            scoped.selectPageDeptList(new SysDeptBo(), new PageParam());
            scoped.selectDeptTreeList(new SysDeptBo());
            scoped.selectDeptByIds(List.of(1L));
            login.userId(2L);
            scoped.checkDeptDataScope(10L);
            assertThat(probe.calls()).isNotEmpty().allSatisfy(c -> {
                assertThat(c.columns()).containsEntry("deptName", "dept_id").doesNotContainKey("userName");
            });

            probe.answer("selectCount", a -> 0L);
            assertThatThrownBy(() -> scoped.checkDeptDataScope(10L)).hasMessage("没有权限访问部门数据！");
        }

        @Test
        @DisplayName("部门树：多根，名称在 label，停用的部门标记 disabled")
        void tree() {
            when(deptMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                dept(10L, 1L, "0,1", "0"), dept(11L, 10L, "0,1,10", "1"), dept(20L, 2L, "0,2", "0")));
            List<Tree<Long>> trees = service.selectDeptTreeList(new SysDeptBo());
            assertThat(trees).extracting(Tree::getId).containsExactlyInAnyOrder(10L, 20L);
            Tree<Long> root = trees.stream().filter(t -> t.getId() == 10L).findFirst().orElseThrow();
            assertEquals("部门10", root.get("label"));
            assertEquals(false, root.get("disabled"));
            assertEquals(true, root.getChildren().get(0).get("disabled"));
        }

        @Test
        @DisplayName("角色的部门：按联动设置回显；角色不存在时报 404")
        void roleDepts() {
            SysRole role = new SysRole();
            role.setDeptCheckStrictly(false);
            when(roleMapper.selectById(5L)).thenReturn(role);
            doReturn(List.of(10L)).when(deptMapper).selectDeptListByRoleId(5L, false);
            assertEquals(List.of(10L), service.selectDeptListByRoleId(5L));

            assertThatThrownBy(() -> service.selectDeptListByRoleId(6L))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertEquals(404, ((ServiceException) e).getCode()));
        }

        @Test
        @DisplayName("子部门、用户、名称唯一性检查；删除不存在的部门时报错")
        void checks() {
            when(deptMapper.exists(any())).thenReturn(true);
            when(deptMapper.selectCount(any())).thenReturn(2L);
            when(userMapper.exists(any())).thenReturn(true);
            when(userMapper.selectCount(any())).thenReturn(1L);
            assertThat(service.hasChildByDeptId(1L)).isTrue();
            assertThat(service.checkDeptExistUser(1L)).isTrue();
            assertThat(service.checkDeptNameUnique(bo(1L, 0L, "0"))).isFalse();
            assertEquals(2L, service.selectNormalChildrenDeptById(1L));
            when(deptMapper.deleteById(1L)).thenReturn(0);
            assertThatThrownBy(() -> service.deleteDeptById(1L)).hasMessage("删除部门失败，部门不存在");
        }

        @Test
        @DisplayName("按所属部门搜索时包含其下级部门")
        void belongDept() {
            doReturn(new ArrayList<>(List.of(10L, 11L))).when(deptMapper).selectDeptAndChildById(10L);
            SysDeptBo query = new SysDeptBo();
            query.setBelongDeptId(10L);
            service.selectDeptList(query);
            ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
            verify(deptMapper).selectList(wrapper.capture());
            assertThat(wrapper.getValue().getSqlSegment()).contains("dept_id IN");
            verify(deptMapper, never()).insert(anyCollection());
        }
    }
}
