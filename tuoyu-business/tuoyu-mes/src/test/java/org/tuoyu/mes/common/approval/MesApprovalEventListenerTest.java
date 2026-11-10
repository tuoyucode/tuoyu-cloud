package org.tuoyu.mes.common.approval;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.workflow.api.event.ProcessEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("单据审批：接收流程事件")
class MesApprovalEventListenerTest {

    static {
        MesTestSupport.init();
    }

    private final RecordingCallback defect = new RecordingCallback("mes_defect_disposition", Optional.of("t1"));

    private final RecordingCallback supplement = new RecordingCallback("mes_material_supplement", Optional.of("t2"));

    private final MesApprovalEventListener listener = new MesApprovalEventListener(List.of(defect, supplement),
        MesTestSupport.noopTransactionManager());

    private static final String APPLICATION_NAME = "spring.application.name";

    /**
     * 流程事件的构造方法读取应用名（作为消息总线的来源服务）
     */
    @BeforeAll
    static void applicationName() {
        System.setProperty(APPLICATION_NAME, "tuoyu-workflow");
    }

    @AfterAll
    static void clearApplicationName() {
        System.clearProperty(APPLICATION_NAME);
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.tenant(false);
    }

    private static ProcessEvent event(String flowCode, String businessId, String status, Boolean submit) {
        ProcessEvent event = new ProcessEvent();
        event.setFlowCode(flowCode);
        event.setBusinessId(businessId);
        event.setStatus(status);
        event.setSubmit(submit);
        event.setParams(Map.of("message", "同意"));
        return event;
    }

    @Test
    @DisplayName("按流程编码分派：审批结果带上审批意见，其他流程的事件忽略")
    void dispatchByFlowCode() {
        listener.onProcessEvent(event("mes_defect_disposition", "500", "finish", false));
        listener.onProcessEvent(event("mes_material_supplement", "600-2", "back", false));
        listener.onProcessEvent(event("leave", "500", "finish", false));

        assertThat(defect.calls).containsExactly("completed 500 500 APPROVED 同意");
        assertThat(supplement.calls).containsExactly("completed 600 600-2 BACK 同意");
    }

    @Test
    @DisplayName("申请人节点提交按重新提交处理（流程状态可能仍是已退回）；进行中的状态与无法解析的业务 ID 忽略")
    void submitAndIgnored() {
        listener.onProcessEvent(event("mes_defect_disposition", "500", "back", true));
        listener.onProcessEvent(event("mes_defect_disposition", "500", "waiting", false));
        listener.onProcessEvent(event("mes_defect_disposition", "abc", "finish", false));
        ProcessEvent noParams = event("mes_defect_disposition", "500", "termination", null);
        noParams.setParams(null);
        listener.onProcessEvent(noParams);

        assertThat(defect.calls).containsExactly("resubmitted 500 500", "completed 500 500 TERMINATED null");
    }

    @Test
    @DisplayName("租户：忽略租户隔离读出单据的租户，再在该租户下处理；单据不存在时忽略")
    void tenant() {
        MesTestSupport.tenant(true);

        listener.onProcessEvent(event("mes_defect_disposition", "500", "cancel", false));

        assertThat(defect.ignoringWhenResolving).containsExactly(true);
        assertThat(defect.tenantWhenHandling).containsExactly("t1");
        assertThat(defect.calls).containsExactly("completed 500 500 WITHDRAWN 同意");

        RecordingCallback missing = new RecordingCallback("mes_defect_disposition", Optional.empty());
        new MesApprovalEventListener(List.of(missing), MesTestSupport.noopTransactionManager())
            .onProcessEvent(event("mes_defect_disposition", "500", "finish", false));
        assertThat(missing.calls).isEmpty();
    }

    private static final class RecordingCallback implements ApprovalCallback {

        private final String flowCode;

        private final Optional<String> tenant;

        private final List<String> calls = new ArrayList<>();

        private final List<Boolean> ignoringWhenResolving = new ArrayList<>();

        private final List<String> tenantWhenHandling = new ArrayList<>();

        private RecordingCallback(String flowCode, Optional<String> tenant) {
            this.flowCode = flowCode;
            this.tenant = tenant;
        }

        @Override
        public String flowCode() {
            return flowCode;
        }

        @Override
        public Optional<String> tenantOf(Long bizId) {
            ignoringWhenResolving.add(TenantHelper.isIgnoring());
            return tenant;
        }

        @Override
        public void resubmitted(Long bizId, String businessId) {
            tenantWhenHandling.add(TenantHelper.currentTenantId());
            calls.add("resubmitted " + bizId + " " + businessId);
        }

        @Override
        public void completed(Long bizId, String businessId, ApprovalOutcome outcome, String comment) {
            tenantWhenHandling.add(TenantHelper.currentTenantId());
            calls.add("completed " + bizId + " " + businessId + " " + outcome + " " + comment);
        }
    }

}
