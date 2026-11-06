package org.tuoyu.common.tenant.core;

import org.tuoyu.common.core.context.ContextPropagator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 透传租户（ADR 0006 第 6 节）：透传当前租户（显式指定的、超级管理员切换的或登录用户所属的）与“忽略租户”标记，
 * 使异步线程与其他服务在同一租户中执行。
 * <p>
 * 跨服务：值为 {@code 租户|忽略标记|时间戳|签名}，签名为 HMAC-SHA256（密钥 TUOYU_TENANT_CONTEXT_SECRET）。
 * <b>提供方验签通过才采信</b>：签名不对、超过有效期都拒绝本次调用，防止伪造租户越权。
 * <p>
 * 跨线程：值只在本进程内存中传递，不签名、不设有效期（{@code @Async} 任务排队超过有效期也能执行）。
 * 两种值使用不同的键，跨服务收到的跨线程键不会被采信。
 *
 * @author tuoyu
 */
public class TenantPropagator implements ContextPropagator {

    static final String KEY = "tenant.context";

    static final String LOCAL_TENANT_KEY = "tenant.local.id";

    static final String LOCAL_IGNORE_KEY = "tenant.local.ignore";

    private final byte[] secret;

    private final Duration ttl;

    private final Clock clock;

    public TenantPropagator(String secret, Duration ttl) {
        this(secret, ttl, Clock.systemUTC());
    }

    TenantPropagator(String secret, Duration ttl, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("开启多租户时必须配置 tuoyu.tenant.context-secret（TUOYU_TENANT_CONTEXT_SECRET），用于签名跨服务透传的租户");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "tenant";
    }

    @Override
    public Map<String, String> capture() {
        boolean ignore = TenantHelper.isIgnoring();
        // 透传解析后的当前租户：异步线程中没有登录请求，取不到用户所属的租户
        String tenantId = ignore ? null : TenantHelper.currentTenantId();
        if (!ignore && tenantId == null) {
            return Map.of();
        }
        String payload = (tenantId == null ? "" : tenantId) + "|" + (ignore ? "1" : "0") + "|" + clock.millis();
        return Map.of(KEY, payload + "|" + sign(payload));
    }

    @Override
    public Scope restore(Map<String, String> values) {
        String value = values.get(KEY);
        if (value == null) {
            return Scope.NOOP;
        }
        int lastSeparator = value.lastIndexOf('|');
        if (lastSeparator < 0) {
            throw new SecurityException("透传的租户上下文格式错误");
        }
        String payload = value.substring(0, lastSeparator);
        String signature = value.substring(lastSeparator + 1);
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("透传的租户上下文签名错误，拒绝调用");
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 3) {
            throw new SecurityException("透传的租户上下文格式错误");
        }
        long issuedAt = Long.parseLong(parts[2]);
        if (Math.abs(clock.millis() - issuedAt) > ttl.toMillis()) {
            throw new SecurityException("透传的租户上下文已过期，拒绝调用");
        }
        AutoCloseable entered = TenantHelper.enter(parts[0].isEmpty() ? null : parts[0], "1".equals(parts[1]));
        return scope(entered);
    }

    @Override
    public Map<String, String> captureLocal() {
        if (TenantHelper.isIgnoring()) {
            return Map.of(LOCAL_IGNORE_KEY, "1");
        }
        String tenantId = TenantHelper.currentTenantId();
        return tenantId == null ? Map.of() : Map.of(LOCAL_TENANT_KEY, tenantId);
    }

    @Override
    public Scope restoreLocal(Map<String, String> values) {
        boolean ignore = "1".equals(values.get(LOCAL_IGNORE_KEY));
        String tenantId = values.get(LOCAL_TENANT_KEY);
        if (!ignore && tenantId == null) {
            return Scope.NOOP;
        }
        return scope(TenantHelper.enter(ignore ? null : tenantId, ignore));
    }

    private static Scope scope(AutoCloseable entered) {
        return () -> {
            try {
                entered.close();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}
