package org.tuoyu.common.encrypt;

import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.common.encrypt.config.PasswordHasherAutoConfiguration;
import org.tuoyu.common.encrypt.password.PasswordHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("密码哈希（BCrypt 强度可配置）")
class PasswordHasherTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PasswordHasherAutoConfiguration.class));

    @Test
    @DisplayName("按配置的强度保存哈希，未配置时为 10")
    void hashUsesConfiguredCost() {
        runner.run(context -> {
            PasswordHasher hasher = context.getBean(PasswordHasher.class);
            assertEquals(10, hasher.cost());
            assertTrue(hasher.hash("P@ssw0rd").startsWith("$2a$10$"));
        });
        runner.withPropertyValues("tuoyu.auth.password.bcrypt-cost=11").run(context ->
            assertTrue(context.getBean(PasswordHasher.class).hash("P@ssw0rd").startsWith("$2a$11$")));
    }

    @Test
    @DisplayName("强度超出 BCrypt 允许的范围时拒绝启动")
    void rejectsCostOutOfRange() {
        runner.withPropertyValues("tuoyu.auth.password.bcrypt-cost=3").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("tuoyu.auth.password.bcrypt-cost=32").run(context -> assertThat(context).hasFailed());
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(10).hash(null));
    }

    @Test
    @DisplayName("调整强度后，按旧强度保存的哈希仍能比对")
    void matchesHashOfAnotherCost() {
        String oldHash = BCrypt.hashpw("P@ssw0rd", BCrypt.gensalt(4));
        PasswordHasher hasher = new PasswordHasher(5);
        assertTrue(hasher.matches("P@ssw0rd", oldHash));
        assertFalse(hasher.matches("wrong", oldHash));
    }

    @Test
    @DisplayName("密码正确才通过；账号不存在（无哈希）或哈希格式错误时按不匹配处理")
    void matches() {
        PasswordHasher hasher = new PasswordHasher(4);
        String hash = hasher.hash("P@ssw0rd");
        assertTrue(hasher.matches("P@ssw0rd", hash));
        assertFalse(hasher.matches("wrong", hash));
        assertFalse(hasher.matches(null, hash));
        assertFalse(hasher.matches("P@ssw0rd", null));
        assertFalse(hasher.matches("P@ssw0rd", " "));
        assertFalse(hasher.matches("P@ssw0rd", "not-a-bcrypt-hash"));
    }

}
