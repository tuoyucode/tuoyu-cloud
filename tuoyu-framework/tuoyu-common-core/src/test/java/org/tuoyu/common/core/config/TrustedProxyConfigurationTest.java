package org.tuoyu.common.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.common.core.utils.LoginFailureKeys;
import org.tuoyu.common.core.web.ip.ClientIpResolver;
import org.tuoyu.common.core.web.ip.TrustedProxies;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("可信代理配置装配")
class TrustedProxyConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TrustedProxyConfiguration.class));

    @AfterEach
    void restoreDefaultResolver() {
        ClientIpResolver.install(new ClientIpResolver(TrustedProxies.defaults()));
    }

    @Test
    @DisplayName("未配置时使用默认网段")
    void defaults() {
        runner.run(context -> {
            ClientIpResolver resolver = context.getBean(ClientIpResolver.class);
            assertSame(resolver, ClientIpResolver.current());
            assertEquals(TrustedProxies.defaults().ranges(), resolver.trustedProxies().ranges());
        });
    }

    @Test
    @DisplayName("逗号分隔的环境变量值（含 IPv6）绑定为网段列表并装配到 ServletUtils")
    void commaSeparated() {
        runner.withPropertyValues("tuoyu.web.trusted-proxies=203.0.113.0/24,2001:db8::/32")
            .run(context -> {
                assertEquals(List.of("203.0.113.0/24", "2001:db8::/32"), ClientIpResolver.current().trustedProxies().ranges());
                assertEquals("198.51.100.1", ClientIpResolver.current().resolve("203.0.113.5", List.of("198.51.100.1"), null));
                // 私有网段不再可信
                assertEquals("10.0.0.2", ClientIpResolver.current().resolve("10.0.0.2", List.of("198.51.100.1"), null));
            });
    }

    @Test
    @DisplayName("配置为 none 时不信任任何代理")
    void none() {
        runner.withPropertyValues("tuoyu.web.trusted-proxies=none")
            .run(context -> assertEquals(List.of(), ClientIpResolver.current().trustedProxies().ranges()));
    }

    @Test
    @DisplayName("配置 /0 或非法网段时拒绝启动")
    void rejectInvalid() {
        runner.withPropertyValues("tuoyu.web.trusted-proxies=0.0.0.0/0")
            .run(context -> {
                assertTrue(context.getStartupFailure() != null);
                assertTrue(rootMessage(context.getStartupFailure()).contains("前缀长度为 0"));
            });
        runner.withPropertyValues("tuoyu.web.trusted-proxies=10.0.0.0/8,proxy.internal")
            .run(context -> assertTrue(context.getStartupFailure() != null));
    }

    @Test
    @DisplayName("登录失败计数键名：账号忽略大小写与空白，IPv6 按 /64 统计")
    void loginFailureKeys() {
        assertEquals(LoginFailureKeys.account("admin"), LoginFailureKeys.account(" Admin "));
        assertEquals("pwd_err_cnt:admin", LoginFailureKeys.account("ADMIN"));
        assertEquals("global:login_fail_ip:203.0.113.9", LoginFailureKeys.ip("203.0.113.9"));
        assertEquals(LoginFailureKeys.ip("2001:db8:1:2::1"), LoginFailureKeys.ip("2001:db8:1:2:ffff::9"));
        assertEquals("global:login_fail_ip:2001:db8:1:2::/64", LoginFailureKeys.ip("2001:db8:1:2::1"));
        assertNull(LoginFailureKeys.ip(" "));
        assertNull(LoginFailureKeys.ip(null));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        StringBuilder messages = new StringBuilder();
        while (current != null) {
            messages.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
    }

}
