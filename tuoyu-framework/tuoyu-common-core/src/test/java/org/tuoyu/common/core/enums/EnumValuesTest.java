package org.tuoyu.common.core.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("枚举的常量与取值")
class EnumValuesTest {

    @Test
    @DisplayName("登录方式")
    void loginType() {
        assertThat(LoginType.values()).contains(LoginType.PASSWORD, LoginType.SMS, LoginType.EMAIL);
        assertThat(LoginType.valueOf("PASSWORD")).isSameAs(LoginType.PASSWORD);
    }

    @Test
    @DisplayName("推送消息的来源与类型：取值写进推送的消息体，前端按它区分")
    void push() {
        assertThat(PushSourceEnum.BACKEND.getSource()).isEqualTo("backend");
        assertThat(PushSourceEnum.NOTICE.getSource()).isEqualTo("notice");
        assertThat(PushSourceEnum.WORKFLOW.getSource()).isEqualTo("workflow");
        assertThat(PushSourceEnum.LLM.getSource()).isEqualTo("llm");
        assertThat(PushSourceEnum.CLIENT.getSource()).isEqualTo("client");

        assertThat(PushTypeEnum.MESSAGE.getType()).isEqualTo("message");
        assertThat(PushTypeEnum.NOTICE.getType()).isEqualTo("notice");
        assertThat(PushTypeEnum.LLM.getType()).isEqualTo("llm");
        assertThat(PushTypeEnum.CUSTOM.getType()).isEqualTo("custom");
    }

    @Test
    @DisplayName("用户状态")
    void userStatus() {
        assertThat(UserStatus.OK.getCode()).isEqualTo("0");
        assertThat(UserStatus.DISABLE.getCode()).isEqualTo("1");
    }

    @Test
    @DisplayName("用户类型：注册接口按 values() 精确匹配，两种类型都可以注册")
    void userType() {
        assertThat(UserType.SYS_USER.getUserType()).isEqualTo("sys_user");
        assertThat(UserType.APP_USER.getUserType()).isEqualTo("app_user");
        assertThat(UserType.values()).containsExactly(UserType.SYS_USER, UserType.APP_USER);
    }
}
