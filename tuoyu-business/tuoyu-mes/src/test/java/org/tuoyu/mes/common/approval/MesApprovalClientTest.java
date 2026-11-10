package org.tuoyu.mes.common.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.workflow.api.RemoteWorkflowService;
import org.tuoyu.workflow.api.domain.RemoteStartProcess;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("单据审批：业务 ID、结果映射与发起审批")
class MesApprovalClientTest {

    private final RemoteWorkflowService workflowService = mock(RemoteWorkflowService.class);

    private final MesApprovalClient client = new MesApprovalClient(workflowService);

    @Test
    @DisplayName("业务 ID：第一轮为主键，之后在主键后加轮次；解析时取出主键，不是本模块格式时为 null")
    void businessIds() {
        assertThat(ApprovalBusinessIds.first(500L)).isEqualTo("500");
        assertThat(ApprovalBusinessIds.next(500L, null)).isEqualTo("500");
        assertThat(ApprovalBusinessIds.next(500L, "500")).isEqualTo("500-2");
        assertThat(ApprovalBusinessIds.next(500L, "500-2")).isEqualTo("500-3");
        assertThat(ApprovalBusinessIds.next(500L, "500-x")).isEqualTo("500-2");
        assertThat(ApprovalBusinessIds.parseId("500")).isEqualTo(500L);
        assertThat(ApprovalBusinessIds.parseId("500-3")).isEqualTo(500L);
        assertThat(ApprovalBusinessIds.parseId("leave-1")).isNull();
        assertThat(ApprovalBusinessIds.parseId(" ")).isNull();
        assertThat(ApprovalBusinessIds.parseId(null)).isNull();
    }

    @Test
    @DisplayName("流程状态映射为审批结果：进行中的状态没有结果")
    void outcomes() {
        assertThat(ApprovalOutcome.of("finish")).contains(ApprovalOutcome.APPROVED);
        assertThat(ApprovalOutcome.of("back")).contains(ApprovalOutcome.BACK);
        assertThat(ApprovalOutcome.of("cancel")).contains(ApprovalOutcome.WITHDRAWN);
        assertThat(ApprovalOutcome.of("termination")).contains(ApprovalOutcome.TERMINATED);
        assertThat(ApprovalOutcome.of("invalid")).contains(ApprovalOutcome.INVALIDATED);
        assertThat(ApprovalOutcome.of("waiting")).isEmpty();
        assertThat(ApprovalOutcome.of("draft")).isEmpty();
        assertThat(ApprovalOutcome.of("unknown")).isEmpty();
        assertThat(ApprovalOutcome.of(null)).isEmpty();
        assertThat(ApprovalOutcome.BACK.reason(" 数量不对 ")).isEqualTo("审批退回：数量不对");
        assertThat(ApprovalOutcome.APPROVED.reason(null)).isEqualTo("审批通过");
    }

    @Test
    @DisplayName("本轮的业务 ID：没审批过用主键；被退回、撤销或实例已删除时沿用；已结束时换下一轮；仍在审批中时拒绝")
    void prepare() {
        assertThat(client.prepare(500L, null)).isEqualTo("500");
        verifyNoInteractions(workflowService);

        when(workflowService.getBusinessStatus("500")).thenReturn("back");
        assertThat(client.prepare(500L, "500")).isEqualTo("500");
        when(workflowService.getBusinessStatus("500")).thenReturn("cancel");
        assertThat(client.prepare(500L, "500")).isEqualTo("500");
        when(workflowService.getBusinessStatus("500")).thenReturn("");
        assertThat(client.prepare(500L, "500")).isEqualTo("500");
        when(workflowService.getBusinessStatus("500")).thenReturn("termination");
        assertThat(client.prepare(500L, "500")).isEqualTo("500-2");
        when(workflowService.getBusinessStatus("500-2")).thenReturn("invalid");
        assertThat(client.prepare(500L, "500-2")).isEqualTo("500-3");

        when(workflowService.getBusinessStatus("500")).thenReturn("waiting");
        assertThatThrownBy(() -> client.prepare(500L, "500")).hasMessageContaining("已有审批中的流程");

        when(workflowService.getBusinessStatus("500")).thenThrow(new IllegalStateException("No provider"));
        assertThatThrownBy(() -> client.prepare(500L, "500"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("工作流服务暂不可用")
            .hasMessageNotContaining("No provider");
    }

    @Test
    @DisplayName("发起：启动流程并提交申请人节点，业务编号与标题供审批人查看")
    void start() {
        client.start("mes_defect_disposition", "500", "NG001", "不良处置：工单 WO001 报废");

        ArgumentCaptor<RemoteStartProcess> captor = ArgumentCaptor.forClass(RemoteStartProcess.class);
        verify(workflowService).startCompleteTask(captor.capture());
        RemoteStartProcess process = captor.getValue();
        assertThat(process.getFlowCode()).isEqualTo("mes_defect_disposition");
        assertThat(process.getBusinessId()).isEqualTo("500");
        assertThat(process.getBizExt().getBusinessId()).isEqualTo("500");
        assertThat(process.getBizExt().getBusinessCode()).isEqualTo("NG001");
        assertThat(process.getBizExt().getBusinessTitle()).isEqualTo("不良处置：工单 WO001 报废");
    }

    @Test
    @DisplayName("发起失败：流程未发布时提示导入并发布；其他业务异常带上原因；未预期的异常不带原消息")
    void startFailures() {
        doThrow(new ServiceException("流程【mes_defect_disposition】未发布，请先在流程设计器中发布流程定义"))
            .when(workflowService).startCompleteTask(any());
        assertThatThrownBy(() -> client.start("mes_defect_disposition", "500", "NG001", "标题"))
            .hasMessage("未找到已发布的流程定义 mes_defect_disposition，请先在“流程定义”中导入并发布");

        doThrow(new ServiceException("请检查流程第一个环节是否为申请人！")).when(workflowService).startCompleteTask(any());
        assertThatThrownBy(() -> client.start("mes_defect_disposition", "500", "NG001", "标题"))
            .hasMessage("发起审批失败：请检查流程第一个环节是否为申请人！");

        doThrow(new IllegalStateException("connection refused")).when(workflowService).startCompleteTask(any());
        assertThatThrownBy(() -> client.start("mes_defect_disposition", "500", "NG001", "标题"))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("工作流服务暂不可用")
            .hasMessageNotContaining("connection refused");
    }

    @Test
    @DisplayName("清理旧实例：只删除被退回、撤销或草稿的实例；没有业务 ID 时不调用；失败时不影响调用方")
    void discard() {
        client.discard(null);
        client.discard(" ");
        verifyNoInteractions(workflowService);

        when(workflowService.getBusinessStatus("500")).thenReturn("back");
        client.discard("500");
        verify(workflowService).deleteInstance(List.of("500"));

        when(workflowService.getBusinessStatus("501")).thenReturn("finish");
        client.discard("501");
        verify(workflowService, never()).deleteInstance(List.of("501"));

        when(workflowService.getBusinessStatus("502")).thenReturn("cancel");
        when(workflowService.deleteInstance(List.of("502"))).thenThrow(new ServiceException("权限不足，无法删除流程实例信息!"));
        assertThatNoException().isThrownBy(() -> client.discard("502"));
    }

}
