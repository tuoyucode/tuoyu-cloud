package org.tuoyu.mes.production.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteOperationMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMemberMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.material.domain.MesStockIn;
import org.tuoyu.mes.material.mapper.MesStockInMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.plan.service.IMesOperationTaskService;
import org.tuoyu.mes.plan.service.impl.MesOperationTaskAssembler;
import org.tuoyu.mes.plan.service.impl.MesOperationTaskServiceImpl;
import org.tuoyu.mes.plan.service.impl.MesWorkorderServiceImpl;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.domain.bo.MesProductionReportBo;
import org.tuoyu.mes.production.domain.vo.MesProductionReportVo;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.mes.trace.service.IMesSnService;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("报工：数量上限、首检门禁、数量累加、自动完工、不良登记与撤销回退")
@SuppressWarnings("unchecked")
class MesProductionReportServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesProductionReportMapper reportMapper = MesTestSupport.mapper(MesProductionReportMapper.class);
    private final MesOperationTaskMapper taskMapper = MesTestSupport.mapper(MesOperationTaskMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesInspectionMapper inspectionMapper = MesTestSupport.mapper(MesInspectionMapper.class);
    private final MesDefectRecordMapper defectMapper = MesTestSupport.mapper(MesDefectRecordMapper.class);
    private final MesStockInMapper stockInMapper = MesTestSupport.mapper(MesStockInMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);
    private final MesTeamMapper teamMapper = MesTestSupport.mapper(MesTeamMapper.class);
    private final IMesCodeService codeService = Mockito.mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = Mockito.mock(IMesStatusLogService.class);
    private final IMesOperationTaskService taskService = Mockito.mock(IMesOperationTaskService.class);

    private final MesProductionReportServiceImpl service = newService(taskService);

    private UserContextProvider previousUser;

    private MesProductionReportServiceImpl newService(IMesOperationTaskService tasks) {
        return new MesProductionReportServiceImpl(reportMapper, taskMapper, workorderMapper, inspectionMapper,
            defectMapper, stockInMapper, materialMapper, workshopMapper, lineMapper, stationMapper, teamMapper, codeService,
            statusLogService, tasks);
    }

    @BeforeEach
    void login() {
        previousUser = MesTestSupport.loginAs(20L);
        // 默认可以对任务报工；不能报工、报工记录不在数据权限内的情形在各测试中单独设置
        when(taskService.reportable(any(MesOperationTask.class))).thenReturn(true);
        when(codeService.next(CodeRules.REPORT)).thenReturn("RP001");
        when(codeService.next(CodeRules.DEFECT)).thenReturn("NG001");
        when(reportMapper.insert(any(MesProductionReport.class))).thenAnswer(invocation -> {
            invocation.<MesProductionReport>getArgument(0).setReportId(500L);
            return 1;
        });
        when(defectMapper.insert(any(MesDefectRecord.class))).thenAnswer(invocation -> {
            invocation.<MesDefectRecord>getArgument(0).setDefectId(600L);
            return 1;
        });
    }

    @AfterEach
    void restoreUser() {
        MesTestSupport.restore(previousUser);
    }

    // ---------------------------------------------------------------- 测试数据

    private static MesWorkorder workorder(String status, String planQty) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(100L);
        workorder.setWorkorderCode("WO001");
        workorder.setMaterialId(1L);
        workorder.setWorkshopId(4L);
        workorder.setLineId(5L);
        workorder.setPlanQty(new BigDecimal(planQty));
        workorder.setCompletedQty(BigDecimal.ZERO);
        workorder.setDefectQty(BigDecimal.ZERO);
        workorder.setBatchCode("B261109001");
        workorder.setWorkorderStatus(status);
        return workorder;
    }

    private static MesOperationTask task(long id, int seqNo, String status, String qualified, String defect) {
        MesOperationTask task = new MesOperationTask();
        task.setTaskId(id);
        task.setTaskCode("OT" + id);
        task.setWorkorderId(100L);
        task.setSeqNo(seqNo);
        task.setOperationName("工序" + seqNo);
        task.setPlanQty(new BigDecimal("10"));
        task.setQualifiedQty(new BigDecimal(qualified));
        task.setDefectQty(new BigDecimal(defect));
        task.setFirstInspection("N");
        task.setTaskStatus(status);
        return task;
    }

    private static MesProductionReportBo report(long taskId, String qualified, String defect) {
        MesProductionReportBo bo = new MesProductionReportBo();
        bo.setTaskId(taskId);
        bo.setQualifiedQty(qualified == null ? null : new BigDecimal(qualified));
        bo.setDefectQty(defect == null ? null : new BigDecimal(defect));
        return bo;
    }

    private static MesStatusLogVo log(String action, String to) {
        MesStatusLogVo log = new MesStatusLogVo();
        log.setAction(action);
        log.setToStatus(to);
        return log;
    }

    /**
     * 两道工序的工单：第 1 道（ID 1，工序 10）与第 2 道（ID 2，工序 20）
     */
    private void twoOperations(MesWorkorder workorder, MesOperationTask first, MesOperationTask second) {
        givenWorkorder(workorder);
        when(taskMapper.selectById(first.getTaskId())).thenReturn(first);
        when(taskMapper.selectById(second.getTaskId())).thenReturn(second);
        List<MesOperationTask> tasks = new ArrayList<>(List.of(first, second));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(tasks);
        doReturn(tasks).when(taskMapper).selectByWorkorderForUpdate(100L);
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(reportMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    /**
     * 按 ID 读取与加锁读取都返回该工单
     */
    private void givenWorkorder(MesWorkorder workorder) {
        when(workorderMapper.selectById(workorder.getWorkorderId())).thenReturn(workorder);
        doReturn(workorder).when(workorderMapper).selectByIdForUpdate(workorder.getWorkorderId());
    }

    private LambdaUpdateWrapper<MesOperationTask> taskUpdate() {
        ArgumentCaptor<Wrapper<MesOperationTask>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).update(isNull(), captor.capture());
        return (LambdaUpdateWrapper<MesOperationTask>) captor.getValue();
    }

    private LambdaUpdateWrapper<MesWorkorder> workorderUpdate() {
        ArgumentCaptor<Wrapper<MesWorkorder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workorderMapper).update(isNull(), captor.capture());
        return (LambdaUpdateWrapper<MesWorkorder>) captor.getValue();
    }

    // ---------------------------------------------------------------- 提交

    @Nested
    @DisplayName("提交报工")
    class Submit {

        @Test
        @DisplayName("合格数与不良数合计必须大于 0")
        void rejectsZero() {
            assertThatThrownBy(() -> service.submit(report(1L, "0", null))).hasMessageContaining("合计必须大于 0");
            assertThatThrownBy(() -> service.submit(report(1L, "-1", "2"))).hasMessageContaining("不能小于 0");
        }

        @Test
        @DisplayName("结束时间不能早于开始时间")
        void rejectsReversedTime() {
            MesProductionReportBo bo = report(1L, "1", null);
            bo.setStartTime(LocalDateTime.of(2026, 11, 9, 10, 0));
            bo.setEndTime(LocalDateTime.of(2026, 11, 9, 9, 0));
            assertThatThrownBy(() -> service.submit(bo)).hasMessageContaining("结束时间");
        }

        @Test
        @DisplayName("工单已暂停或已完工时拒绝报工")
        void rejectsPausedWorkorder() {
            twoOperations(workorder("paused", "10"), task(1L, 10, "in_progress", "0", "0"),
                task(2L, 20, "pending", "0", "0"));
            assertThatThrownBy(() -> service.submit(report(1L, "1", null)))
                .hasMessageContaining("工单当前为“已暂停”，不能报工");

            givenWorkorder(workorder("completed", "10"));
            assertThatThrownBy(() -> service.submit(report(1L, "1", null))).hasMessageContaining("已完工");
            verify(reportMapper, never()).insert(any(MesProductionReport.class));
        }

        @Test
        @DisplayName("已完成的工序任务不能再报工")
        void rejectsCompletedTask() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "completed", "10", "0"),
                task(2L, 20, "pending", "0", "0"));
            assertThatThrownBy(() -> service.submit(report(1L, "1", null)))
                .hasMessageContaining("工序任务当前为“已完成”");
        }

        @Test
        @DisplayName("第一道工序累计报工不超过工单计划数")
        void firstOperationCapacity() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "7", "1"),
                task(2L, 20, "pending", "0", "0"));

            assertThatThrownBy(() -> service.submit(report(1L, "2", "1")))
                .hasMessageContaining("本工序最多还能报工 2");
            verify(reportMapper, never()).insert(any(MesProductionReport.class));
        }

        @Test
        @DisplayName("后续工序累计报工不超过上一道工序的累计合格数")
        void laterOperationCapacity() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "5", "3"),
                task(2L, 20, "in_progress", "3.5", "1"));

            assertThatThrownBy(() -> service.submit(report(2L, "1", null)))
                .hasMessageContaining("本工序最多还能报工 0.5");
        }

        @Test
        @DisplayName("需要首检的工序没有合格的首检单时拒绝报工")
        void firstInspectionGate() {
            MesOperationTask first = task(1L, 10, "dispatched", "0", "0");
            first.setFirstInspection("Y");
            twoOperations(workorder("released", "10"), first, task(2L, 20, "pending", "0", "0"));

            assertThatThrownBy(() -> service.submit(report(1L, "1", null)))
                .hasMessage("该工序需要首检合格后才能报工");

            when(inspectionMapper.exists(any(Wrapper.class))).thenReturn(true);
            service.submit(report(1L, "1", null));
            verify(reportMapper).insert(any(MesProductionReport.class));
        }

        @Test
        @DisplayName("首次报工：写入报工记录，任务与工单进入生产中，累加数量，不良数登记为待处置不良记录")
        void firstReport() {
            MesOperationTask first = task(1L, 10, "dispatched", "0", "0");
            first.setStationId(6L);
            first.setTeamId(9L);
            twoOperations(workorder("released", "10"), first, task(2L, 20, "pending", "0", "0"));
            MesProductionReportBo bo = report(1L, "3", "1");
            bo.setWorkMinutes(new BigDecimal("30"));

            service.submit(bo);

            ArgumentCaptor<MesProductionReport> saved = ArgumentCaptor.forClass(MesProductionReport.class);
            verify(reportMapper).insert(saved.capture());
            MesProductionReport report = saved.getValue();
            assertThat(report.getReportCode()).isEqualTo("RP001");
            assertThat(report.getReportQty()).isEqualByComparingTo("4");
            assertThat(report.getReportUserId()).isEqualTo(20L);
            assertThat(report.getReportStatus()).isEqualTo("reported");
            assertThat(report.getBatchCode()).isEqualTo("B261109001");
            assertThat(report.getStationId()).as("未指定工位时取派工的工位").isEqualTo(6L);
            assertThat(report.getTeamId()).isEqualTo(9L);
            assertThat(report.getWorkshopId()).isEqualTo(4L);
            assertThat(report.getLineId()).isEqualTo(5L);
            assertThat(report.getMaterialId()).isEqualTo(1L);
            assertThat(report.getReportTime()).isNotNull();

            LambdaUpdateWrapper<MesOperationTask> taskWrapper = taskUpdate();
            assertThat(taskWrapper.getSqlSet())
                .contains("qualified_qty = qualified_qty +", "defect_qty = defect_qty +", "task_status",
                    "actual_start_time");
            assertThat(taskWrapper.getSqlSegment()).contains("qualified_qty + defect_qty <=");
            assertThat(taskWrapper.getParamNameValuePairs()).containsValue(new BigDecimal("6"));

            LambdaUpdateWrapper<MesWorkorder> workorderWrapper = workorderUpdate();
            assertThat(workorderWrapper.getSqlSet())
                .contains("defect_qty = defect_qty +", "workorder_status", "actual_start_time")
                .as("不是末道工序，不累加完工数").doesNotContain("completed_qty")
                .as("入库数量不由报工写入").doesNotContain("stocked_qty");

            verify(statusLogService).record(BizType.PRODUCTION_REPORT, 500L, "RP001", "create", null, "reported", null);
            verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "start", "dispatched", "in_progress",
                null);
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "start", "released", "in_progress", null);

            ArgumentCaptor<MesDefectRecord> defect = ArgumentCaptor.forClass(MesDefectRecord.class);
            verify(defectMapper).insert(defect.capture());
            assertThat(defect.getValue().getSourceType()).isEqualTo("report");
            assertThat(defect.getValue().getSourceId()).isEqualTo(500L);
            assertThat(defect.getValue().getHandleStatus()).isEqualTo("pending");
            assertThat(defect.getValue().getDefectLevel()).isEqualTo("minor");
            assertThat(defect.getValue().getDefectCode()).isEqualTo("NG001");
            assertThat(defect.getValue().getDefectQty()).isEqualByComparingTo("1");
            assertThat(defect.getValue().getTaskId()).isEqualTo(1L);
            verify(statusLogService).record(eq(BizType.DEFECT_RECORD), eq(600L), eq("NG001"), eq("create"), isNull(),
                eq("pending"), anyString());
            verify(taskService).completeIfReached(1L);
        }

        @Test
        @DisplayName("末道工序的合格数累加到工单完工数；没有不良数时不登记不良记录；生产中的任务不再记开工")
        void lastOperation() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "0"),
                task(2L, 20, "in_progress", "2", "0"));

            service.submit(report(2L, "6", "0"));

            assertThat(workorderUpdate().getSqlSet()).contains("completed_qty = completed_qty +")
                .doesNotContain("workorder_status");
            assertThat(taskUpdate().getSqlSet()).doesNotContain("task_status");
            verify(defectMapper, never()).insert(any(MesDefectRecord.class));
            verify(statusLogService, never()).record(eq(BizType.OPERATION_TASK), anyLong(), anyString(), eq("start"),
                anyString(), anyString(), any());
        }

        @Test
        @DisplayName("指定的工位必须属于工单的车间")
        void rejectsStationOfOtherWorkshop() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "0", "0"),
                task(2L, 20, "pending", "0", "0"));
            MesStation station = new MesStation();
            station.setWorkshopId(99L);
            when(stationMapper.selectById(6L)).thenReturn(station);
            MesProductionReportBo bo = report(1L, "1", null);
            bo.setStationId(6L);

            assertThatThrownBy(() -> service.submit(bo)).hasMessageContaining("工位不存在或不属于工单的车间");
        }

        @Test
        @DisplayName("并发报工使任务的条件更新落空时拒绝，工单数量不变")
        void concurrentReport() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "0", "0"),
                task(2L, 20, "pending", "0", "0"));
            when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> service.submit(report(1L, "1", null))).hasMessageContaining("请刷新后重试");
            verify(workorderMapper, never()).update(any(), any(Wrapper.class));
        }

        @Test
        @DisplayName("工单状态被并发改变时拒绝")
        void concurrentWorkorder() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "0", "0"),
                task(2L, 20, "pending", "0", "0"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> service.submit(report(1L, "1", null))).hasMessageContaining("工单状态已变化");
        }

        @Test
        @DisplayName("任务不是我的任务、也不在数据权限内时按不存在处理，不锁定工单")
        void rejectsTaskOutOfScope() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "0", "0"),
                task(2L, 20, "pending", "0", "0"));
            when(taskService.reportable(any(MesOperationTask.class))).thenReturn(false);

            assertThatThrownBy(() -> service.submit(report(1L, "1", null))).hasMessage("工序任务不存在");
            assertThatThrownBy(() -> service.submit(report(9L, "1", null))).hasMessage("工序任务不存在");
            verify(workorderMapper, never()).selectByIdForUpdate(anyLong());
            verify(reportMapper, never()).insert(any(MesProductionReport.class));
        }

        @Test
        @DisplayName("先锁定工单行，再加锁读取工序任务，之后才写报工记录与累加数量")
        void locksWorkorderThenTasks() {
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "0", "0"),
                task(2L, 20, "pending", "0", "0"));

            service.submit(report(1L, "1", null));

            InOrder order = inOrder(taskService, workorderMapper, taskMapper, reportMapper);
            order.verify(taskService).reportable(any(MesOperationTask.class));
            order.verify(workorderMapper).selectByIdForUpdate(100L);
            order.verify(taskMapper).selectByWorkorderForUpdate(100L);
            order.verify(reportMapper).insert(any(MesProductionReport.class));
            order.verify(taskMapper).update(isNull(), any(Wrapper.class));
            order.verify(workorderMapper).update(isNull(), any(Wrapper.class));
        }

        @Test
        @DisplayName("上限按加锁读到的最新数据计算：上一道工序的合格数刚被撤销减少时，本工序按减少后的合格数报工")
        void capacityUsesLockedData() {
            MesOperationTask staleFirst = task(1L, 10, "in_progress", "10", "0");
            MesOperationTask second = task(2L, 20, "in_progress", "3", "0");
            twoOperations(workorder("in_progress", "10"), staleFirst, second);
            MesOperationTask lockedFirst = task(1L, 10, "in_progress", "5", "0");
            doReturn(List.of(lockedFirst, second)).when(taskMapper).selectByWorkorderForUpdate(100L);

            assertThatThrownBy(() -> service.submit(report(2L, "3", null))).hasMessage("本工序最多还能报工 2");
            verify(reportMapper, never()).insert(any(MesProductionReport.class));
        }

        /**
         * 报工、自动完成任务与自动完工工单都用真实的服务串起来，序列号服务为模拟对象
         */
        private MesProductionReportServiceImpl chained(IMesSnService snService) {
            MesOperationTaskAssembler assembler = new MesOperationTaskAssembler(workorderMapper, taskMapper,
                materialMapper, workshopMapper, lineMapper, stationMapper, teamMapper);
            MesWorkorderServiceImpl workorderService = new MesWorkorderServiceImpl(workorderMapper, taskMapper,
                reportMapper, materialMapper, MesTestSupport.mapper(MesBomMapper.class),
                MesTestSupport.mapper(MesRouteMapper.class), MesTestSupport.mapper(MesRouteOperationMapper.class),
                MesTestSupport.mapper(MesFactoryMapper.class), workshopMapper, lineMapper, codeService,
                statusLogService, assembler, snService);
            MesOperationTaskServiceImpl realTaskService = new MesOperationTaskServiceImpl(taskMapper, workorderMapper,
                stationMapper, teamMapper, MesTestSupport.mapper(MesTeamMemberMapper.class), workorderService,
                statusLogService, assembler, Mockito.mock(RemoteUserService.class));
            return newService(realTaskService);
        }

        @Test
        @DisplayName("有不良时按已报数量自动完成：末道已报（合格 + 不良）达到前道合格数即完成，工单随之完工，不要求完工数达到计划数")
        void autoCompleteWithDefects() {
            IMesSnService snService = Mockito.mock(IMesSnService.class);
            MesProductionReportServiceImpl chained = chained(snService);
            // 计划 10：第 1 道已完成（合格 9、不良 1），第 2 道（末道）已报合格 7、不良 1，本次再报合格 1
            MesOperationTask first = task(1L, 10, "completed", "9", "1");
            MesOperationTask before = task(2L, 20, "in_progress", "7", "1");
            MesOperationTask after = task(2L, 20, "in_progress", "8", "1");
            MesWorkorder workorder = workorder("in_progress", "10");
            workorder.setCompletedQty(new BigDecimal("7"));
            givenWorkorder(workorder);
            when(taskMapper.selectById(2L)).thenReturn(before);
            doReturn(before).when(taskMapper).selectByIdInScope(2L);
            doReturn(new ArrayList<>(List.of(first, before)), new ArrayList<>(List.of(first, after)))
                .when(taskMapper).selectByWorkorderForUpdate(100L);
            when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            chained.submit(report(2L, "1", "0"));

            ArgumentCaptor<Wrapper<MesOperationTask>> taskUpdates = ArgumentCaptor.forClass(Wrapper.class);
            verify(taskMapper, Mockito.atLeast(2)).update(isNull(), taskUpdates.capture());
            // 自动完成带上数量条件：已报数量不少于上一道的合格数 9，以数据库中的当前值为准
            assertThat(taskUpdates.getAllValues()).anySatisfy(w -> {
                assertThat(w.getSqlSegment()).contains("qualified_qty + defect_qty >=");
                assertThat(((LambdaUpdateWrapper<MesOperationTask>) w).getParamNameValuePairs())
                    .containsValue(new BigDecimal("9"));
            });
            ArgumentCaptor<Wrapper<MesWorkorder>> workorderUpdates = ArgumentCaptor.forClass(Wrapper.class);
            verify(workorderMapper, Mockito.atLeast(2)).update(isNull(), workorderUpdates.capture());
            assertThat(workorderUpdates.getAllValues()).noneSatisfy(w -> assertThat(w.getSqlSegment())
                .contains("completed_qty >= plan_qty"));

            verify(statusLogService).record(BizType.OPERATION_TASK, 2L, "OT2", "auto_complete", "in_progress",
                "completed", null);
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "auto_complete", "in_progress",
                "completed", null);
            verify(snService).completeByWorkorder(100L);
        }

        @Test
        @DisplayName("首道达到计划数自动完成后带动后续工序：后续工序已报数量达到首道合格数时一并完成")
        void autoCompleteChainsToNextOperation() {
            IMesSnService snService = Mockito.mock(IMesSnService.class);
            MesProductionReportServiceImpl chained = chained(snService);
            // 计划 10：第 1 道已报合格 8、不良 1，第 2 道已按第 1 道的合格数报完（合格 8），第 3 道已报合格 6、不良 1
            MesOperationTask firstBefore = task(1L, 10, "in_progress", "8", "1");
            MesOperationTask firstAfter = task(1L, 10, "in_progress", "8", "2");
            MesOperationTask second = task(2L, 20, "in_progress", "8", "0");
            MesOperationTask third = task(3L, 30, "in_progress", "6", "1");
            givenWorkorder(workorder("in_progress", "10"));
            when(taskMapper.selectById(1L)).thenReturn(firstBefore);
            doReturn(firstBefore).when(taskMapper).selectByIdInScope(1L);
            doReturn(new ArrayList<>(List.of(firstBefore, second, third)),
                new ArrayList<>(List.of(firstAfter, second, third)))
                .when(taskMapper).selectByWorkorderForUpdate(100L);
            when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            chained.submit(report(1L, "0", "1"));

            verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "auto_complete", "in_progress",
                "completed", null);
            verify(statusLogService).record(BizType.OPERATION_TASK, 2L, "OT2", "auto_complete", "in_progress",
                "completed", null);
            // 第 3 道已报 7，少于第 2 道的合格数 8：保持生产中，工单不完工
            verify(statusLogService, never()).record(eq(BizType.OPERATION_TASK), eq(3L), anyString(),
                eq("auto_complete"), any(), any(), any());
            verify(statusLogService, never()).record(eq(BizType.WORKORDER), anyLong(), anyString(),
                eq("auto_complete"), any(), any(), any());
            verify(snService, never()).completeByWorkorder(anyLong());
        }

    }

    // ---------------------------------------------------------------- 确认与撤销

    @Nested
    @DisplayName("确认与撤销")
    class ConfirmAndCancel {

        private MesProductionReport saved(String status, long taskId, String qualified, String defect) {
            MesProductionReport report = new MesProductionReport();
            report.setReportId(500L);
            report.setReportCode("RP001");
            report.setWorkorderId(100L);
            report.setTaskId(taskId);
            report.setQualifiedQty(new BigDecimal(qualified));
            report.setDefectQty(new BigDecimal(defect));
            report.setReportStatus(status);
            when(reportMapper.selectById(500L)).thenReturn(report);
            doReturn(report).when(reportMapper).selectByIdForUpdate(500L);
            doReturn(report).when(reportMapper).selectByIdInScope(500L);
            return report;
        }

        @Test
        @DisplayName("确认：已提交 → 已确认；已撤销的不能确认")
        void confirm() {
            saved("reported", 1L, "1", "0");
            when(reportMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            service.confirm(500L);
            verify(statusLogService).record(BizType.PRODUCTION_REPORT, 500L, "RP001", "confirm", "reported",
                "confirmed", null);

            saved("cancelled", 1L, "1", "0");
            assertThatThrownBy(() -> service.confirm(500L)).hasMessageContaining("不能确认");
        }

        @Test
        @DisplayName("重复确认：状态已被另一次确认改变时条件更新落空，拒绝且不记履历")
        void confirmTwice() {
            saved("reported", 1L, "1", "0");
            when(reportMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> service.confirm(500L)).hasMessageContaining("状态已变化");
            verify(statusLogService, never()).record(eq(BizType.PRODUCTION_REPORT), anyLong(), anyString(),
                eq("confirm"), any(), any(), any());
        }

        @Test
        @DisplayName("详情、履历、确认、撤销：报工记录不在数据权限内时按不存在处理")
        void outOfScope() {
            saved("reported", 1L, "1", "0");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "0"),
                task(2L, 20, "pending", "0", "0"));
            doReturn(null).when(reportMapper).selectByIdInScope(500L);

            assertThatThrownBy(() -> service.queryById(500L)).hasMessage("报工记录不存在");
            assertThatThrownBy(() -> service.logs(500L)).hasMessage("报工记录不存在");
            assertThatThrownBy(() -> service.confirm(500L)).hasMessage("报工记录不存在");
            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessage("报工记录不存在");
            verify(reportMapper, never()).update(any(), any(Wrapper.class));
            verify(taskMapper, never()).update(any(), any(Wrapper.class));
            verify(statusLogService, never()).list(any(), anyLong());
        }

        @Test
        @DisplayName("撤销的加锁顺序：报工记录 → 工单 → 工序任务，之后再改报工状态与扣回数量")
        void cancelLockOrder() {
            saved("reported", 1L, "1", "0");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "0"),
                task(2L, 20, "pending", "0", "0"));

            service.cancel(500L, "录错");

            InOrder order = inOrder(reportMapper, workorderMapper, taskMapper);
            order.verify(reportMapper).selectByIdForUpdate(500L);
            order.verify(workorderMapper).selectByIdForUpdate(100L);
            order.verify(taskMapper).selectByWorkorderForUpdate(100L);
            order.verify(reportMapper).selectByIdInScope(500L);
            order.verify(reportMapper).update(isNull(), any(Wrapper.class));
            order.verify(taskMapper).update(isNull(), any(Wrapper.class));
            order.verify(workorderMapper).update(isNull(), any(Wrapper.class));
        }

        @Test
        @DisplayName("重复撤销：加锁读到已撤销时拒绝；状态被并发改变使条件更新落空时拒绝，数量都不扣回")
        void cancelTwice() {
            saved("cancelled", 1L, "1", "0");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "0"),
                task(2L, 20, "pending", "0", "0"));
            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessageContaining("不能撤销");

            saved("reported", 1L, "1", "0");
            when(reportMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessageContaining("报工记录状态已变化");

            verify(taskMapper, never()).update(any(), any(Wrapper.class));
            verify(workorderMapper, never()).update(any(), any(Wrapper.class));
        }

        @Test
        @DisplayName("后续工序是否已报工按加锁读到的最新数据判断")
        void cancelUsesLockedNextOperation() {
            saved("reported", 1L, "3", "0");
            MesOperationTask first = task(1L, 10, "in_progress", "8", "0");
            twoOperations(workorder("in_progress", "10"), first, task(2L, 20, "in_progress", "0", "0"));
            doReturn(List.of(first, task(2L, 20, "in_progress", "5", "1"))).when(taskMapper)
                .selectByWorkorderForUpdate(100L);

            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessage("后续工序已按该数量报工，不能撤销");
            verify(reportMapper, never()).update(any(), any(Wrapper.class));
        }

        @Test
        @DisplayName("末道工序撤销后完工数量不能少于已入库数量加已提交的入库单数量；扣回带上同样的条件")
        void cancelRespectsStockedQty() {
            saved("reported", 2L, "3", "0");
            MesWorkorder stocked = workorder("in_progress", "10");
            stocked.setCompletedQty(new BigDecimal("8"));
            stocked.setStockedQty(new BigDecimal("3"));
            twoOperations(stocked, task(1L, 10, "in_progress", "10", "0"), task(2L, 20, "in_progress", "8", "0"));
            MesStockIn submitted = new MesStockIn();
            submitted.setQty(new BigDecimal("3"));
            when(stockInMapper.selectList(any(Wrapper.class))).thenReturn(List.of(submitted));

            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessageContaining("已入库 3、待入库 3")
                .hasMessageContaining("不能撤销");
            verify(reportMapper, never()).update(any(), any(Wrapper.class));
            ArgumentCaptor<Wrapper<MesStockIn>> stockIns = ArgumentCaptor.forClass(Wrapper.class);
            verify(stockInMapper).selectList(stockIns.capture());
            assertThat(stockIns.getValue().getSqlSegment()).contains("workorder_id =", "stock_in_status =")
                .endsWith("for update");

            submitted.setQty(new BigDecimal("2"));
            service.cancel(500L, "录错");
            LambdaUpdateWrapper<MesWorkorder> wrapper = workorderUpdate();
            assertThat(wrapper.getSqlSet()).contains("completed_qty = completed_qty -");
            assertThat(wrapper.getSqlSegment()).contains("defect_qty >=", "completed_qty -", ">= stocked_qty +");
            assertThat(wrapper.getParamNameValuePairs()).containsValue(new BigDecimal("2"));
        }

        @Test
        @DisplayName("不是末道工序时不扣完工数量，也不读取入库单")
        void cancelMiddleOperationSkipsStockIn() {
            saved("reported", 1L, "1", "0");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "0"),
                task(2L, 20, "pending", "0", "0"));

            service.cancel(500L, "录错");

            verify(stockInMapper, never()).selectList(any(Wrapper.class));
        }

        @Test
        @DisplayName("报工登记的不良品已被处置时不能撤销")
        void cancelRejectsHandledDefect() {
            saved("reported", 1L, "3", "1");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "1"),
                task(2L, 20, "in_progress", "5", "0"));
            when(defectMapper.delete(any(Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessage("该报工登记的不良品已处置或正在审批，不能撤销");
        }

        @Test
        @DisplayName("撤销必须填写原因；已确认的报工不能撤销")
        void cancelRules() {
            assertThatThrownBy(() -> service.cancel(500L, "")).hasMessageContaining("撤销原因");
            saved("confirmed", 1L, "1", "0");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "0"),
                task(2L, 20, "pending", "0", "0"));
            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessageContaining("不能撤销");
        }

        @Test
        @DisplayName("工单已完工或已关闭时不能撤销报工")
        void cancelRejectsCompletedWorkorder() {
            saved("reported", 1L, "1", "0");
            twoOperations(workorder("completed", "10"), task(1L, 10, "completed", "10", "0"),
                task(2L, 20, "completed", "10", "0"));
            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessageContaining("不能撤销报工");

            givenWorkorder(workorder("closed", "10"));
            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessageContaining("已关闭");
        }

        @Test
        @DisplayName("后续工序已按该数量报工时不能撤销")
        void cancelRejectsWhenNextOperationReported() {
            saved("reported", 1L, "3", "0");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "0"),
                task(2L, 20, "in_progress", "5", "1"));

            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessage("后续工序已按该数量报工，不能撤销");
            verify(reportMapper, never()).update(any(), any(Wrapper.class));
        }

        @Test
        @DisplayName("撤销：扣回任务与工单数量，删除该报工产生的待处置不良记录")
        void cancel() {
            saved("reported", 1L, "3", "1");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "1"),
                task(2L, 20, "in_progress", "5", "0"));
            when(defectMapper.delete(any(Wrapper.class))).thenReturn(1);

            service.cancel(500L, "录错");

            verify(statusLogService).record(BizType.PRODUCTION_REPORT, 500L, "RP001", "cancel", "reported",
                "cancelled", "录错");
            LambdaUpdateWrapper<MesOperationTask> taskWrapper = taskUpdate();
            assertThat(taskWrapper.getSqlSet()).contains("qualified_qty = qualified_qty -", "defect_qty = defect_qty -")
                .doesNotContain("task_status");
            assertThat(taskWrapper.getSqlSegment()).contains("qualified_qty >=", "defect_qty >=");
            assertThat(workorderUpdate().getSqlSet()).contains("defect_qty = defect_qty -")
                .as("不是末道工序，不扣完工数").doesNotContain("completed_qty");
            ArgumentCaptor<Wrapper<MesDefectRecord>> defects = ArgumentCaptor.forClass(Wrapper.class);
            verify(defectMapper).delete(defects.capture());
            assertThat(defects.getValue().getSqlSegment()).contains("source_type", "source_id", "handle_status");
        }

        @Test
        @DisplayName("自动完成的任务撤销后合格数低于计划数时回到生产中；末道工序扣回工单完工数")
        void cancelReopensAutoCompletedTask() {
            saved("reported", 2L, "3", "0");
            MesWorkorder workorder = workorder("in_progress", "10");
            workorder.setCompletedQty(new BigDecimal("10"));
            twoOperations(workorder, task(1L, 10, "completed", "10", "0"), task(2L, 20, "completed", "10", "0"));
            when(statusLogService.list(BizType.OPERATION_TASK, 2L)).thenReturn(List.of(
                log("start", "in_progress"), log("auto_complete", "completed")));

            service.cancel(500L, "数量录错");

            assertThat(taskUpdate().getSqlSet()).contains("task_status", "actual_end_time");
            assertThat(workorderUpdate().getSqlSet()).contains("completed_qty = completed_qty -");
            verify(statusLogService).record(eq(BizType.OPERATION_TASK), eq(2L), eq("OT2"), eq("reopen"),
                eq("completed"), eq("in_progress"), anyString());
        }

        @Test
        @DisplayName("手动完成的任务撤销报工后保持已完成")
        void cancelKeepsManuallyCompletedTask() {
            saved("reported", 2L, "3", "0");
            MesWorkorder workorder = workorder("in_progress", "10");
            workorder.setCompletedQty(new BigDecimal("8"));
            twoOperations(workorder, task(1L, 10, "completed", "10", "0"), task(2L, 20, "completed", "8", "0"));
            when(statusLogService.list(BizType.OPERATION_TASK, 2L)).thenReturn(List.of(log("complete", "completed")));

            service.cancel(500L, "数量录错");

            assertThat(taskUpdate().getSqlSet()).doesNotContain("task_status");
        }

        @Test
        @DisplayName("自动完成的任务按已报数量判断是否回到生产中：只撤销不良数时同样回到生产中")
        void cancelDefectOnlyReopensAutoCompletedTask() {
            saved("reported", 2L, "0", "1");
            MesWorkorder workorder = workorder("in_progress", "10");
            workorder.setDefectQty(new BigDecimal("2"));
            // 第 1 道合格 9，第 2 道已报合格 8、不良 1，达到上限 9 后自动完成
            twoOperations(workorder, task(1L, 10, "completed", "9", "1"), task(2L, 20, "completed", "8", "1"));
            when(statusLogService.list(BizType.OPERATION_TASK, 2L)).thenReturn(List.of(log("auto_complete", "completed")));
            when(defectMapper.delete(any(Wrapper.class))).thenReturn(1);

            service.cancel(500L, "不良录错");

            assertThat(taskUpdate().getSqlSet()).contains("task_status", "actual_end_time");
            verify(statusLogService).record(eq(BizType.OPERATION_TASK), eq(2L), eq("OT2"), eq("reopen"),
                eq("completed"), eq("in_progress"), anyString());
        }

        @Test
        @DisplayName("撤销会使自动完成的任务回到生产中、而后续工序已完成时拒绝，数量不扣回")
        void cancelRejectsReopenWhenNextCompleted() {
            saved("reported", 1L, "0", "1");
            // 第 1 道合格 8、不良 2，撤销 1 个不良后已报 9 少于计划数 10；第 2 道已按合格数 8 报完并完成
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "completed", "8", "2"),
                task(2L, 20, "completed", "8", "0"));
            when(statusLogService.list(BizType.OPERATION_TASK, 1L)).thenReturn(List.of(log("auto_complete", "completed")));

            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessage("后续工序已完成，不能撤销");
            verify(reportMapper, never()).update(any(), any(Wrapper.class));
            verify(taskMapper, never()).update(any(), any(Wrapper.class));
        }

        @Test
        @DisplayName("本工序保持已完成时，下一道的上限减少，撤销后检查下一道能否自动完成")
        void cancelChecksNextOperationWhenKept() {
            saved("reported", 1L, "2", "0");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "completed", "10", "0"),
                task(2L, 20, "in_progress", "8", "0"));
            when(statusLogService.list(BizType.OPERATION_TASK, 1L)).thenReturn(List.of(log("complete", "completed")));

            service.cancel(500L, "录错");

            verify(taskService).completeIfReached(2L);
        }

        @Test
        @DisplayName("本工序未完成或回到生产中时，撤销后不检查下一道")
        void cancelSkipsNextOperationCheck() {
            saved("reported", 1L, "1", "0");
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "in_progress", "8", "0"),
                task(2L, 20, "pending", "0", "0"));

            service.cancel(500L, "录错");

            verify(taskService, never()).completeIfReached(anyLong());
        }

        @Test
        @DisplayName("报工记录不存在时提示不存在")
        void missing() {
            assertThatThrownBy(() -> service.confirm(1L)).hasMessageContaining("报工记录不存在");
            assertThatThrownBy(() -> service.queryById(null)).hasMessageContaining("报工记录不存在");
        }

    }

    // ---------------------------------------------------------------- 查询

    @Test
    @DisplayName("列表填充工单编号、工序名称、产品名称；按工单编号用子查询过滤")
    void pageFillsNames() {
        MesProductionReport report = new MesProductionReport();
        report.setReportId(500L);
        report.setWorkorderId(100L);
        report.setTaskId(1L);
        report.setMaterialId(1L);
        when(reportMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(report));
        when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder("in_progress", "10")));
        when(taskMapper.selectByIds(anyCollection())).thenReturn(List.of(task(1L, 10, "in_progress", "1", "0")));
        MesMaterial material = new MesMaterial();
        material.setMaterialId(1L);
        material.setMaterialName("控制器");
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material));
        MesProductionReportBo query = new MesProductionReportBo();
        query.setWorkorderCode("WO");
        query.setBeginReportTime(LocalDateTime.of(2026, 11, 1, 0, 0));
        query.setEndReportTime(LocalDateTime.of(2026, 11, 30, 23, 59, 59));

        PageResult<MesProductionReportVo> page = service.queryPageList(query, new PageParam(1, 10));

        MesProductionReportVo vo = page.getItems().get(0);
        assertThat(vo.getWorkorderCode()).isEqualTo("WO001");
        assertThat(vo.getOperationName()).isEqualTo("工序10");
        assertThat(vo.getSeqNo()).isEqualTo(10);
        assertThat(vo.getMaterialName()).isEqualTo("控制器");
        verify(reportMapper).selectPageInScope(any(), any(Wrapper.class));
        ArgumentCaptor<Wrapper<MesProductionReport>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportMapper).selectList(any(IPage.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("workorder_id IN (SELECT", "report_time >=",
            "report_time <=");
    }

    @Test
    @DisplayName("导出列表、统计与履历")
    void exportQueries() {
        when(reportMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new MesProductionReport()));
        doReturn(new MesProductionReport()).when(reportMapper).selectByIdInScope(500L);
        assertThat(service.queryList(new MesProductionReportBo())).hasSize(1);
        assertThat(service.countList(new MesProductionReportBo())).isZero();
        service.logs(500L);
        verify(statusLogService).list(BizType.PRODUCTION_REPORT, 500L);
        verify(reportMapper).selectListInScope(any(Wrapper.class));
        verify(reportMapper).selectPageInScope(any(), any(Wrapper.class));
    }

}
