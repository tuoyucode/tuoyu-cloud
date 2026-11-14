package org.tuoyu.common.mybatis.helper;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据权限上下文：当前注解、访问要求、忽略数据权限的代码块，以及跨线程、跨服务透传
 */
@DisplayName("DataPermissionHelper：注解、访问要求与忽略状态")
class DataPermissionHelperTest {

    private static final String ID = "org.example.UserMapper.selectList";

    @AfterEach
    void clearThreadContext() {
        DataPermissionHelper.removePermission();
        DataPermissionHelper.setAccess(null);
        DataPermissionHelper.getContext().clear();
        InterceptorIgnoreHelper.clearIgnoreStrategy();
        RequestContextHolder.resetRequestAttributes();
    }

    @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
    void annotated() {
    }

    private DataPermission permission() throws Exception {
        return getClass().getDeclaredMethod("annotated").getAnnotation(DataPermission.class);
    }

    @Test
    @DisplayName("当前注解按线程保存，设置、读取、清除")
    void permissionIsThreadLocal() throws Exception {
        DataPermission permission = permission();
        DataPermissionHelper.setPermission(permission);
        assertThat(DataPermissionHelper.getPermission()).isSameAs(permission);

        AtomicReference<DataPermission> other = new AtomicReference<>(permission);
        CompletableFuture.runAsync(() -> other.set(DataPermissionHelper.getPermission())).join();
        assertThat(other.get()).isNull();

        DataPermissionHelper.removePermission();
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    @Test
    @DisplayName("访问要求：设置后读取，传 null 清除；没有设置时为 null")
    void access() {
        assertThat(DataPermissionHelper.getAccess()).isNull();
        AccessRequirement access = new AccessRequirement(Set.of("system:user:list"), Set.of());
        DataPermissionHelper.setAccess(access);
        assertThat(DataPermissionHelper.getAccess()).isEqualTo(access);
        DataPermissionHelper.setAccess(null);
        assertThat(DataPermissionHelper.getAccess()).isNull();
    }

    @Test
    @DisplayName("请求中上下文保存在请求属性里（同一请求共用，换一个请求就没有了）；不在请求中时保存在线程里")
    void contextScope() {
        AccessRequirement access = new AccessRequirement(Set.of("a:b:c"), Set.of());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        DataPermissionHelper.setAccess(access);
        assertThat(DataPermissionHelper.getAccess()).isEqualTo(access);
        assertThat(DataPermissionHelper.getContext()).isSameAs(DataPermissionHelper.getContext());

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertThat(DataPermissionHelper.getAccess()).isNull();

        RequestContextHolder.resetRequestAttributes();
        assertThat(DataPermissionHelper.getAccess()).isNull();
        DataPermissionHelper.setAccess(access);
        assertThat(DataPermissionHelper.getAccess()).isEqualTo(access);
        AtomicReference<AccessRequirement> other = new AtomicReference<>(access);
        CompletableFuture.runAsync(() -> other.set(DataPermissionHelper.getAccess())).join();
        assertThat(other.get()).isNull();
    }

    @Test
    @DisplayName("忽略数据权限：代码块中生效（Runnable 与 Supplier 两种写法），结束后恢复")
    void ignore() {
        assertThat(DataPermissionHelper.isIgnoring()).isFalse();
        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isFalse();

        String result = DataPermissionHelper.ignore(() -> {
            assertThat(DataPermissionHelper.isIgnoring()).isTrue();
            assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isTrue();
            return "done";
        });
        assertThat(result).isEqualTo("done");

        AtomicReference<Boolean> inside = new AtomicReference<>();
        DataPermissionHelper.ignore(() -> inside.set(DataPermissionHelper.isIgnoring()));
        assertThat(inside.get()).isTrue();

        assertThat(DataPermissionHelper.isIgnoring()).isFalse();
        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isFalse();
        assertThat(InterceptorIgnoreHelper.hasIgnoreStrategy()).isFalse();
    }

    @Test
    @DisplayName("嵌套忽略：内层结束后外层仍然忽略，全部结束后恢复")
    void nestedIgnore() {
        DataPermissionHelper.ignore(() -> {
            DataPermissionHelper.ignore(() -> assertThat(DataPermissionHelper.isIgnoring()).isTrue());
            assertThat(DataPermissionHelper.isIgnoring()).isTrue();
            assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isTrue();
        });
        assertThat(DataPermissionHelper.isIgnoring()).isFalse();
        assertThat(InterceptorIgnoreHelper.hasIgnoreStrategy()).isFalse();
    }

    @Test
    @DisplayName("代码块抛出异常时同样恢复")
    void restoresAfterException() {
        assertThatThrownBy(() -> DataPermissionHelper.ignore(() -> {
            throw new IllegalStateException("failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("failed");
        assertThat(DataPermissionHelper.isIgnoring()).isFalse();
        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isFalse();
    }

    @Test
    @DisplayName("与其他忽略策略共存：已有的忽略项（例如租户）保留，数据权限只在代码块中忽略")
    void coexistsWithOtherIgnoreStrategy() {
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        DataPermissionHelper.ignore(() -> {
            assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isTrue();
            assertThat(InterceptorIgnoreHelper.willIgnoreTenantLine(ID)).isTrue();
        });
        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isFalse();
        assertThat(InterceptorIgnoreHelper.willIgnoreTenantLine(ID)).isTrue();
        assertThat(DataPermissionHelper.isIgnoring()).isFalse();

        // 外部已经忽略数据权限时，代码块结束后仍然忽略
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().dataPermission(true).build());
        DataPermissionHelper.ignore(() -> assertThat(DataPermissionHelper.isIgnoring()).isTrue());
        assertThat(DataPermissionHelper.isIgnoring()).isTrue();
        assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isTrue();
    }

    @Test
    @DisplayName("跨服务透传：调用方处于忽略数据权限的代码块中、接口有访问要求时，提供方恢复同样的状态，结束后还原")
    void propagateIgnoreAndAccess() {
        PermissionContext permissionContext = () -> new AccessRequirement(Set.of("system:user:list"), Set.of("admin"));
        DataPermissionPropagator propagator = new DataPermissionPropagator(permissionContext);

        Map<String, String> captured = DataPermissionHelper.ignore(propagator::capture);
        assertThat(captured).containsEntry("dataPermission.ignore", "true")
            .containsEntry("dataPermission.permissions", "system:user:list")
            .containsEntry("dataPermission.roles", "admin");

        assertThat(DataPermissionHelper.isIgnoring()).isFalse();
        try (ContextPropagator.Scope ignored = propagator.restore(captured)) {
            assertThat(DataPermissionHelper.isIgnoring()).isTrue();
            assertThat(InterceptorIgnoreHelper.willIgnoreDataPermission(ID)).isTrue();
            assertThat(DataPermissionHelper.getAccess().permissions()).containsExactly("system:user:list");
            assertThat(DataPermissionHelper.getAccess().roleKeys()).containsExactly("admin");
        }
        assertThat(DataPermissionHelper.isIgnoring()).isFalse();
        assertThat(DataPermissionHelper.getAccess()).isNull();
    }

    @Test
    @DisplayName("没有可透传的状态时不透传；提供方按没有访问要求处理")
    void captureNothingWhenUnconstrained() {
        DataPermissionPropagator propagator = new DataPermissionPropagator(() -> AccessRequirement.NONE);
        assertThat(propagator.capture()).isEmpty();
        try (ContextPropagator.Scope ignored = propagator.restore(Map.of())) {
            assertThat(DataPermissionHelper.isIgnoring()).isFalse();
            assertThat(DataPermissionHelper.getAccess().constrained()).isFalse();
        }
        assertThat(DataPermissionHelper.getAccess()).isNull();
    }

}
