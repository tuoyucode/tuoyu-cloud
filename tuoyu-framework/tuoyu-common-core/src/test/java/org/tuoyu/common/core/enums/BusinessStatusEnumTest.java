package org.tuoyu.common.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BusinessStatusEnum：流程单据的业务状态")
class BusinessStatusEnumTest {

    @Test
    @DisplayName("状态码与描述")
    void values() {
        assertThat(BusinessStatusEnum.CANCEL.getStatus()).isEqualTo("cancel");
        assertThat(BusinessStatusEnum.CANCEL.getDesc()).isEqualTo("已撤销");
        assertThat(BusinessStatusEnum.DRAFT.getStatus()).isEqualTo("draft");
        assertThat(BusinessStatusEnum.DRAFT.getDesc()).isEqualTo("草稿");
        assertThat(BusinessStatusEnum.WAITING.getStatus()).isEqualTo("waiting");
        assertThat(BusinessStatusEnum.WAITING.getDesc()).isEqualTo("待审核");
        assertThat(BusinessStatusEnum.FINISH.getStatus()).isEqualTo("finish");
        assertThat(BusinessStatusEnum.FINISH.getDesc()).isEqualTo("已完成");
        assertThat(BusinessStatusEnum.INVALID.getStatus()).isEqualTo("invalid");
        assertThat(BusinessStatusEnum.INVALID.getDesc()).isEqualTo("已作废");
        assertThat(BusinessStatusEnum.BACK.getStatus()).isEqualTo("back");
        assertThat(BusinessStatusEnum.BACK.getDesc()).isEqualTo("已退回");
        assertThat(BusinessStatusEnum.TERMINATION.getStatus()).isEqualTo("termination");
        assertThat(BusinessStatusEnum.TERMINATION.getDesc()).isEqualTo("已终止");
        assertThat(BusinessStatusEnum.values()).containsExactly(BusinessStatusEnum.CANCEL, BusinessStatusEnum.DRAFT,
            BusinessStatusEnum.WAITING, BusinessStatusEnum.FINISH, BusinessStatusEnum.INVALID, BusinessStatusEnum.BACK,
            BusinessStatusEnum.TERMINATION);
    }

    @Test
    @DisplayName("按状态码查找：区分大小写，找不到时为 null")
    void getByStatus() {
        for (BusinessStatusEnum status : BusinessStatusEnum.values()) {
            assertThat(BusinessStatusEnum.getByStatus(status.getStatus())).isSameAs(status);
        }
        assertThat(BusinessStatusEnum.getByStatus("unknown")).isNull();
        assertThat(BusinessStatusEnum.getByStatus("DRAFT")).isNull();
        assertThat(BusinessStatusEnum.getByStatus("")).isNull();
    }

    @Test
    @DisplayName("按状态码查找：参数为 null 时返回 null（流程事件中的状态可能为空）")
    void getByStatusNull() {
        assertThat(BusinessStatusEnum.getByStatus(null)).isNull();
    }

    @Test
    @DisplayName("运行中与已结束的状态列表：顺序固定，contains(null) 为 false（业务模块直接传入可能为空的状态）")
    void statusLists() {
        assertThat(BusinessStatusEnum.runningStatus()).containsExactly("draft", "waiting", "back", "cancel");
        assertThat(BusinessStatusEnum.finishStatus()).containsExactly("finish", "invalid", "termination");
        List<String> running = BusinessStatusEnum.runningStatus();
        List<String> finish = BusinessStatusEnum.finishStatus();
        assertThat(running.contains(null)).isFalse();
        assertThat(finish.contains(null)).isFalse();
        assertThat(finish).contains("invalid").doesNotContain("waiting");
    }

    @Test
    @DisplayName("状态分类")
    void classify() {
        assertThat(BusinessStatusEnum.isDraftOrCancelOrBack("draft")).isTrue();
        assertThat(BusinessStatusEnum.isDraftOrCancelOrBack("cancel")).isTrue();
        assertThat(BusinessStatusEnum.isDraftOrCancelOrBack("back")).isTrue();
        assertThat(BusinessStatusEnum.isDraftOrCancelOrBack("waiting")).isFalse();
        assertThat(BusinessStatusEnum.isDraftOrCancelOrBack(null)).isFalse();

        assertThat(BusinessStatusEnum.initialState("cancel")).isTrue();
        assertThat(BusinessStatusEnum.initialState("back")).isTrue();
        assertThat(BusinessStatusEnum.initialState("invalid")).isTrue();
        assertThat(BusinessStatusEnum.initialState("termination")).isTrue();
        assertThat(BusinessStatusEnum.initialState("draft")).isFalse();
        assertThat(BusinessStatusEnum.initialState("waiting")).isFalse();
        assertThat(BusinessStatusEnum.initialState(null)).isFalse();
    }

    @Test
    @DisplayName("发起流程的校验：提示返回给前端，逐字一致")
    void checkStart() {
        for (String allowed : List.of("draft", "cancel", "back", "unknown")) {
            assertThatCode(() -> BusinessStatusEnum.checkStartStatus(allowed)).doesNotThrowAnyException();
        }
        assertRejected(() -> BusinessStatusEnum.checkStartStatus("waiting"), "该单据已提交过申请,正在审批中！");
        assertRejected(() -> BusinessStatusEnum.checkStartStatus("finish"), "该单据已完成申请！");
        assertRejected(() -> BusinessStatusEnum.checkStartStatus("invalid"), "该单据已作废！");
        assertRejected(() -> BusinessStatusEnum.checkStartStatus("termination"), "该单据已终止！");
        assertRejected(() -> BusinessStatusEnum.checkStartStatus(" "), "流程状态为空！");
        assertRejected(() -> BusinessStatusEnum.checkStartStatus(null), "流程状态为空！");
    }

    @Test
    @DisplayName("撤销的校验")
    void checkCancel() {
        assertThatCode(() -> BusinessStatusEnum.checkCancelStatus("waiting")).doesNotThrowAnyException();
        assertThatCode(() -> BusinessStatusEnum.checkCancelStatus("draft")).doesNotThrowAnyException();
        assertRejected(() -> BusinessStatusEnum.checkCancelStatus("cancel"), "该单据已撤销！");
        assertRejected(() -> BusinessStatusEnum.checkCancelStatus("finish"), "该单据已完成申请！");
        assertRejected(() -> BusinessStatusEnum.checkCancelStatus("invalid"), "该单据已作废！");
        assertRejected(() -> BusinessStatusEnum.checkCancelStatus("termination"), "该单据已终止！");
        assertRejected(() -> BusinessStatusEnum.checkCancelStatus("back"), "该单据已退回！");
        assertRejected(() -> BusinessStatusEnum.checkCancelStatus(""), "流程状态为空！");
    }

    @Test
    @DisplayName("驳回的校验")
    void checkBack() {
        assertThatCode(() -> BusinessStatusEnum.checkBackStatus("waiting")).doesNotThrowAnyException();
        assertThatCode(() -> BusinessStatusEnum.checkBackStatus("draft")).doesNotThrowAnyException();
        assertRejected(() -> BusinessStatusEnum.checkBackStatus("back"), "该单据已退回！");
        assertRejected(() -> BusinessStatusEnum.checkBackStatus("finish"), "该单据已完成申请！");
        assertRejected(() -> BusinessStatusEnum.checkBackStatus("invalid"), "该单据已作废！");
        assertRejected(() -> BusinessStatusEnum.checkBackStatus("termination"), "该单据已终止！");
        assertRejected(() -> BusinessStatusEnum.checkBackStatus("cancel"), "该单据已撤销！");
        assertRejected(() -> BusinessStatusEnum.checkBackStatus(null), "流程状态为空！");
    }

    @Test
    @DisplayName("作废、终止的校验")
    void checkInvalid() {
        for (String allowed : List.of("waiting", "draft", "cancel", "back")) {
            assertThatCode(() -> BusinessStatusEnum.checkInvalidStatus(allowed)).doesNotThrowAnyException();
        }
        assertRejected(() -> BusinessStatusEnum.checkInvalidStatus("finish"), "该单据已完成申请！");
        assertRejected(() -> BusinessStatusEnum.checkInvalidStatus("invalid"), "该单据已作废！");
        assertRejected(() -> BusinessStatusEnum.checkInvalidStatus("termination"), "该单据已终止！");
        assertRejected(() -> BusinessStatusEnum.checkInvalidStatus("  "), "流程状态为空！");
    }

    private static void assertRejected(Runnable check, String message) {
        assertThatThrownBy(check::run).isInstanceOf(ServiceException.class).hasMessage(message);
    }
}
