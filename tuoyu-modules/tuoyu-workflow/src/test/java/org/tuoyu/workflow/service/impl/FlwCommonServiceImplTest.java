package org.tuoyu.workflow.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;
import org.tuoyu.workflow.service.IFlwTaskService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("流程消息：流转后通知当前办理人")
class FlwCommonServiceImplTest {

    @Test
    @DisplayName("按流程实例通知时用内部查询取办理人（不校验当前用户的读取权限，否则审批人会因无权读取下一节点而审批失败）")
    void notifiesHandlersWithoutReadCheck() {
        IFlwTaskService taskService = mock(IFlwTaskService.class);
        FlowTask task = new FlowTask();
        task.setId(9L);
        when(taskService.selectByInstId(100L)).thenReturn(List.of(task));
        RemoteUserVo handler = new RemoteUserVo();
        handler.setUserId(1L);
        when(taskService.selectHandlersByTaskIds(List.of(9L))).thenReturn(List.of(handler));

        FlwCommonServiceImpl service = spy(new FlwCommonServiceImpl(null, null, null));
        doNothing().when(service).sendMessage(anyList(), anyString(), anyString(), anyList(), any());

        // SpringUtils.getBean 继承自 hutool 的 SpringUtil，静态方法要在声明它的类上模拟
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(IFlwTaskService.class)).thenReturn(taskService);
            service.sendMessage("请假", 100L, List.of("1"), null);
        }

        verify(taskService, never()).currentTaskAllUser(anyList());
        verify(service).sendMessage(eq(List.of("1")), anyString(), anyString(), eq(List.of(handler)), any());
    }

}
