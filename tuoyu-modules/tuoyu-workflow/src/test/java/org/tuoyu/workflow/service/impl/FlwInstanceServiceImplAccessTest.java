package org.tuoyu.workflow.service.impl;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.service.UserService;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowInstance;
import org.dromara.warm.flow.orm.entity.FlowTask;
import org.dromara.warm.flow.orm.entity.FlowUser;
import org.dromara.warm.flow.orm.mapper.FlowHisTaskMapper;
import org.dromara.warm.flow.orm.mapper.FlowInstanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.workflow.domain.bo.FlowVariableBo;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.workflow.service.IFlwTaskService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("流程实例：读取范围与流程变量的修改")
class FlwInstanceServiceImplAccessTest {

    private UserContextProvider previousProvider;
    private CurrentUser currentUser;
    private MockedStatic<StpUtil> stp;
    private MockedStatic<FlowEngine> engine;

    private FlowHisTaskMapper hisTaskMapper;
    private FlowInstanceMapper instanceMapper;
    private IFlwTaskService taskService;
    private UserService userService;
    private FlwInstanceServiceImpl service;

    @BeforeAll
    static void registerTables() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, FlowHisTask.class);
    }

    @BeforeEach
    void setUp() {
        hisTaskMapper = mock(FlowHisTaskMapper.class);
        instanceMapper = mock(FlowInstanceMapper.class);
        taskService = mock(IFlwTaskService.class);
        userService = mock(UserService.class);
        service = new FlwInstanceServiceImpl(null, null, null, hisTaskMapper, instanceMapper, taskService, null, null);

        currentUser = user(7L);
        previousProvider = UserContext.setProvider(() -> Optional.ofNullable(currentUser));
        stp = mockStatic(StpUtil.class);
        engine = mockStatic(FlowEngine.class);
        engine.when(FlowEngine::userService).thenReturn(userService);
        stp.when(() -> StpUtil.hasPermissionOr(any(String[].class))).thenReturn(false);

        FlowInstance instance = new FlowInstance();
        instance.setId(100L);
        instance.setCreateBy("1");
        when(instanceMapper.selectById(100L)).thenReturn(instance);
        when(hisTaskMapper.selectList(any())).thenReturn(List.of());
        when(taskService.selectByInstId(100L)).thenReturn(List.of());
        when(userService.getByAssociateds(anyList())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        UserContext.setProvider(previousProvider);
        stp.close();
        engine.close();
    }

    @Test
    @DisplayName("与流程无关的普通用户不能查看")
    void outsiderShouldBeRejected() {
        assertThrows(NotPermissionException.class, () -> service.checkInstanceReadAccess(100L));
    }

    @Test
    @DisplayName("发起人可以查看")
    void initiatorCanRead() {
        currentUser = user(1L);
        assertDoesNotThrow(() -> service.checkInstanceReadAccess(100L));
    }

    @Test
    @DisplayName("办理过任务的人可以查看")
    void handlerCanRead() {
        FlowHisTask his = new FlowHisTask();
        his.setTaskId(5L);
        his.setApprover("7");
        when(hisTaskMapper.selectList(any())).thenReturn(List.of(his));
        assertDoesNotThrow(() -> service.checkInstanceReadAccess(100L));
    }

    @Test
    @DisplayName("当前任务的办理人、抄送人可以查看")
    void assigneeOrCopyCanRead() {
        FlowTask task = new FlowTask();
        task.setId(9L);
        when(taskService.selectByInstId(100L)).thenReturn(List.of(task));
        FlowUser copy = new FlowUser();
        copy.setProcessedBy("7");
        copy.setType("4");
        copy.setAssociated(9L);
        when(userService.getByAssociateds(anyList())).thenReturn(List.<User>of(copy));
        assertDoesNotThrow(() -> service.checkInstanceReadAccess(100L));
    }

    @Test
    @DisplayName("超级管理员、有流程实例或任务管理权限的人可以查看")
    void monitorCanRead() {
        stp.when(() -> StpUtil.hasPermissionOr(any(String[].class))).thenReturn(true);
        assertDoesNotThrow(() -> service.checkInstanceReadAccess(100L));
    }

    @Test
    @DisplayName("没有登录用户时拒绝（401），不能因办理人、发起人为空而放行")
    void anonymousShouldBeRejected() {
        currentUser = null;
        ServiceException e = assertThrows(ServiceException.class, () -> service.checkInstanceReadAccess(100L));
        assertEquals(Integer.valueOf(401), e.getCode());
    }

    @Test
    @DisplayName("修改流程变量：变量不存在时拒绝并说明原因，不写入")
    void updateUnknownVariableShouldBeRejected() {
        FlowInstance instance = mock(FlowInstance.class);
        when(instance.getVariableMap()).thenReturn(Map.of("days", 3));
        when(instanceMapper.selectById(200L)).thenReturn(instance);
        FlowVariableBo bo = new FlowVariableBo();
        bo.setInstanceId(200L);
        bo.setKey("amount");
        bo.setValue("1");

        ServiceException e = assertThrows(ServiceException.class, () -> service.updateVariable(bo));
        assertEquals("流程变量 amount 不存在，只能修改已有的变量", e.getMessage());
        verify(instanceMapper, never()).updateById(any(FlowInstance.class));
    }

    @Test
    @DisplayName("修改流程变量时按原来的类型转换，无法转换时拒绝")
    void shouldKeepVariableType() {
        assertEquals(1200, FlwInstanceServiceImpl.convertVariableValue("days", 3, " 1200 "));
        assertEquals(5L, FlwInstanceServiceImpl.convertVariableValue("count", 2L, "5"));
        assertEquals(new BigDecimal("12.5"), FlwInstanceServiceImpl.convertVariableValue("amount", 1.0, "12.5"));
        assertEquals(true, FlwInstanceServiceImpl.convertVariableValue("pass", false, "TRUE"));
        assertEquals("abc", FlwInstanceServiceImpl.convertVariableValue("name", "x", "abc"));
        assertEquals("abc", FlwInstanceServiceImpl.convertVariableValue("name", null, "abc"));
        assertThrows(ServiceException.class, () -> FlwInstanceServiceImpl.convertVariableValue("days", 3, "three"));
        assertThrows(ServiceException.class, () -> FlwInstanceServiceImpl.convertVariableValue("pass", true, "yes"));
        assertThrows(ServiceException.class, () -> FlwInstanceServiceImpl.convertVariableValue("map", Map.of("a", 1), "x"));
    }

    private static CurrentUser user(Long userId) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        return user;
    }

}
