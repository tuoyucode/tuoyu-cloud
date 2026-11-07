package org.tuoyu.system.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.support.SystemTestSupport;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("岗位管理：数据权限")
class SysPostServiceImplTest {

    static {
        // 对象转换使用真实的转换器
        SystemTestSupport.init();
    }

    private final SysPostMapper postMapper = mock(SysPostMapper.class);

    private final SysPostServiceImpl service = new SysPostServiceImpl(postMapper, mock(SysDeptMapper.class),
        mock(SysUserPostMapper.class));

    @Test
    @DisplayName("列表、导出、导出前统计与下拉选项使用的 Mapper 方法都带数据权限")
    void queriesUseDataPermission() {
        for (String name : List.of("selectPagePostList", "selectPostList", "selectPostOptions", "selectPostCount")) {
            Method method = Arrays.stream(SysPostMapper.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            assertNotNull(method.getAnnotation(DataPermission.class), name + " 缺少 @DataPermission");
        }
    }

    @Test
    @DisplayName("按岗位ID查询下拉选项时走带数据权限的查询")
    void optionsByIdsUseDataPermission() {
        SysPost post = new SysPost();
        post.setPostId(1L);
        post.setPostName("董事长");
        when(postMapper.selectPostOptions(List.of(1L, 2L))).thenReturn(List.of(post));

        List<SysPostVo> options = service.selectPostByIds(List.of(1L, 2L));
        assertEquals(1, options.size());
        assertEquals(1L, options.get(0).getPostId());
        assertEquals("董事长", options.get(0).getPostName());
        verify(postMapper).selectPostOptions(List.of(1L, 2L));
    }

}
