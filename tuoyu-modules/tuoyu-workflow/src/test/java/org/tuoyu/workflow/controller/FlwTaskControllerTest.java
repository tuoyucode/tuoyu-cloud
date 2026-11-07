package org.tuoyu.workflow.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.workflow.domain.bo.CompleteTaskBo;
import org.tuoyu.workflow.service.IFlwTaskService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("办理任务接口")
class FlwTaskControllerTest {

    @Test
    @DisplayName("请求中的 ignore 等内部变量被去掉，不能借此跳过办理人、委派、会签校验；其他变量原样保留")
    void shouldDropInternalVariables() {
        IFlwTaskService service = mock(IFlwTaskService.class);
        CompleteTaskBo bo = new CompleteTaskBo();
        Map<String, Object> variables = new HashMap<>();
        variables.put("ignore", true);
        variables.put("ignoreDepute", true);
        variables.put("ignoreCooperate", "true");
        variables.put("amount", 100);
        bo.setVariables(variables);

        new FlwTaskController(service).completeTask(bo);

        ArgumentCaptor<CompleteTaskBo> captor = ArgumentCaptor.forClass(CompleteTaskBo.class);
        verify(service).completeTask(captor.capture());
        assertEquals(Map.of("amount", 100), captor.getValue().getVariables());
    }

}
