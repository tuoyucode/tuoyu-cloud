package org.tuoyu.workflow.service.impl;

import cn.dev33.satoken.exception.NotPermissionException;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.workflow.service.IFlwInstanceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("按业务 ID 校验能否查看流程：供业务模块向流程参与人开放审批中的业务详情")
class WorkflowServiceImplReadAccessTest {

    private IFlwInstanceService instanceService;
    private WorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        instanceService = mock(IFlwInstanceService.class);
        service = new WorkflowServiceImpl(instanceService, null, null);
        FlowInstance instance = new FlowInstance();
        instance.setId(100L);
        when(instanceService.selectInstByBusinessId("123-2")).thenReturn(instance);
    }

    @Test
    @DisplayName("流程参与人：校验通过")
    void participantCanRead() {
        assertThatCode(() -> service.checkReadAccess("123-2")).doesNotThrowAnyException();
        verify(instanceService).checkInstanceReadAccess(100L);
    }

    @Test
    @DisplayName("与流程无关的人：返回带 403 的业务异常（跨服务时错误码原样传回）")
    void outsiderIsForbidden() {
        doThrow(new NotPermissionException("无权查看该流程")).when(instanceService).checkInstanceReadAccess(100L);

        ServiceException e = catchThrowableOfType(ServiceException.class, () -> service.checkReadAccess("123-2"));

        assertThat(e).isNotNull();
        assertThat(e.getCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("没有对应的流程实例或业务 ID 为空：返回带 404 的业务异常，不做读取校验")
    void missingInstanceIsNotFound() {
        for (String businessId : new String[]{"999", " ", null}) {
            ServiceException e = catchThrowableOfType(ServiceException.class, () -> service.checkReadAccess(businessId));
            assertThat(e).as("业务 ID：%s", businessId).isNotNull();
            assertThat(e.getCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        verify(instanceService, never()).checkInstanceReadAccess(anyLong());
    }

}
