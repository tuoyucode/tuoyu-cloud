package org.tuoyu.mes.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.mes.common.approval.ApprovalOutcome;
import org.tuoyu.mes.common.approval.MesApprovalClient;
import org.tuoyu.mes.common.approval.MesApprovalProperties;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.material.domain.MesMaterialRequest;
import org.tuoyu.mes.material.domain.MesMaterialRequestItem;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
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

@DisplayName("领退料单：补料申请审批")
class MesMaterialRequestApprovalTest {

    static {
        MesTestSupport.init();
    }

    private final MesMaterialRequestMapper requestMapper = MesTestSupport.mapper(MesMaterialRequestMapper.class);
    private final MesMaterialRequestItemMapper itemMapper = MesTestSupport.mapper(MesMaterialRequestItemMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesWarehouseMapper warehouseMapper = MesTestSupport.mapper(MesWarehouseMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);
    private final RemoteWorkflowService workflowService = mock(RemoteWorkflowService.class);
    private final MesApprovalProperties properties = new MesApprovalProperties();

    private final MesMaterialRequestServiceImpl service = new MesMaterialRequestServiceImpl(requestMapper, itemMapper,
        workorderMapper, warehouseMapper, MesTestSupport.mapper(MesLocationMapper.class), materialMapper,
        MesTestSupport.mapper(MesBomMapper.class), MesTestSupport.mapper(MesBomItemMapper.class), mock(IMesCodeService.class),
        statusLogService, mock(RemoteUserService.class), properties, new MesApprovalClient(workflowService),
        MesTestSupport.noopTransactionManager());

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        previous = MesTestSupport.loginAs(7L);
        properties.getSupplement().setEnabled(true);
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(10L);
        workorder.setWorkorderCode("WO001");
        when(workorderMapper.selectById(10L)).thenReturn(workorder);
        MesWarehouse warehouse = new MesWarehouse();
        warehouse.setWarehouseId(1L);
        warehouse.setWarehouseName("原料仓");
        when(warehouseMapper.selectById(1L)).thenReturn(warehouse);
        MesMaterial material = new MesMaterial();
        material.setMaterialId(60L);
        material.setMaterialCode("M060");
        when(materialMapper.selectById(60L)).thenReturn(material);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(60L, "5.5"), item(61L, "2")));
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.restore(previous);
    }

    private static MesMaterialRequestItem item(Long materialId, String qty) {
        MesMaterialRequestItem item = new MesMaterialRequestItem();
        item.setMaterialId(materialId);
        item.setRequestQty(new BigDecimal(qty));
        return item;
    }

    private static MesMaterialRequest request(String type, String status, String businessId) {
        MesMaterialRequest request = new MesMaterialRequest();
        request.setRequestId(100L);
        request.setRequestCode("BL001");
        request.setRequestType(type);
        request.setWorkorderId(10L);
        request.setWarehouseId(1L);
        request.setRequestStatus(status);
        request.setApprovalBusinessId(businessId);
        request.setTenantId("t2");
        return request;
    }

    private static String sql(Wrapper<?> wrapper) {
        String set = wrapper instanceof Update<?, ?> update ? update.getSqlSet() : "";
        return set + " WHERE " + wrapper.getSqlSegment() + " " + ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().values();
    }

    @SuppressWarnings("unchecked")
    private List<Wrapper<MesMaterialRequest>> updates(int times) {
        ArgumentCaptor<Wrapper<MesMaterialRequest>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(requestMapper, times(times)).update(any(), captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("开关关闭时补料照常直接提交，不调用工作流")
    void disabledSubmitsDirectly() {
        properties.getSupplement().setEnabled(false);
        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "draft", null));
        when(requestMapper.update(any(), any())).thenReturn(1);

        assertThat(service.submit(100L)).isEqualTo("submitted");

        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "BL001", "submit", "draft", "submitted", null);
        verifyNoInteractions(workflowService);
    }

    @Test
    @DisplayName("开启时领料、退料照常直接提交")
    void issueSubmitsDirectly() {
        when(requestMapper.selectById(100L)).thenReturn(request("issue", "draft", null));
        when(requestMapper.update(any(), any())).thenReturn(1);

        assertThat(service.submit(100L)).isEqualTo("submitted");

        verifyNoInteractions(workflowService);
    }

    @Test
    @DisplayName("开启时补料进入审批中，标题包含工单、仓库与物料")
    void supplementStartsApproval() {
        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "draft", null));
        when(requestMapper.update(any(), any())).thenReturn(1);

        assertThat(service.submit(100L)).isEqualTo("approving");

        assertThat(sql(updates(1).getFirst())).contains("request_status", "approving", "submit_time",
            "approval_business_id", "100", "draft");
        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "BL001", "submit_approval", "draft", "approving", null);
        ArgumentCaptor<RemoteStartProcess> process = ArgumentCaptor.forClass(RemoteStartProcess.class);
        verify(workflowService).startCompleteTask(process.capture());
        assertThat(process.getValue().getFlowCode()).isEqualTo("mes_material_supplement");
        assertThat(process.getValue().getBusinessId()).isEqualTo("100");
        assertThat(process.getValue().getBizExt().getBusinessCode()).isEqualTo("BL001");
        assertThat(process.getValue().getBizExt().getBusinessTitle()).isEqualTo("补料申请：工单 WO001 原料仓 M060 × 5.5 等 2 项物料");
    }

    @Test
    @DisplayName("发起失败时改回草稿并报告原因")
    void startFailureReverts() {
        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "draft", null));
        when(requestMapper.update(any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("No provider available")).when(workflowService).startCompleteTask(any());

        assertThatThrownBy(() -> service.submit(100L)).hasMessageContaining("工作流服务暂不可用");

        assertThat(sql(updates(2).get(1))).contains("request_status", "draft", "approving");
        verify(statusLogService).record(eq(BizType.MATERIAL_REQUEST), eq(100L), eq("BL001"), eq("approval_failed"),
            eq("approving"), eq("draft"), anyString());
    }

    @Test
    @DisplayName("审批中的补料单不能修改、取消，仓库不能执行")
    void approvingIsLocked() {
        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "approving", "100"));

        assertThatThrownBy(() -> service.cancel(100L)).hasMessageContaining("审批中");
        assertThatThrownBy(() -> service.submit(100L)).hasMessageContaining("审批中");
        verify(requestMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("审批通过：审批中 → 已提交；退回、撤销、终止：回到草稿")
    void outcomes() {
        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "approving", "100"));
        when(requestMapper.update(any(), any())).thenReturn(1);

        service.completed(100L, "100", ApprovalOutcome.APPROVED, "同意");
        service.completed(100L, "100", ApprovalOutcome.BACK, "数量过多");
        service.completed(100L, "100", ApprovalOutcome.WITHDRAWN, null);
        service.completed(100L, "100", ApprovalOutcome.TERMINATED, null);

        List<Wrapper<MesMaterialRequest>> updates = updates(4);
        assertThat(sql(updates.get(0))).contains("submitted", "approving", "100");
        assertThat(sql(updates.get(1))).contains("draft", "approving", "100");
        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "BL001", "approve", "approving", "submitted", "审批通过：同意");
        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "BL001", "approval_back", "approving", "draft", "审批退回：数量过多");
        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "BL001", "approval_cancel", "approving", "draft", "撤销审批");
        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "BL001", "approval_terminate", "approving", "draft", "审批终止");
    }

    @Test
    @DisplayName("幂等：重复事件不记履历；旧一轮的事件与不存在的单据忽略")
    void idempotent() {
        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "submitted", "100"));
        when(requestMapper.update(any(), any())).thenReturn(0);
        service.completed(100L, "100", ApprovalOutcome.APPROVED, null);
        verify(statusLogService, never()).record(any(), any(), any(), any(), any(), any(), any());

        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "approving", "100-2"));
        service.completed(100L, "100", ApprovalOutcome.APPROVED, null);
        when(requestMapper.selectById(100L)).thenReturn(null);
        service.completed(100L, "100", ApprovalOutcome.APPROVED, null);
        service.resubmitted(100L, "100");
        assertThat(service.tenantOf(100L)).isEmpty();
        verify(requestMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("在待办中重新提交被退回的补料单：草稿 → 审批中；流程编码与租户")
    void resubmittedAndBasics() {
        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "draft", "100"));
        when(requestMapper.update(any(), any())).thenReturn(1);

        service.resubmitted(100L, "100");

        assertThat(sql(updates(1).getFirst())).contains("approving", "draft", "supplement");
        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "BL001", "submit_approval", "draft", "approving",
            "在待办中重新提交");
        assertThat(service.flowCode()).isEqualTo("mes_material_supplement");
        assertThat(service.tenantOf(100L)).contains("t2");
    }

    @Test
    @DisplayName("删除、取消退回后的草稿时清理留在待办中的审批实例")
    void discardOnDeleteAndCancel() {
        when(requestMapper.selectByIds(any())).thenReturn(List.of(request("supplement", "draft", "100")));
        when(requestMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(workflowService.getBusinessStatus("100")).thenReturn("back");

        service.deleteByIds(List.of(100L));
        verify(workflowService).deleteInstance(List.of("100"));

        when(requestMapper.selectById(100L)).thenReturn(request("supplement", "draft", "100"));
        when(requestMapper.update(any(), any())).thenReturn(1);
        service.cancel(100L);
        verify(workflowService, times(2)).deleteInstance(List.of("100"));
    }

}
