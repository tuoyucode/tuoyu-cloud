package org.tuoyu.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesTeamMember;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMemberMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.domain.bo.MesOperationTaskBo;
import org.tuoyu.mes.plan.domain.bo.MesTaskDispatchBo;
import org.tuoyu.mes.plan.domain.vo.MesOperationTaskVo;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.plan.service.IMesWorkorderService;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("工序任务：派工、手动完成、自动完成与我的任务")
@SuppressWarnings("unchecked")
class MesOperationTaskServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesOperationTaskMapper taskMapper = MesTestSupport.mapper(MesOperationTaskMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);
    private final MesTeamMapper teamMapper = MesTestSupport.mapper(MesTeamMapper.class);
    private final MesTeamMemberMapper teamMemberMapper = MesTestSupport.mapper(MesTeamMemberMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final IMesWorkorderService workorderService = Mockito.mock(IMesWorkorderService.class);
    private final IMesStatusLogService statusLogService = Mockito.mock(IMesStatusLogService.class);
    private final RemoteUserService remoteUserService = Mockito.mock(RemoteUserService.class);

    private final MesOperationTaskServiceImpl service = new MesOperationTaskServiceImpl(taskMapper, workorderMapper,
        stationMapper, teamMapper, teamMemberMapper, workorderService, statusLogService,
        new MesOperationTaskAssembler(workorderMapper, taskMapper, materialMapper, workshopMapper, lineMapper,
            stationMapper, teamMapper), remoteUserService);

    private UserContextProvider previousUser;

    @BeforeEach
    void knownUsers() {
        // 派工时作业人 20、21 是当前租户中的用户
        when(remoteUserService.selectListByIds(List.of(20L))).thenReturn(List.of(new RemoteUserVo()));
        when(remoteUserService.selectListByIds(List.of(21L))).thenReturn(List.of(new RemoteUserVo()));
    }

    /**
     * 任务的普通读取与加锁读取都返回同一份数据，所属工单加锁读取
     */
    private void given(MesOperationTask task, MesWorkorder workorder) {
        given(task, task, workorder);
    }

    /**
     * 普通读取（数据权限校验时）与加锁读取（锁定工单之后）返回不同的数据，模拟两次读取之间被其他操作修改；
     * 不在数据权限内的情形在各测试中单独设置
     */
    private void given(MesOperationTask plain, MesOperationTask locked, MesWorkorder workorder) {
        when(taskMapper.selectById(plain.getTaskId())).thenReturn(plain);
        doReturn(plain).when(taskMapper).selectByIdInScope(plain.getTaskId());
        doReturn(workorder).when(workorderMapper).selectByIdForUpdate(workorder.getWorkorderId());
        doReturn(List.of(locked)).when(taskMapper).selectByWorkorderForUpdate(workorder.getWorkorderId());
    }

    @AfterEach
    void restoreUser() {
        if (previousUser != null) {
            MesTestSupport.restore(previousUser);
        }
    }

    private static MesOperationTask task(String status) {
        MesOperationTask task = new MesOperationTask();
        task.setTaskId(1L);
        task.setTaskCode("OT1");
        task.setWorkorderId(100L);
        task.setSeqNo(10);
        task.setOperationName("装配");
        task.setWorkshopId(4L);
        task.setPlanQty(new BigDecimal("10"));
        task.setQualifiedQty(BigDecimal.ZERO);
        task.setDefectQty(BigDecimal.ZERO);
        task.setTaskStatus(status);
        return task;
    }

    private static MesWorkorder workorder(String status) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(100L);
        workorder.setWorkorderCode("WO001");
        workorder.setWorkshopId(4L);
        workorder.setPlanQty(new BigDecimal("10"));
        workorder.setWorkorderStatus(status);
        return workorder;
    }

    private static MesTaskDispatchBo dispatchTo(Long stationId, Long teamId, Long userId) {
        MesTaskDispatchBo bo = new MesTaskDispatchBo();
        bo.setStationId(stationId);
        bo.setTeamId(teamId);
        bo.setAssigneeUserId(userId);
        return bo;
    }

    private static MesStation station(long workshopId) {
        MesStation station = new MesStation();
        station.setStationId(6L);
        station.setWorkshopId(workshopId);
        return station;
    }

    @Test
    @DisplayName("派工至少选择工位、班组、作业人中的一项")
    void dispatchRequiresTarget() {
        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(null, null, null))).hasMessageContaining("至少选择");
    }

    @Test
    @DisplayName("派工：待派工 → 已派工，覆盖工位、班组与作业人并记录履历")
    void dispatch() {
        given(task("pending"), workorder("released"));
        when(stationMapper.selectById(6L)).thenReturn(station(4L));
        MesTeam team = new MesTeam();
        team.setTeamId(9L);
        team.setWorkshopId(4L);
        when(teamMapper.selectById(9L)).thenReturn(team);
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.dispatch(1L, dispatchTo(6L, 9L, 20L));

        ArgumentCaptor<Wrapper<MesOperationTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).update(isNull(), captor.capture());
        assertThat(((LambdaUpdateWrapper<MesOperationTask>) captor.getValue()).getSqlSet())
            .contains("station_id", "team_id", "assignee_user_id", "task_status");
        verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "dispatch", "pending", "dispatched", null);
    }

    @Test
    @DisplayName("作业人必须是当前租户中正常状态的用户（按调用方租户查询用户），否则拒绝且不锁定工单")
    void dispatchRejectsUnknownAssignee() {
        given(task("pending"), workorder("released"));

        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(null, null, 99L))).hasMessage("作业人不存在或已停用");
        verify(remoteUserService).selectListByIds(List.of(99L));
        verify(workorderMapper, never()).selectByIdForUpdate(anyLong());
        verify(taskMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("只派工位或班组时不查询用户")
    void dispatchWithoutAssigneeSkipsUserCheck() {
        given(task("pending"), workorder("released"));
        when(stationMapper.selectById(6L)).thenReturn(station(4L));
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.dispatch(1L, dispatchTo(6L, null, null));

        verify(remoteUserService, never()).selectListByIds(any());
    }

    @Test
    @DisplayName("已派工的任务可以重新派工；生产中的任务不能再派工")
    void redispatch() {
        given(task("dispatched"), workorder("in_progress"));
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.dispatch(1L, dispatchTo(null, null, 21L));
        verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "dispatch", "dispatched", "dispatched",
            null);

        given(task("in_progress"), workorder("in_progress"));
        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(null, null, 21L))).hasMessageContaining("不能派工");
    }

    @Test
    @DisplayName("工单已暂停时不能派工")
    void dispatchRejectsPausedWorkorder() {
        given(task("pending"), workorder("paused"));

        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(null, null, 20L)))
            .hasMessageContaining("工单当前为“已暂停”");
    }

    @Test
    @DisplayName("工位与班组必须属于工单的车间")
    void dispatchRejectsOtherWorkshop() {
        given(task("pending"), workorder("released"));
        when(stationMapper.selectById(6L)).thenReturn(station(99L));
        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(6L, null, null))).hasMessageContaining("工位");

        MesTeam team = new MesTeam();
        team.setWorkshopId(99L);
        when(teamMapper.selectById(9L)).thenReturn(team);
        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(null, 9L, null))).hasMessageContaining("班组");
        verify(taskMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("派工时状态已被其他操作改变则拒绝")
    void dispatchConcurrent() {
        given(task("pending"), workorder("released"));
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(null, null, 20L))).hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("派工先锁定工单行再加锁读取工序任务，状态按加锁读到的最新数据判断（读取之间已开工的任务不能再派工）")
    void dispatchUsesLockedData() {
        given(task("pending"), task("in_progress"), workorder("released"));

        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(null, null, 20L))).hasMessageContaining("不能派工");

        InOrder order = inOrder(workorderMapper, taskMapper);
        order.verify(taskMapper).selectByIdInScope(1L);
        order.verify(workorderMapper).selectByIdForUpdate(100L);
        order.verify(taskMapper).selectByWorkorderForUpdate(100L);
        verify(taskMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("派工、完成、详情、履历：任务不在数据权限内时按不存在处理，不锁定工单")
    void outOfScope() {
        given(task("in_progress"), workorder("released"));
        doReturn(null).when(taskMapper).selectByIdInScope(1L);

        assertThatThrownBy(() -> service.dispatch(1L, dispatchTo(null, null, 20L))).hasMessageContaining("工序任务不存在");
        assertThatThrownBy(() -> service.complete(1L)).hasMessageContaining("工序任务不存在");
        assertThatThrownBy(() -> service.queryById(1L)).hasMessageContaining("工序任务不存在");
        assertThatThrownBy(() -> service.logs(1L)).hasMessageContaining("工序任务不存在");
        verify(workorderMapper, never()).selectByIdForUpdate(anyLong());
        verify(statusLogService, never()).list(any(), anyLong());
    }

    @Test
    @DisplayName("手动完成：只有生产中可以完成；末道工序完成时检查工单是否自动完工")
    void completeLastOperation() {
        given(task("dispatched"), workorder("in_progress"));
        assertThatThrownBy(() -> service.complete(1L)).hasMessageContaining("不能完成");

        given(task("in_progress"), workorder("in_progress"));
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(taskMapper.exists(any(Wrapper.class))).thenReturn(false);
        service.complete(1L);

        verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "complete", "in_progress", "completed", null);
        verify(workorderService).completeIfFinished(100L);
    }

    @Test
    @DisplayName("不是末道工序时完成任务不触发工单完工")
    void completeMiddleOperation() {
        given(task("in_progress"), workorder("in_progress"));
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(taskMapper.exists(any(Wrapper.class))).thenReturn(true);

        service.complete(1L);

        verify(workorderService, never()).completeIfFinished(anyLong());
    }

    @Test
    @DisplayName("报工后自动完成：判断与修改在同一条条件更新中（生产中且合格数达到计划数）")
    void completeIfReached() {
        when(taskMapper.selectById(1L)).thenReturn(task("in_progress"));
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assertThat(service.completeIfReached(1L)).isTrue();

        ArgumentCaptor<Wrapper<MesOperationTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<MesOperationTask> update = (LambdaUpdateWrapper<MesOperationTask>) captor.getValue();
        assertThat(update.getSqlSet()).contains("task_status", "actual_end_time");
        assertThat(update.getSqlSegment()).contains("task_status =", "qualified_qty >= plan_qty");
        verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "auto_complete", "in_progress", "completed",
            null);
        verify(workorderService).completeIfFinished(100L);
    }

    @Test
    @DisplayName("合格数未达到计划数、任务已不在生产中（例如被并发完成）时条件更新落空，不记履历、不触发工单完工")
    void completeIfReachedNotReached() {
        assertThat(service.completeIfReached(1L)).as("任务不存在").isFalse();

        when(taskMapper.selectById(1L)).thenReturn(task("in_progress"));
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThat(service.completeIfReached(1L)).isFalse();

        verify(statusLogService, never()).record(any(), anyLong(), any(), any(), any(), any(), any());
        verify(workorderService, never()).completeIfFinished(anyLong());
    }

    @Test
    @DisplayName("能否报工：派给本人或本人所在班组的任务不看数据权限，其他任务按数据权限")
    void reportable() {
        previousUser = MesTestSupport.loginAs(20L);
        MesOperationTask mine = task("dispatched");
        mine.setAssigneeUserId(20L);
        assertThat(service.reportable(mine)).isTrue();

        MesTeamMember member = new MesTeamMember();
        member.setTeamId(9L);
        when(teamMemberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(member));
        MesOperationTask teamTask = task("dispatched");
        teamTask.setTeamId(9L);
        assertThat(service.reportable(teamTask)).isTrue();

        MesOperationTask other = task("dispatched");
        other.setAssigneeUserId(21L);
        other.setTeamId(8L);
        assertThat(service.reportable(other)).isFalse();
        doReturn(other).when(taskMapper).selectByIdInScope(1L);
        assertThat(service.reportable(other)).isTrue();
    }

    @Test
    @DisplayName("我的任务：派给当前用户或当前用户所在班组；工单编号按子查询过滤")
    void mineFilter() {
        previousUser = MesTestSupport.loginAs(20L);
        MesTeamMember member = new MesTeamMember();
        member.setTeamId(9L);
        when(teamMemberMapper.selectList(any(Wrapper.class))).thenReturn(List.of(member));
        MesOperationTaskBo bo = new MesOperationTaskBo();
        bo.setMine(true);
        bo.setWorkorderCode("WO");
        bo.setTaskStatus("pending,dispatched,in_progress");

        service.queryPageList(bo, new PageParam(1, 10));

        ArgumentCaptor<Wrapper<MesOperationTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).selectList(any(IPage.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("assignee_user_id", "team_id IN", "workorder_id IN (SELECT", "task_status IN");
        // 我的任务按派给谁过滤，不走数据权限
        verify(taskMapper, never()).selectPageInScope(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("列表填充工单编号、车间、工位、班组名称与可报数量")
    void pageFillsNames() {
        MesOperationTask first = task("completed");
        first.setQualifiedQty(new BigDecimal("8"));
        MesOperationTask second = task("in_progress");
        second.setTaskId(2L);
        second.setSeqNo(20);
        second.setQualifiedQty(new BigDecimal("3"));
        second.setDefectQty(new BigDecimal("1"));
        second.setStationId(6L);
        second.setTeamId(9L);
        when(taskMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(second));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        MesWorkorder workorder = workorder("in_progress");
        workorder.setBatchCode("B001");
        when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder));
        MesStation station = station(4L);
        station.setStationName("装配工位 1");
        when(stationMapper.selectByIds(anyCollection())).thenReturn(List.of(station));
        MesTeam team = new MesTeam();
        team.setTeamId(9L);
        team.setTeamName("甲班");
        when(teamMapper.selectByIds(anyCollection())).thenReturn(List.of(team));

        PageResult<MesOperationTaskVo> page = service.queryPageList(new MesOperationTaskBo(), new PageParam(1, 10));

        verify(taskMapper).selectPageInScope(any(), any(Wrapper.class));
        MesOperationTaskVo vo = page.getItems().get(0);
        assertThat(vo.getWorkorderCode()).isEqualTo("WO001");
        assertThat(vo.getWorkorderStatus()).isEqualTo("in_progress");
        assertThat(vo.getBatchCode()).isEqualTo("B001");
        assertThat(vo.getStationName()).isEqualTo("装配工位 1");
        assertThat(vo.getTeamName()).isEqualTo("甲班");
        assertThat(vo.getAvailableQty()).isEqualByComparingTo("4");
        assertThat(vo.getLastOperation()).isTrue();
    }

    @Test
    @DisplayName("工序任务选项：名称为工序顺序与工序名称；未指定工单时为空")
    void options() {
        assertThat(service.options(null)).isEmpty();
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task("pending")));

        List<MesOptionVo> options = service.options(100L);

        verify(taskMapper).selectListInScope(any(Wrapper.class));
        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.getName()).isEqualTo("10 装配");
            assertThat(option.getCode()).isEqualTo("OT1");
            assertThat(option.getParentId()).isEqualTo(100L);
        });
    }

    @Test
    @DisplayName("详情与履历；任务不存在时提示不存在")
    void detail() {
        assertThatThrownBy(() -> service.queryById(1L)).hasMessageContaining("工序任务不存在");
        doReturn(task("pending")).when(taskMapper).selectByIdInScope(1L);
        assertThat(service.queryById(1L).getTaskCode()).isEqualTo("OT1");
        service.logs(1L);
        verify(statusLogService).list(BizType.OPERATION_TASK, 1L);
    }

    @Test
    @DisplayName("工序前后关系与可报数量")
    void sequence() {
        MesOperationTask a = task("completed");
        a.setTaskId(1L);
        a.setSeqNo(10);
        a.setQualifiedQty(new BigDecimal("5"));
        MesOperationTask b = task("in_progress");
        b.setTaskId(2L);
        b.setSeqNo(20);
        b.setQualifiedQty(new BigDecimal("4"));
        b.setDefectQty(new BigDecimal("2"));
        MesOperationTask c = task("pending");
        c.setTaskId(3L);
        c.setSeqNo(30);
        List<MesOperationTask> tasks = List.of(a, b, c);

        assertThat(TaskSequence.previous(tasks, b)).isSameAs(a);
        assertThat(TaskSequence.previous(tasks, a)).isNull();
        assertThat(TaskSequence.next(tasks, b)).isSameAs(c);
        assertThat(TaskSequence.next(tasks, c)).isNull();
        assertThat(TaskSequence.isLast(tasks, c)).isTrue();
        assertThat(TaskSequence.isLast(tasks, b)).isFalse();
        assertThat(TaskSequence.available(new BigDecimal("10"), tasks, a)).isEqualByComparingTo("5");
        assertThat(TaskSequence.available(new BigDecimal("10"), tasks, b)).as("超出时按 0").isEqualByComparingTo("0");
        assertThat(TaskSequence.available(new BigDecimal("10"), tasks, c)).isEqualByComparingTo("4");
        assertThat(TaskSequence.plain(new BigDecimal("2.500000"))).isEqualTo("2.5");
        assertThat(TaskSequence.plain(null)).isEqualTo("0");
    }

}
