package org.tuoyu.workflow.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.dromara.warm.flow.orm.entity.FlowNode;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.workflow.domain.vo.FlowTaskVo;
import org.tuoyu.workflow.domain.vo.NodeExtVo;
import org.tuoyu.workflow.service.IFlwCommonService;
import org.tuoyu.workflow.service.IFlwNodeExtService;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DisplayName("查询任务：表单路径与待办、已办列表一致，任务上没有时取流程定义的")
class FlwTaskServiceImplSelectByIdTest {

    private UserContextProvider previousProvider;
    private MockedStatic<StpUtil> stp;
    private TaskService taskService;
    private FlwTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        InsService insService = mock(InsService.class);
        DefService defService = mock(DefService.class);
        IFlwCommonService flwCommonService = mock(IFlwCommonService.class);
        IFlwNodeExtService flwNodeExtService = mock(IFlwNodeExtService.class);
        service = spy(new FlwTaskServiceImpl(taskService, insService, defService, null, null, null, null, null, null,
            null, null, null, null, flwCommonService, flwNodeExtService, null));

        // 有任务管理权限的用户可以读取任何任务，这里不测试读取权限
        CurrentUser user = new CurrentUser();
        user.setUserId(7L);
        previousProvider = UserContext.setProvider(() -> Optional.of(user));
        stp = mockStatic(StpUtil.class);
        stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

        // 流程变量的解析依赖 Warm-Flow 引擎的 JSON 转换器，这里不需要变量
        FlowInstance instance = spy(new FlowInstance());
        doReturn(Map.of()).when(instance).getVariableMap();
        instance.setId(100L);
        instance.setDefinitionId(10L);
        instance.setBusinessId("123-2");
        instance.setFlowStatus("waiting");
        when(insService.getById(100L)).thenReturn(instance);

        FlowDefinition definition = new FlowDefinition();
        definition.setId(10L);
        definition.setFlowCode("mes_material_supplement");
        definition.setFlowName("补料申请");
        definition.setFormCustom("Y");
        definition.setFormPath(" mes/material-request/approval-detail ");
        when(defService.getById(10L)).thenReturn(definition);

        FlowNode node = new FlowNode();
        node.setNodeCode("approve");
        doReturn(node).when(service).getByNodeCode(any(), any());
        when(flwNodeExtService.parseNodeExt(any(), any())).thenReturn(new NodeExtVo());
        when(flwCommonService.applyNodeCode(10L)).thenReturn("start");
    }

    @AfterEach
    void tearDown() {
        UserContext.setProvider(previousProvider);
        stp.close();
    }

    private FlowTask task(String formPath) {
        FlowTask task = new FlowTask();
        task.setId(9L);
        task.setInstanceId(100L);
        task.setDefinitionId(10L);
        task.setNodeCode("approve");
        task.setFormPath(formPath);
        when(taskService.getById(9L)).thenReturn(task);
        return task;
    }

    @Test
    @DisplayName("任务上没有表单路径时取流程定义的（去掉首尾空白），业务 ID 原样返回")
    void fallsBackToDefinitionFormPath() {
        task(null);

        FlowTaskVo vo = service.selectById(9L);

        assertThat(vo.getFormPath()).isEqualTo("mes/material-request/approval-detail");
        assertThat(vo.getFormCustom()).isEqualTo("Y");
        assertThat(vo.getBusinessId()).isEqualTo("123-2");
    }

    @Test
    @DisplayName("任务上有表单路径时以任务的为准")
    void keepsTaskFormPath() {
        task("mes/defect/review");

        assertThat(service.selectById(9L).getFormPath()).isEqualTo("mes/defect/review");
    }

}
