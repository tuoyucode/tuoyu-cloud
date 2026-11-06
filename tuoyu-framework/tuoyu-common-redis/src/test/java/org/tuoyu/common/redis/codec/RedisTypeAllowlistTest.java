package org.tuoyu.common.redis.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RedisTypeAllowlist 单元测试")
class RedisTypeAllowlistTest {

    private final RedisTypeAllowlist allowlist = new RedisTypeAllowlist(List.of());

    @ParameterizedTest
    @ValueSource(strings = {
        "org.tuoyu.system.api.model.LoginUser",
        "cn.dev33.satoken.session.SaSession",
        "cn.dev33.satoken.session.SaTerminalInfo",
        "java.util.ArrayList",
        "java.util.concurrent.ConcurrentHashMap",
        "java.util.ImmutableCollections$List12",
        "java.util.Collections$EmptyList",
        "java.lang.Long",
        "java.util.UUID",
        "java.sql.Timestamp",
        "java.time.LocalDate",
        "org.redisson.spring.cache.NullValue",
        "com.alibaba.fastjson2.JSONObject"})
    @DisplayName("放行清单内的类型")
    void shouldAllowKnownTypes(String className) {
        assertTrue(allowlist.isAllowed(className));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "com.example.untrusted.UntrustedGadget",
        "java.util.logging.FileHandler",
        "java.util.concurrent.ThreadPoolExecutor",
        "java.net.URLClassLoader",
        "org.springframework.beans.factory.config.PropertyPathFactoryBean",
        "com.sun.rowset.JdbcRowSetImpl",
        "cn.dev33.satoken.dao.SaTokenDaoDefaultImpl",
        "org.redisson.Redisson",
        ""})
    @DisplayName("拒绝清单外的类型（包括同一组织下未放行的包）")
    void shouldDenyUnknownTypes(String className) {
        assertFalse(allowlist.isAllowed(className));
    }

    @Test
    @DisplayName("数组：基本类型数组放行，对象数组按元素类型判断")
    void shouldCheckArrayComponents() {
        assertTrue(allowlist.isAllowed("[B"));
        assertTrue(allowlist.isAllowed("[[J"));
        assertTrue(allowlist.isAllowed("[Ljava.lang.Long;"));
        // Object[] 的元素会逐个校验，数组本身放行；但 java.lang.Object 本身不在白名单中
        assertTrue(allowlist.isAllowed("[Ljava.lang.Object;"));
        assertFalse(allowlist.isAllowed("java.lang.Object"));
        assertTrue(allowlist.isAllowed("byte[]"));
        assertTrue(allowlist.isAllowed("java.lang.Long[]"));
        assertFalse(allowlist.isAllowed("[Lcom.sun.rowset.JdbcRowSetImpl;"));
        assertFalse(allowlist.isAllowed("[[Lcom.example.untrusted.UntrustedGadget;"));
        assertFalse(allowlist.isAllowed("com.example.untrusted.UntrustedGadget[]"));
        assertFalse(allowlist.isAllowed("[X"));
    }

    @Test
    @DisplayName("业务扩展前缀生效")
    void shouldAllowExtensionPrefixes() {
        RedisTypeAllowlist extended = new RedisTypeAllowlist(List.of(" com.acme.order. ", ""));

        assertTrue(extended.isAllowed("com.acme.order.OrderVo"));
        assertFalse(extended.isAllowed("com.acme.other.Thing"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"com", "com.", "java.util.", "javax.naming.", "org.springframework.beans.",
        "org.apache.commons.", "com.sun.rowset.", "com.m", "tools.jackson.databind."})
    @DisplayName("过于宽泛或指向受限根包的扩展前缀，启动时直接报错")
    void shouldRejectUnsafeExtensionPrefixes(String prefix) {
        assertThrows(IllegalArgumentException.class, () -> new RedisTypeAllowlist(List.of(prefix)));
    }

    @Test
    @DisplayName("合理的扩展前缀不报错")
    void shouldAcceptReasonableExtensionPrefixes() {
        assertDoesNotThrow(() -> new RedisTypeAllowlist(List.of("com.acme.", "cn.example.crm.Customer")));
    }

}
