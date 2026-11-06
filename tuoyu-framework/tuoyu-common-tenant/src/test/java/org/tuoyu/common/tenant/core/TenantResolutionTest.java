package org.tuoyu.common.tenant.core;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.Pointcuts;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.annotation.TenantIgnore;
import org.tuoyu.common.tenant.cache.TenantAwareCacheManager;
import org.tuoyu.common.tenant.mybatis.TenantIgnoreAdvisor;
import org.tuoyu.common.tenant.mybatis.TuoyuTenantLineHandler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 当前租户的确定顺序、失败闭合与忽略租户（ADR 0006 第 3、4 节）
 */
@DisplayName("多租户：当前租户的确定、缺少租户时拒绝、忽略租户的注解")
class TenantResolutionTest {

    private final AtomicReference<CurrentUser> user = new AtomicReference<>();

    private final Map<Long, String> switched = new java.util.HashMap<>();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        TenantProperties disabled = new TenantProperties();
        new DefaultTenantContext(disabled, new StaticListableBeanFactory().getBeanProvider(UserContext.class), id -> null);
    }

    private DefaultTenantContext enable() {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("userContext", (UserContext) () -> Optional.ofNullable(user.get()));
        return new DefaultTenantContext(properties, beans.getBeanProvider(UserContext.class), switched::get);
    }

    private void login(long userId, String tenantId, boolean superAdmin) {
        CurrentUser current = new CurrentUser();
        current.setUserId(userId);
        current.setTenantId(tenantId);
        current.setSuperAdmin(superAdmin);
        user.set(current);
    }

    @Test
    @DisplayName("是否处于忽略租户的代码块中：只在开启多租户且在 TenantHelper.ignore 中时为 true")
    void ignoringState() {
        DefaultTenantContext context = enable();
        assertThat(context.ignoring()).isFalse();
        assertThat(TenantHelper.ignore(context::ignoring)).isTrue();
        assertThat(TenantHelper.dynamic("acme", context::ignoring)).isFalse();

        TenantProperties disabled = new TenantProperties();
        DefaultTenantContext off = new DefaultTenantContext(disabled, new StaticListableBeanFactory().getBeanProvider(UserContext.class), id -> null);
        assertThat(TenantHelper.ignore(off::ignoring)).isFalse();
    }

    @Test
    @DisplayName("当前租户：显式指定 > 超级管理员切换的租户 > 用户所属租户 > 默认租户；忽略租户时为 null")
    void resolutionOrder() {
        DefaultTenantContext context = enable();
        assertThat(context.currentTenantId()).as("没有登录用户").isNull();

        login(7L, "acme", false);
        assertThat(context.currentTenantId()).isEqualTo("acme");
        assertThat(TenantHelper.dynamic("other", context::currentTenantId)).isEqualTo("other");
        assertThat(TenantHelper.ignore(context::currentTenantId)).isNull();
        assertThat(TenantHelper.ignore(() -> TenantHelper.dynamic("nested", context::currentTenantId))).isEqualTo("nested");

        login(8L, " ", false);
        assertThat(context.currentTenantId()).isEqualTo("default");

        // 普通用户即使有切换记录也不生效
        switched.put(8L, "evil");
        assertThat(context.currentTenantId()).isEqualTo("default");

        login(1L, "default", true);
        assertThat(context.currentTenantId()).isEqualTo("default");
        switched.put(1L, "acme");
        assertThat(context.currentTenantId()).isEqualTo("acme");
    }

    @Test
    @DisplayName("同一请求中缓存解析结果；未开启多租户时为 null")
    void requestCache() {
        DefaultTenantContext context = enable();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        login(7L, "acme", false);
        assertThat(context.currentTenantId()).isEqualTo("acme");
        login(7L, "changed", false);
        assertThat(context.currentTenantId()).as("同一请求中不重复解析").isEqualTo("acme");

        TenantProperties disabled = new TenantProperties();
        DefaultTenantContext off = new DefaultTenantContext(disabled, new StaticListableBeanFactory().getBeanProvider(UserContext.class), id -> "x");
        assertThat(off.enabled()).isFalse();
        assertThat(off.currentTenantId()).isNull();
        assertThat(TenantHelper.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("SQL 改写：缺少租户或租户为空白时拒绝；表名忽略引号、库名与大小写；忽略租户时所有表都不改写")
    void tenantLineHandler() {
        enable();
        AtomicInteger loads = new AtomicInteger();
        Set<String> tables = new HashSet<>();
        TuoyuTenantLineHandler handler = new TuoyuTenantLineHandler(() -> {
            loads.incrementAndGet();
            return Set.copyOf(tables);
        });

        assertThatThrownBy(handler::getTenantId).isInstanceOf(ServiceException.class).hasMessageContaining("缺少租户上下文");
        assertThatThrownBy(() -> TenantHelper.dynamic(" ", handler::getTenantId)).isInstanceOf(ServiceException.class);
        assertThat(TenantHelper.dynamic("acme", handler::getTenantId).toString()).isEqualTo("'acme'");
        assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");

        // 表信息尚未加载完成（为空）时下次再查，不会永远缓存为空而放过隔离表
        assertThat(handler.ignoreTable("sys_user")).isTrue();
        tables.add("sys_user");
        assertThat(handler.ignoreTable("`SYS_USER`")).isFalse();
        assertThat(handler.ignoreTable("tuoyu_cloud.\"sys_user\"")).isFalse();
        assertThat(handler.ignoreTable("sys_menu")).isTrue();
        int loaded = loads.get();
        handler.ignoreTable("sys_user");
        assertThat(loads.get()).as("加载到表后缓存").isEqualTo(loaded);
        assertThat(TenantHelper.ignore(() -> handler.ignoreTable("sys_user"))).isTrue();
    }

    @TenantIgnore
    static class PlatformService {

        public String run() {
            return "ok";
        }

    }

    static class MixedService {

        @TenantIgnore
        public String ignored() {
            return "ok";
        }

        public String scoped() {
            return "ok";
        }

    }

    @Test
    @DisplayName("@TenantIgnore：类或方法上有注解时匹配，方法在忽略租户中执行；受检异常包装后抛出")
    void tenantIgnoreAdvisor() throws Throwable {
        enable();
        TenantIgnoreAdvisor advisor = new TenantIgnoreAdvisor();
        var pointcut = advisor.getPointcut();
        assertThat(Pointcuts.matches(pointcut, PlatformService.class.getMethod("run"), PlatformService.class)).isTrue();
        assertThat(Pointcuts.matches(pointcut, MixedService.class.getMethod("ignored"), MixedService.class)).isTrue();
        assertThat(Pointcuts.matches(pointcut, MixedService.class.getMethod("scoped"), MixedService.class)).isFalse();

        MethodInterceptor advice = (MethodInterceptor) advisor.getAdvice();
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.proceed()).thenAnswer(i -> TenantHelper.isIgnoring());
        assertThat(advice.invoke(invocation)).isEqualTo(true);
        assertThat(TenantHelper.isIgnoring()).isFalse();

        MethodInvocation failing = mock(MethodInvocation.class);
        when(failing.proceed()).thenThrow(new java.io.IOException("io"));
        assertThatThrownBy(() -> advice.invoke(failing)).isInstanceOf(IllegalStateException.class).hasRootCauseMessage("io");
        MethodInvocation runtime = mock(MethodInvocation.class);
        when(runtime.proceed()).thenThrow(new ServiceException("业务异常"));
        assertThatThrownBy(() -> advice.invoke(runtime)).isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("透传格式错误时拒绝；签名正确但时间在未来超出有效期同样拒绝")
    void propagatorRejectsMalformed() {
        enable();
        TenantPropagator propagator = new TenantPropagator("unit-test-signing-key", Duration.ofSeconds(60));
        assertThatThrownBy(() -> propagator.restore(Map.of(TenantPropagator.KEY, "no-separator")))
            .isInstanceOf(SecurityException.class);

        Clock future = Clock.fixed(Instant.now().plus(Duration.ofMinutes(10)), ZoneOffset.UTC);
        Map<String, String> fromFuture = TenantHelper.dynamic("acme",
            () -> new TenantPropagator("unit-test-signing-key", Duration.ofSeconds(60), future).capture());
        assertThatThrownBy(() -> propagator.restore(fromFuture)).isInstanceOf(SecurityException.class)
            .hasMessageContaining("过期");
        assertThat(propagator.name()).isEqualTo("tenant");
    }

    @Test
    @DisplayName("隔离的缓存：按租户读写、加载与删除，互不影响")
    void tenantCache() throws Exception {
        enable();
        TenantAwareCacheManager manager = new TenantAwareCacheManager(new ConcurrentMapCacheManager(), List.of("global:"));
        Cache cache = manager.getCache("sys_user_name");
        assertThat(cache.getName()).isEqualTo("sys_user_name");
        assertThat(cache.getNativeCache()).isNotNull();

        assertThat(TenantHelper.dynamic("a", () -> cache.get(1L, () -> "loaded-a"))).isEqualTo("loaded-a");
        assertThat(TenantHelper.dynamic("b", () -> cache.get(1L, () -> "loaded-b"))).isEqualTo("loaded-b");
        TenantHelper.dynamic("a", () -> cache.evict(1L));
        assertThat(TenantHelper.dynamic("a", () -> cache.get(1L))).isNull();
        assertThat(TenantHelper.dynamic("b", () -> cache.get(1L, String.class))).isEqualTo("loaded-b");

        TenantHelper.dynamic("b", cache::clear);
        assertThat(TenantHelper.dynamic("b", () -> cache.get(1L))).isNull();
        assertThat(manager.getCacheNames()).contains("sys_user_name");
        assertThat(manager.getCache("global:config")).isNotInstanceOf(cache.getClass());
    }

}
