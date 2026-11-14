package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.bo.SysPostBo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.support.CurrentUserStub;
import org.tuoyu.system.support.DataScopeProbe;
import org.tuoyu.system.support.SystemTestSupport;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 岗位服务的行为契约
 */
@DisplayName("岗位服务契约")
class SysPostServiceContractTest {

    static {
        SystemTestSupport.init();
    }

    private final SysPostMapper postMapper = SystemTestSupport.mapper(SysPostMapper.class);
    private final SysDeptMapper deptMapper = SystemTestSupport.mapper(SysDeptMapper.class);
    private final SysUserPostMapper userPostMapper = SystemTestSupport.mapper(SysUserPostMapper.class);
    private final SysPostServiceImpl service = new SysPostServiceImpl(postMapper, deptMapper, userPostMapper);
    private final CurrentUserStub login = CurrentUserStub.install().userId(SystemConstants.SUPER_ADMIN_USER_ID);

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysPost post(Long id, String name) {
        SysPost post = new SysPost();
        post.setPostId(id);
        post.setPostName(name);
        return post;
    }

    @Test
    @DisplayName("列表、分页、统计、下拉选项、数量检查都在数据权限下执行（部门列、创建人列）")
    void listsUnderDataScope() {
        DataScopeProbe<SysPostMapper> probe = DataScopeProbe.of(SysPostMapper.class);
        SysPostServiceImpl scoped = new SysPostServiceImpl(probe.mapper(), deptMapper, userPostMapper);
        scoped.selectPostList(new SysPostBo());
        scoped.selectPagePostList(new SysPostBo(), new PageParam());
        scoped.countPostList(new SysPostBo());
        scoped.selectPostByIds(List.of(1L));
        probe.mapper().selectPostCount(List.of(1L));
        assertThat(probe.calls()).hasSizeGreaterThanOrEqualTo(5).allSatisfy(c -> assertThat(c.columns())
            .containsEntry("deptName", "dept_id").containsEntry("userName", "create_by"));
    }

    @Test
    @DisplayName("按 ID 查询岗位详情受数据权限限制")
    void detailUnderDataScope() {
        DataScopeProbe<SysPostMapper> probe = DataScopeProbe.of(SysPostMapper.class);
        SysPostServiceImpl scoped = new SysPostServiceImpl(probe.mapper(), deptMapper, userPostMapper);
        scoped.selectPostById(1L);
        assertThat(probe.calls()).isNotEmpty().allSatisfy(c -> assertThat(c.permission()).isNotNull());
    }

    @Test
    @DisplayName("修改、删除数据权限范围外的岗位时拒绝")
    void writesCheckScope() {
        login.userId(2L);
        when(postMapper.selectCount(any())).thenReturn(0L);
        when(postMapper.updateById(any(SysPost.class))).thenReturn(1);
        when(postMapper.deleteByIds(anyList())).thenReturn(1);
        SysPostBo bo = new SysPostBo();
        bo.setPostId(1L);
        bo.setPostName("p");
        assertThatThrownBy(() -> service.updatePost(bo)).hasMessage("没有权限访问岗位数据！");
        assertThatThrownBy(() -> service.deletePostByIds(List.of(1L))).hasMessage("没有权限访问岗位数据！");
        verify(postMapper, never()).updateById(any(SysPost.class));
    }

    @Test
    @DisplayName("删除：已分配给用户的岗位不能删；全部不存在时报错")
    void delete() {
        when(postMapper.selectCount(any())).thenReturn(1L);
        when(postMapper.selectByIds(anyList())).thenReturn(List.of(post(1L, "经理")));
        when(userPostMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> service.deletePostByIds(List.of(1L))).hasMessage("经理已分配，不能删除!");

        when(userPostMapper.selectCount(any())).thenReturn(0L);
        when(postMapper.deleteByIds(anyList())).thenReturn(0);
        assertThatThrownBy(() -> service.deletePostByIds(List.of(1L))).hasMessage("删除岗位失败，岗位不存在");
    }

    @Test
    @DisplayName("修改：不存在时报错；名称在部门内唯一，编码在租户内唯一")
    void updateAndUnique() {
        when(postMapper.selectCount(any())).thenReturn(1L);
        when(postMapper.updateById(any(SysPost.class))).thenReturn(0);
        SysPostBo bo = new SysPostBo();
        bo.setPostId(1L);
        bo.setDeptId(10L);
        bo.setPostName("经理");
        bo.setPostCode("pm");
        assertThatThrownBy(() -> service.updatePost(bo)).hasMessage("修改岗位'经理'失败，岗位不存在");

        when(postMapper.exists(any())).thenReturn(false);
        assertThat(service.checkPostNameUnique(bo)).isTrue();
        assertThat(service.checkPostCodeUnique(bo)).isTrue();
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper, org.mockito.Mockito.times(2)).exists(wrapper.capture());
        assertThat(wrapper.getAllValues().get(0).getSqlSegment()).contains("post_name").contains("dept_id").contains("post_id");
        assertThat(wrapper.getAllValues().get(1).getSqlSegment()).contains("post_code").doesNotContain("dept_id");
    }

    @Test
    @DisplayName("按部门查询：指定部门时只查该部门，按所属部门时包含下级部门")
    void deptFilter() {
        SysPostBo byDept = new SysPostBo();
        byDept.setDeptId(10L);
        service.selectPostList(byDept);
        doReturn(new ArrayList<>(List.of(10L, 11L))).when(deptMapper).selectDeptAndChildById(10L);
        SysPostBo byTree = new SysPostBo();
        byTree.setBelongDeptId(10L);
        service.selectPostList(byTree);
        ArgumentCaptor<Wrapper> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(postMapper, org.mockito.Mockito.times(2)).selectList(wrapper.capture());
        assertThat(wrapper.getAllValues().get(0).getSqlSegment()).contains("dept_id =").doesNotContain("dept_id IN");
        assertThat(wrapper.getAllValues().get(1).getSqlSegment()).contains("dept_id IN");
    }

    @Test
    @DisplayName("用户的岗位、岗位使用数、部门下岗位数")
    void counts() {
        SysPostVo vo = new SysPostVo();
        vo.setPostId(3L);
        doReturn(List.of(vo)).when(postMapper).selectPostsByUserId(100L);
        assertEquals(List.of(3L), service.selectPostListByUserId(100L));
        assertThat(service.selectPostsByUserId(100L)).hasSize(1);
        when(userPostMapper.selectCount(any())).thenReturn(4L);
        assertEquals(4L, service.countUserPostById(3L));
        when(postMapper.selectCount(any())).thenReturn(2L);
        assertEquals(2L, service.countPostByDeptId(10L));
    }
}
