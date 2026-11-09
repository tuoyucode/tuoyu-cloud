package org.tuoyu.mes.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.mes.plan.enums.WorkorderStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("状态流转的前置校验与编码枚举")
class StateGuardTest {

    @Test
    @DisplayName("当前状态允许时返回当前状态")
    void allowsListedStates() {
        assertThat(StateGuard.require("工单", WorkorderStatus.class, "draft", "下达", WorkorderStatus.DRAFT))
            .isEqualTo(WorkorderStatus.DRAFT);
    }

    @Test
    @DisplayName("不允许时的提示写明当前状态、动作与允许的状态")
    void rejectsOtherStates() {
        assertThatThrownBy(() -> StateGuard.require("工单", WorkorderStatus.class, "closed", "下达", WorkorderStatus.DRAFT))
            .isInstanceOf(ServiceException.class)
            .hasMessage("工单当前为“已关闭”，不能下达（只有草稿的工单可以下达）");
    }

    @Test
    @DisplayName("未知的状态编码视为数据错误")
    void rejectsUnknownState() {
        assertThatThrownBy(() -> StateGuard.require("工单", WorkorderStatus.class, "unknown", "下达", WorkorderStatus.DRAFT))
            .hasMessageContaining("状态不正确");
    }

    @Test
    @DisplayName("按编码查找、校验与判断")
    void codeEnumHelpers() {
        assertThat(CodeEnum.of(WorkorderStatus.class, "in_progress", "工单状态")).isEqualTo(WorkorderStatus.IN_PROGRESS);
        assertThatThrownBy(() -> CodeEnum.of(WorkorderStatus.class, "x", "工单状态")).hasMessage("工单状态不正确：x");
        assertThat(CodeEnum.validOrEmpty(WorkorderStatus.class, null)).isTrue();
        assertThat(CodeEnum.validOrEmpty(WorkorderStatus.class, "x")).isFalse();
        assertThat(CodeEnum.in("paused", WorkorderStatus.RELEASED, WorkorderStatus.PAUSED)).isTrue();
        assertThat(CodeEnum.find(WorkorderStatus.class, null)).isEmpty();
    }

}
