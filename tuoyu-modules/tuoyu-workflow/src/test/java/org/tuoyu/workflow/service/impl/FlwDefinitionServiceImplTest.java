package org.tuoyu.workflow.service.impl;

import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("流程定义：流程引擎没有更新时抛出带原因的业务异常")
class FlwDefinitionServiceImplTest {

    private DefService defService;
    private FlwDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        defService = mock(DefService.class);
        service = new FlwDefinitionServiceImpl(defService, null, null, null, null, null);
    }

    @Test
    @DisplayName("激活、挂起：更新成功时不抛异常，没有更新时说明是哪种操作失败")
    void activeReportsFailure() {
        when(defService.active(1L)).thenReturn(true);
        when(defService.unActive(1L)).thenReturn(false);

        assertThatCode(() -> service.active(1L, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.active(1L, false))
            .isInstanceOf(ServiceException.class)
            .hasMessage("挂起流程定义失败，流程定义未更新");
    }

    @Test
    @DisplayName("取消发布、复制：流程引擎返回失败时拒绝")
    void unPublishAndCopyReportFailure() {
        when(defService.unPublish(1L)).thenReturn(false);
        when(defService.copyDef(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.unPublish(1L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("取消发布流程定义失败，流程定义未更新");
        assertThatThrownBy(() -> service.copyDef(1L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("复制流程定义失败");
    }

    @Test
    @DisplayName("修改：流程定义不存在时拒绝")
    void updateMissingDefinition() {
        when(defService.updateById(any())).thenReturn(false);

        assertThatThrownBy(() -> service.updateDefinition(new FlowDefinition()))
            .isInstanceOf(ServiceException.class)
            .hasMessage("流程定义不存在，无法修改");
    }

}
