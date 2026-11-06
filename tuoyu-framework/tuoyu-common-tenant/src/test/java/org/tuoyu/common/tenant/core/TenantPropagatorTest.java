package org.tuoyu.common.tenant.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.ContextPropagators;
import org.tuoyu.common.core.context.UserContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("多租户：跨服务透传与签名")
class TenantPropagatorTest {

    private static final String SECRET = "test-only-tenant-context-secret";

    private final TenantPropagator propagator = new TenantPropagator(SECRET, Duration.ofSeconds(60));

    @BeforeEach
    void enableTenant() {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        new DefaultTenantContext(properties, new StaticListableBeanFactory().getBeanProvider(UserContext.class), id -> null);
    }

    @Test
    @DisplayName("显式指定的租户在提供方恢复，结束后还原")
    void roundTrip() {
        Map<String, String> captured = TenantHelper.dynamic("acme", propagator::capture);

        assertNull(TenantHelper.currentTenantId());
        try (ContextPropagator.Scope ignored = propagator.restore(captured)) {
            assertEquals("acme", TenantHelper.currentTenantId());
        }
        assertNull(TenantHelper.currentTenantId());
    }

    @Test
    @DisplayName("忽略租户的标记也透传")
    void ignoreRoundTrip() {
        Map<String, String> captured = TenantHelper.ignore(propagator::capture);

        try (ContextPropagator.Scope ignored = propagator.restore(captured)) {
            assertTrue(TenantHelper.isIgnoring());
        }
        assertFalse(TenantHelper.isIgnoring());
    }

    @Test
    @DisplayName("篡改租户、用别的密钥签名都被拒绝")
    void tamperingIsRejected() {
        String value = TenantHelper.dynamic("acme", propagator::capture).get(TenantPropagator.KEY);
        Map<String, String> tampered = Map.of(TenantPropagator.KEY, value.replaceFirst("acme", "evil"));
        assertThrows(SecurityException.class, () -> propagator.restore(tampered));

        TenantPropagator other = new TenantPropagator("another-secret-value", Duration.ofSeconds(60));
        Map<String, String> forged = TenantHelper.dynamic("acme", other::capture);
        assertThrows(SecurityException.class, () -> propagator.restore(forged));
    }

    @Test
    @DisplayName("超过有效期被拒绝（防重放）")
    void expiredIsRejected() {
        Clock past = Clock.fixed(Instant.now().minusSeconds(120), ZoneOffset.UTC);
        Map<String, String> old = TenantHelper.dynamic("acme", new TenantPropagator(SECRET, Duration.ofSeconds(60), past)::capture);

        assertThrows(SecurityException.class, () -> propagator.restore(old));
    }

    @Test
    @DisplayName("没有显式租户时不透传（提供方从令牌得到用户所属租户）；缺少密钥时拒绝启动")
    void nothingToPropagate() {
        assertTrue(propagator.capture().isEmpty());
        assertTrue(propagator.captureLocal().isEmpty());
        assertThrows(IllegalStateException.class, () -> new TenantPropagator(" ", Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("跨线程：任务排队超过跨服务的有效期后执行，仍在提交时的租户中")
    void localHasNoExpiry() throws Exception {
        MovableClock clock = new MovableClock();
        TenantPropagator local = new TenantPropagator(SECRET, Duration.ofSeconds(60), clock);
        ContextPropagators propagators = new ContextPropagators(List.of(local));

        Callable<String> task = TenantHelper.dynamic("acme", () -> propagators.wrap((Callable<String>) TenantHelper::currentTenantId));
        clock.advance(Duration.ofHours(2));

        assertEquals("acme", task.call());
        assertNull(TenantHelper.currentTenantId());
    }

    @Test
    @DisplayName("跨线程：忽略租户的标记也透传")
    void localIgnoreRoundTrip() {
        Map<String, String> captured = TenantHelper.ignore(propagator::captureLocal);

        try (ContextPropagator.Scope ignored = propagator.restoreLocal(captured)) {
            assertTrue(TenantHelper.isIgnoring());
        }
        assertFalse(TenantHelper.isIgnoring());
    }

    @Test
    @DisplayName("跨服务不采信跨线程的值：伪造的未签名租户被忽略")
    void remoteIgnoresLocalKeys() {
        Map<String, String> forged = Map.of(TenantPropagator.LOCAL_TENANT_KEY, "evil", TenantPropagator.LOCAL_IGNORE_KEY, "1");

        try (ContextPropagator.Scope ignored = propagator.restore(forged)) {
            assertNull(TenantHelper.currentTenantId());
            assertFalse(TenantHelper.isIgnoring());
        }
    }

    /**
     * 可以拨快的时钟
     */
    private static final class MovableClock extends Clock {

        private Instant now = Instant.now();

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

}
