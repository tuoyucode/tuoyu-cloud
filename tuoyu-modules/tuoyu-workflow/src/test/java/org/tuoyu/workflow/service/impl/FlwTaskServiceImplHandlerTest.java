package org.tuoyu.workflow.service.impl;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.core.service.UserService;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.dromara.warm.flow.orm.entity.FlowUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("任务办理人：对外查询校验读取权限，流转后内部通知不校验")
class FlwTaskServiceImplHandlerTest {

    private MockedStatic<LoginHelper> login;
    private MockedStatic<StpUtil> stp;
    private MockedStatic<FlowEngine> engine;

    private RemoteUserService remoteUserService;
    private FlwTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        TaskService taskService = mock(TaskService.class);
        InsService insService = mock(InsService.class);
        UserService userService = mock(UserService.class);
        remoteUserService = mock(RemoteUserService.class);
        service = new FlwTaskServiceImpl(taskService, insService, null, null, null, null, null, null, null, null,
            null, null, null, null, null, remoteUserService);

        login = mockStatic(LoginHelper.class);
        stp = mockStatic(StpUtil.class);
        engine = mockStatic(FlowEngine.class);
        engine.when(FlowEngine::userService).thenReturn(userService);
        // 当前用户 7：既不是下一节点的办理人，也不是发起人，没有任务管理权限
        login.when(LoginHelper::getUserIdStr).thenReturn("7");
        login.when(LoginHelper::isSuperAdmin).thenReturn(false);
        stp.when(() -> StpUtil.hasPermission(anyString())).thenReturn(false);

        FlowTask next = new FlowTask();
        next.setId(9L);
        next.setInstanceId(100L);
        when(taskService.getById(9L)).thenReturn(next);
        FlowInstance instance = new FlowInstance();
        instance.setId(100L);
        instance.setCreateBy("3");
        when(insService.getById(100L)).thenReturn(instance);
        FlowUser handler = new FlowUser();
        handler.setProcessedBy("1");
        handler.setType("1");
        handler.setAssociated(9L);
        when(userService.getByAssociateds(anyList())).thenReturn(List.<User>of(handler));
        RemoteUserVo admin = new RemoteUserVo();
        admin.setUserId(1L);
        when(remoteUserService.selectListByIds(Set.of(1L))).thenReturn(List.of(admin));
    }

    @AfterEach
    void tearDown() {
        login.close();
        stp.close();
        engine.close();
    }

    @Test
    @DisplayName("对外查询（接口、催办）：与任务无关的用户不能查看办理人")
    void currentTaskAllUserChecksReadAccess() {
        assertThatThrownBy(() -> service.currentTaskAllUser(List.of(9L))).isInstanceOf(NotPermissionException.class);
    }

    @Test
    @DisplayName("流转后通知下一节点：审批人不是下一节点的办理人也能取到办理人，不能因此让审批失败")
    void selectHandlersByTaskIdsSkipsReadAccess() {
        List<RemoteUserVo> users = service.selectHandlersByTaskIds(List.of(9L));

        assertThat(users).extracting(RemoteUserVo::getUserId).containsExactly(1L);
        verify(remoteUserService).selectListByIds(Set.of(1L));
    }

    @Test
    @DisplayName("内部查询：没有任务或任务 ID 为空时返回空列表")
    void selectHandlersByTaskIdsHandlesEmpty() {
        assertThat(service.selectHandlersByTaskIds(List.of())).isEmpty();
        assertThat(service.selectHandlersByTaskIds(null)).isEmpty();
        assertThat(service.selectHandlersByTaskIds(Arrays.asList((Long) null))).isEmpty();
    }

}
