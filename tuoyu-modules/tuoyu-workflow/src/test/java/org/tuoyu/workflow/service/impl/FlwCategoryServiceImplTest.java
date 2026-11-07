package org.tuoyu.workflow.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.warm.flow.core.service.DefService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.workflow.common.constant.FlowConstant;
import org.tuoyu.workflow.domain.FlowCategory;
import org.tuoyu.workflow.domain.bo.FlowCategoryBo;
import org.tuoyu.workflow.mapper.FlwCategoryMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("流程分类：写操作失败时抛出带原因的业务异常")
class FlwCategoryServiceImplTest {

    private DefService defService;
    private FlwCategoryMapper mapper;
    private FlwCategoryServiceImpl service;

    @BeforeAll
    static void registerTables() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FlowCategory.class);
    }

    @BeforeEach
    void setUp() {
        defService = mock(DefService.class);
        mapper = mock(FlwCategoryMapper.class);
        service = new FlwCategoryServiceImpl(defService, mapper);
    }

    @Test
    @DisplayName("默认分类不能删除")
    void defaultCategoryCannotBeDeleted() {
        assertThatThrownBy(() -> service.deleteWithValidById(FlowConstant.FLOW_CATEGORY_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessage("默认流程分类,不允许删除");
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("有下级分类时不能删除")
    void categoryWithChildrenCannotBeDeleted() {
        when(mapper.exists(any())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteWithValidById(10L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("存在下级流程分类,不允许删除");
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("已有流程定义的分类不能删除")
    void categoryWithDefinitionCannotBeDeleted() {
        when(mapper.exists(any())).thenReturn(false);
        when(defService.exists(any())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteWithValidById(10L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("流程分类存在流程定义,不允许删除");
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("没有下级分类与流程定义时删除")
    void emptyCategoryIsDeleted() {
        when(mapper.exists(any())).thenReturn(false);
        when(defService.exists(any())).thenReturn(false);
        service.deleteWithValidById(10L);
        verify(mapper).deleteById(10L);
    }

    @Test
    @DisplayName("同一父级下名称重复时不能新增、修改")
    void duplicateNameIsRejected() {
        when(mapper.exists(any())).thenReturn(true);
        FlowCategoryBo bo = bo(10L, 1L, "请假");
        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessage("新增流程分类'请假'失败，流程分类名称已存在");
        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessage("修改流程分类'请假'失败，流程分类名称已存在");
    }

    @Test
    @DisplayName("上级分类不能是自己")
    void parentCannotBeSelf() {
        when(mapper.exists(any())).thenReturn(false);
        assertThatThrownBy(() -> service.updateByBo(bo(10L, 10L, "请假")))
            .isInstanceOf(ServiceException.class)
            .hasMessage("修改流程分类'请假'失败，上级流程分类不能是自己");
    }

    @Test
    @DisplayName("父级分类不存在时不能新增")
    void missingParentIsRejected() {
        when(mapper.exists(any())).thenReturn(false);
        assertThatThrownBy(() -> service.insertByBo(bo(null, 99L, "请假")))
            .isInstanceOf(ServiceException.class)
            .hasMessage("父级流程分类不存在!");
    }

    private static FlowCategoryBo bo(Long categoryId, Long parentId, String name) {
        FlowCategoryBo bo = new FlowCategoryBo();
        bo.setCategoryId(categoryId);
        bo.setParentId(parentId);
        bo.setCategoryName(name);
        return bo;
    }

}
