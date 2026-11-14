package org.tuoyu.auth.login;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("登录策略索引：按授权类型查找，重复时启动失败")
class LoginStrategiesTest {

    private record FixedStrategy(String grantType) implements LoginStrategy {

        @Override
        public VerifiedLogin verify(String body) {
            return null;
        }

    }

    @Test
    @DisplayName("按授权类型找到对应的策略")
    void findsByGrantType() {
        LoginStrategy password = new FixedStrategy("password");
        LoginStrategy sms = new FixedStrategy("sms");
        LoginStrategies strategies = new LoginStrategies(List.of(password, sms));

        assertThat(strategies.find("password")).isSameAs(password);
        assertThat(strategies.find("sms")).isSameAs(sms);
    }

    @Test
    @DisplayName("未知、为空或大小写不同的授权类型提示“授权类型不正确!”")
    void unknownGrantType() {
        LoginStrategies strategies = new LoginStrategies(List.of(new FixedStrategy("password")));

        for (String grantType : new String[]{"unknown", null, "", "PASSWORD"}) {
            assertThatThrownBy(() -> strategies.find(grantType)).as(String.valueOf(grantType))
                .isInstanceOf(ServiceException.class)
                .hasMessage("授权类型不正确!");
        }
    }

    @Test
    @DisplayName("两个策略声明同一个授权类型时启动失败")
    void duplicateGrantType() {
        List<LoginStrategy> duplicated = List.of(new FixedStrategy("password"), new FixedStrategy("password"));
        assertThatThrownBy(() -> new LoginStrategies(duplicated))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("password");
    }

}
