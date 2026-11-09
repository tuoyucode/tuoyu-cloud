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
            defectMapper, materialMapper, workshopMapper, lineMapper, stationMapper, teamMapper, codeService,
            statusLogService, tasks);
    }

    @BeforeEach
    void login() {
        previousUser = MesTestSupport.loginAs(20L);
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
        when(workorderMapper.selectById(100L)).thenReturn(workorder);
        when(taskMapper.selectById(first.getTaskId())).thenReturn(first);
        when(taskMapper.selectById(second.getTaskId())).thenReturn(second);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(first, second)));
        when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(reportMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
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

            when(workorderMapper.selectById(100L)).thenReturn(workorder("completed", "10"));
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
        @DisplayName("末道工序合格数达到计划数：任务自动完成，工单自动完工，未完成的任务一并完成")
        void autoCompleteChain() {
            MesOperationTaskAssembler assembler = new MesOperationTaskAssembler(workorderMapper, taskMapper,
                materialMapper, workshopMapper, lineMapper, stationMapper, teamMapper);
            MesWorkorderServiceImpl workorderService = new MesWorkorderServiceImpl(workorderMapper, taskMapper,
                reportMapper, materialMapper, MesTestSupport.mapper(MesBomMapper.class),
                MesTestSupport.mapper(MesRouteMapper.class), MesTestSupport.mapper(MesRouteOperationMapper.class),
                MesTestSupport.mapper(MesFactoryMapper.class), workshopMapper, lineMapper, codeService,
                statusLogService, assembler);
            MesOperationTaskServiceImpl realTaskService = new MesOperationTaskServiceImpl(taskMapper, workorderMapper,
                stationMapper, teamMapper, MesTestSupport.mapper(MesTeamMemberMapper.class), workorderService,
                statusLogService, assembler);
            MesProductionReportServiceImpl chained = newService(realTaskService);

            MesOperationTask before = task(1L, 10, "in_progress", "8", "0");
            MesOperationTask after = task(1L, 10, "in_progress", "10", "0");
            MesWorkorder workorderBefore = workorder("in_progress", "10");
            workorderBefore.setCompletedQty(new BigDecimal("8"));
            MesWorkorder workorderAfter = workorder("in_progress", "10");
            workorderAfter.setCompletedQty(new BigDecimal("10"));
            when(taskMapper.selectById(1L)).thenReturn(before, after);
            when(workorderMapper.selectById(100L)).thenReturn(workorderBefore, workorderAfter);
            when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(before));
            when(taskMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            chained.submit(report(1L, "2", "0"));

            verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "auto_complete", "in_progress",
                "completed", null);
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "auto_complete", "in_progress",
                "completed", null);
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
        @DisplayName("撤销必须填写原因；已确认的报工不能撤销")
        void cancelRules() {
            assertThatThrownBy(() -> service.cancel(500L, "")).hasMessageContaining("撤销原因");
            saved("confirmed", 1L, "1", "0");
            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessageContaining("不能撤销");
        }

        @Test
        @DisplayName("工单已完工或已关闭时不能撤销报工")
        void cancelRejectsCompletedWorkorder() {
            saved("reported", 1L, "1", "0");
            twoOperations(workorder("completed", "10"), task(1L, 10, "completed", "10", "0"),
                task(2L, 20, "completed", "10", "0"));
            assertThatThrownBy(() -> service.cancel(500L, "录错")).hasMessageContaining("不能撤销报工");

            when(workorderMapper.selectById(100L)).thenReturn(workorder("closed", "10"));
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
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "completed", "10", "0"),
                task(2L, 20, "completed", "10", "0"));
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
            twoOperations(workorder("in_progress", "10"), task(1L, 10, "completed", "10", "0"),
                task(2L, 20, "completed", "8", "0"));
            when(statusLogService.list(BizType.OPERATION_TASK, 2L)).thenReturn(List.of(log("complete", "completed")));

            service.cancel(500L, "数量录错");

            assertThat(taskUpdate().getSqlSet()).doesNotContain("task_status");
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
        ArgumentCaptor<Wrapper<MesProductionReport>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportMapper).selectList(any(IPage.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("workorder_id IN (SELECT", "report_time >=",
            "report_time <=");
    }

    @Test
    @DisplayName("导出列表、统计与履历")
    void exportQueries() {
        when(reportMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new MesProductionReport()));
        assertThat(service.queryList(new MesProductionReportBo())).hasSize(1);
        assertThat(service.countList(new MesProductionReportBo())).isZero();
        service.logs(500L);
        verify(statusLogService).list(BizType.PRODUCTION_REPORT, 500L);
    }

}
