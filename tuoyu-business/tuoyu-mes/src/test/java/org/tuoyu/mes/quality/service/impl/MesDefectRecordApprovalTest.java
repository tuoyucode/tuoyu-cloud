package org.tuoyu.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.mes.common.approval.ApprovalOutcome;
import org.tuoyu.mes.common.approval.MesApprovalClient;
import org.tuoyu.mes.common.approval.MesApprovalProperties;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.domain.bo.MesDefectHandleBo;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.workflow.api.RemoteWorkflowService;
import org.tuoyu.workflow.api.domain.RemoteStartProcess;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("不良记录：处置评审（让步接收、报废需审批）")
class MesDefectRecordApprovalTest {

    static {
        MesTestSupport.init();
    }

    private final MesDefectRecordMapper defectMapper = MesTestSupport.mapper(MesDefectRecordMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);
    private final RemoteWorkflowService workflowService = mock(RemoteWorkflowService.class);
    private final MesApprovalProperties properties = new MesApprovalProperties();

    private final MesDefectRecordServiceImpl service = new MesDefectRecordServiceImpl(defectMapper, workorderMapper,
        MesTestSupport.mapper(MesOperationTaskMapper.class), materialMapper, MesTestSupport.mapper(MesInspectionMapper.class),
        MesTestSupport.mapper(MesProductionReportMapper.class), mock(IMesCodeService.class), statusLogService,
        mock(RemoteUserService.class), properties, new MesApprovalClient(workflowService),
        MesTestSupport.noopTransactionManager());

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        previous = MesTestSupport.loginAs(9L);
        properties.getDefectDisposition().setEnabled(true);
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(10L);
        workorder.setWorkorderCode("WO001");
        when(workorderMapper.selectById(10L)).thenReturn(workorder);
        MesMaterial material = new MesMaterial();
        material.setMaterialId(50L);
        material.setMaterialCode("M001");
        when(materialMapper.selectById(50L)).thenReturn(material);
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.restore(previous);
    }

    private static MesDefectRecord record(String status, String method, String businessId) {
        MesDefectRecord record = new MesDefectRecord();
        record.setDefectId(500L);
        record.setDefectCode("NG001");
        record.setWorkorderId(10L);
        record.setMaterialId(50L);
        record.setDefectQty(new BigDecimal("5.000000"));
        record.setHandleStatus(status);
        record.setHandleMethod(method);
        record.setHandleRemark("报废说明");
        record.setHandleUserId(9L);
        record.setApprovalBusinessId(businessId);
        record.setTenantId("t1");
        return record;
    }

    private static MesDefectHandleBo handleBo(String method) {
        MesDefectHandleBo bo = new MesDefectHandleBo();
        bo.setHandleMethod(method);
        bo.setHandleRemark("批次划伤严重");
        return bo;
    }

    /**
     * 更新语句的 SET 与 WHERE 部分（含参数值），用于核对条件更新
     */
    private static String sql(Wrapper<?> wrapper) {
        String set = wrapper instanceof Update<?, ?> update ? update.getSqlSet() : "";
        return set + " WHERE " + wrapper.getSqlSegment() + " " + ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
    }

    @SuppressWarnings("unchecked")
    private List<Wrapper<MesDefectRecord>> updates(int times) {
        ArgumentCaptor<Wrapper<MesDefectRecord>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(defectMapper, times(times)).update(any(), captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("开关关闭时报废照常直接处置，不调用工作流")
    void disabledHandlesDirectly() {
        properties.getDefectDisposition().setEnabled(false);
        when(defectMapper.selectById(500L)).thenReturn(record("pending", null, null));
        when(defectMapper.update(any(), any())).thenReturn(1);

        assertThat(service.handle(500L, handleBo("scrap"))).isEqualTo("handled");

        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "handle", "pending", "handled", "报废");
        verifyNoInteractions(workflowService);
    }

    @Test
    @DisplayName("开启时返工照常直接处置；上一轮被退回的审批实例被清理")
    void reworkHandlesDirectly() {
        when(defectMapper.selectById(500L)).thenReturn(record("pending", "scrap", "500"));
        when(defectMapper.update(any(), any())).thenReturn(1);
        when(workflowService.getBusinessStatus("500")).thenReturn("back");

        assertThat(service.handle(500L, handleBo("rework"))).isEqualTo("handled");

        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "handle", "pending", "handled", "返工");
        verify(workflowService, never()).startCompleteTask(any());
        verify(workflowService).deleteInstance(List.of("500"));
    }

    @Test
    @DisplayName("开启时报废进入审批中：先保存处置方式与业务 ID，再发起审批，标题包含工单、产品、数量与处置方式")
    void scrapStartsApproval() {
        when(defectMapper.selectById(500L)).thenReturn(record("pending", null, null));
        when(defectMapper.update(any(), any())).thenReturn(1);

        assertThat(service.handle(500L, handleBo("scrap"))).isEqualTo("approving");

        String update = sql(updates(1).getFirst());
        assertThat(update).contains("handle_status", "approving", "handle_method", "scrap", "approval_business_id", "500",
            "批次划伤严重", "pending");
        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "submit_approval", "pending", "approving", "报废");
        ArgumentCaptor<RemoteStartProcess> process = ArgumentCaptor.forClass(RemoteStartProcess.class);
        verify(workflowService).startCompleteTask(process.capture());
        assertThat(process.getValue().getFlowCode()).isEqualTo("mes_defect_disposition");
        assertThat(process.getValue().getBusinessId()).isEqualTo("500");
        assertThat(process.getValue().getBizExt().getBusinessCode()).isEqualTo("NG001");
        assertThat(process.getValue().getBizExt().getBusinessTitle()).isEqualTo("不良处置：工单 WO001 产品 M001 数量 5 报废");
    }

    @Test
    @DisplayName("上一轮审批已终止时，让步接收用下一轮的业务 ID 发起")
    void concessionAfterTermination() {
        when(defectMapper.selectById(500L)).thenReturn(record("pending", "scrap", "500"));
        when(defectMapper.update(any(), any())).thenReturn(1);
        when(workflowService.getBusinessStatus("500")).thenReturn("termination");

        assertThat(service.handle(500L, handleBo("concession"))).isEqualTo("approving");

        assertThat(sql(updates(1).getFirst())).contains("500-2", "concession");
        ArgumentCaptor<RemoteStartProcess> process = ArgumentCaptor.forClass(RemoteStartProcess.class);
        verify(workflowService).startCompleteTask(process.capture());
        assertThat(process.getValue().getBusinessId()).isEqualTo("500-2");
    }

    @Test
    @DisplayName("发起失败（例如流程未发布）时改回待处置、恢复业务 ID，并报告原因")
    void startFailureReverts() {
        when(defectMapper.selectById(500L)).thenReturn(record("pending", null, null));
        when(defectMapper.update(any(), any())).thenReturn(1);
        doThrow(new ServiceException("流程【mes_defect_disposition】未发布，请先在流程设计器中发布流程定义"))
            .when(workflowService).startCompleteTask(any());

        assertThatThrownBy(() -> service.handle(500L, handleBo("scrap")))
            .hasMessageContaining("未找到已发布的流程定义 mes_defect_disposition");

        List<Wrapper<MesDefectRecord>> updates = updates(2);
        String revert = sql(updates.get(1));
        assertThat(revert).contains("handle_status", "pending", "approving", "approval_business_id");
        verify(statusLogService).record(eq(BizType.DEFECT_RECORD), eq(500L), eq("NG001"), eq("approval_failed"),
            eq("approving"), eq("pending"), anyString());
    }

    @Test
    @DisplayName("本地改为审批中失败（状态已变化）时不发起审批")
    void concurrentChangeDoesNotStart() {
        when(defectMapper.selectById(500L)).thenReturn(record("pending", null, null));
        when(defectMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.handle(500L, handleBo("scrap"))).hasMessageContaining("状态已变化");

        verify(workflowService, never()).startCompleteTask(any());
    }

    @Test
    @DisplayName("审批中的不良不能再次处置")
    void approvingCannotBeHandled() {
        when(defectMapper.selectById(500L)).thenReturn(record("approving", "scrap", "500"));

        assertThatThrownBy(() -> service.handle(500L, handleBo("rework"))).hasMessageContaining("审批中");
    }

    @Test
    @DisplayName("审批通过：按提交时保存的处置方式执行处置（审批中 → 已处置）")
    void approvedAppliesDisposition() {
        when(defectMapper.selectById(500L)).thenReturn(record("approving", "scrap", "500"));
        when(defectMapper.update(any(), any())).thenReturn(1);

        service.completed(500L, "500", ApprovalOutcome.APPROVED, "同意报废");

        ArgumentCaptor<MesDefectRecord> change = ArgumentCaptor.forClass(MesDefectRecord.class);
        verify(defectMapper).update(change.capture(), any());
        assertThat(change.getValue().getHandleStatus()).isEqualTo("handled");
        assertThat(change.getValue().getHandleMethod()).isEqualTo("scrap");
        assertThat(change.getValue().getHandleUserId()).isEqualTo(9L);
        assertThat(change.getValue().getHandleTime()).isNotNull();
        assertThat(sql(updates(1).getFirst())).contains("approving", "500");
        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "approve", "approving", "handled",
            "报废（审批通过：同意报废）");
    }

    @Test
    @DisplayName("退回、撤销、终止：回到待处置并记录原因")
    void rejectedBackToPending() {
        when(defectMapper.selectById(500L)).thenReturn(record("approving", "scrap", "500"));
        when(defectMapper.update(any(), any())).thenReturn(1);

        service.completed(500L, "500", ApprovalOutcome.BACK, "数量有误");
        service.completed(500L, "500", ApprovalOutcome.WITHDRAWN, null);
        service.completed(500L, "500", ApprovalOutcome.TERMINATED, "不予处理");

        assertThat(sql(updates(3).getFirst())).contains("handle_status", "pending", "approving", "500");
        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "approval_back", "approving", "pending",
            "审批退回：数量有误");
        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "approval_cancel", "approving", "pending",
            "撤销审批");
        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "approval_terminate", "approving", "pending",
            "审批终止：不予处理");
    }

    @Test
    @DisplayName("幂等：重复的事件影响 0 行时不记履历；旧一轮的事件不更新")
    void idempotent() {
        when(defectMapper.selectById(500L)).thenReturn(record("handled", "scrap", "500"));
        when(defectMapper.update(any(), any())).thenReturn(0);
        service.completed(500L, "500", ApprovalOutcome.APPROVED, null);
        service.completed(500L, "500", ApprovalOutcome.BACK, null);
        verify(statusLogService, never()).record(any(), any(), any(), any(), any(), any(), any());

        when(defectMapper.selectById(500L)).thenReturn(record("approving", "scrap", "500-2"));
        service.completed(500L, "500", ApprovalOutcome.APPROVED, null);
        service.resubmitted(500L, "500");
        when(defectMapper.selectById(500L)).thenReturn(null);
        service.completed(500L, "500", ApprovalOutcome.APPROVED, null);
        verify(defectMapper, times(2)).update(any(), any());
    }

    @Test
    @DisplayName("在待办中重新提交被退回的审批：待处置 → 审批中")
    void resubmittedFromTodo() {
        when(defectMapper.selectById(500L)).thenReturn(record("pending", "scrap", "500"));
        when(defectMapper.update(any(), any())).thenReturn(1);

        service.resubmitted(500L, "500");

        assertThat(sql(updates(1).getFirst())).contains("approving", "pending", "scrap", "concession");
        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "submit_approval", "pending", "approving",
            "在待办中重新提交");
    }

    @Test
    @DisplayName("流程编码取自配置；租户按主键读出")
    void callbackBasics() {
        properties.getDefectDisposition().setFlowCode("custom_flow");
        assertThat(service.flowCode()).isEqualTo("custom_flow");
        when(defectMapper.selectById(500L)).thenReturn(record("approving", "scrap", "500"));
        assertThat(service.tenantOf(500L)).contains("t1");
        assertThat(service.tenantOf(501L)).isEmpty();
    }

}
