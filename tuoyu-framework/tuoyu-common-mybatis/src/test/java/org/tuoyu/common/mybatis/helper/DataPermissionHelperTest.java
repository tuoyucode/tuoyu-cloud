package org.tuoyu.common.mybatis.helper;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("DataPermissionHelper 单元测试")
class DataPermissionHelperTest {

    /**
     * 清理数据权限测试使用的线程变量和 MyBatis-Plus 忽略策略。
     */
    @AfterEach
    void clearThreadContext() {
        DataPermissionHelper.removePermission();
        InterceptorIgnoreHelper.clearIgnoreStrategy();
    }

    /**
     * 验证 Mapper 数据权限注解可以在线程内设置、读取并清理。
     */
    @Test
    @DisplayName("管理数据权限注解线程变量")
    void shouldManagePermissionThreadLocal() {
        DataPermission permission = mock(DataPermission.class);

        DataPermissionHelper.setPermission(permission);
        assertSame(permission, DataPermissionHelper.getPermission());

        DataPermissionHelper.removePermission();
        assertNull(DataPermissionHelper.getPermission());
    }

    /**
     * 验证数据权限访问上下文根据接口权限或角色集合判断是否存在约束。
     */
    @Test
    @DisplayName("判断数据权限访问约束")
    void shouldDetectDataPermissionConstraints() {
        assertFalse(AccessRequirement.NONE.constrained());
        assertTrue(new AccessRequirement(Set.of("system:user:list"), Set.of()).constrained());
        assertTrue(new AccessRequirement(Set.of(), Set.of("admin")).constrained());
    }

    /**
     * 跨服务透传：调用方处于忽略数据权限的代码块中、接口有访问要求时，提供方恢复同样的状态，结束后还原
     */
    @Test
    @DisplayName("透传忽略状态与访问要求，结束后还原")
    void shouldPropagateIgnoreAndAccess() {
        PermissionContext permissionContext = () -> new AccessRequirement(Set.of("system:user:list"), Set.of("admin"));
        DataPermissionPropagator propagator = new DataPermissionPropagator(permissionContext);

        Map<String, String> captured = DataPermissionHelper.ignore(propagator::capture);
        assertEquals("true", captured.get(DataPermissionPropagator.IGNORE));
        assertEquals("system:user:list", captured.get(DataPermissionPropagator.PERMISSIONS));
        assertEquals("admin", captured.get(DataPermissionPropagator.ROLES));

        assertFalse(DataPermissionHelper.isIgnoring());
        try (ContextPropagator.Scope ignored = propagator.restore(captured)) {
            assertTrue(DataPermissionHelper.isIgnoring());
            assertEquals(Set.of("system:user:list"), DataPermissionHelper.getAccess().permissions());
        }
        assertFalse(DataPermissionHelper.isIgnoring());
        assertNull(DataPermissionHelper.getAccess());
    }

    /**
     * 调用方没有忽略数据权限、也没有访问要求时，不透传任何值；提供方按“没有访问要求”处理
     */
    @Test
    @DisplayName("没有可透传的状态时不透传")
    void shouldCaptureNothingWhenUnconstrained() {
        DataPermissionPropagator propagator = new DataPermissionPropagator(() -> AccessRequirement.NONE);

        assertTrue(propagator.capture().isEmpty());
        try (ContextPropagator.Scope ignored = propagator.restore(Map.of())) {
            assertFalse(DataPermissionHelper.isIgnoring());
            assertFalse(DataPermissionHelper.getAccess().constrained());
        }
    }

    /**
     * 验证嵌套忽略数据权限会在内部保持忽略状态，并在退出后恢复原始线程状态。
     */
    @Test
    @DisplayName("嵌套忽略并恢复数据权限")
    void shouldNestAndRestoreDataPermissionIgnoreState() {
        assertFalse(InterceptorIgnoreHelper.willIgnoreDataPermission("test.select"));

        String result = DataPermissionHelper.ignore(() -> {
            assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission("test.select"));
            DataPermissionHelper.ignore(() ->
                assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission("test.select")));
            assertTrue(InterceptorIgnoreHelper.willIgnoreDataPermission("test.select"));
            return "done";
        });

        assertEquals("done", result);
        assertFalse(InterceptorIgnoreHelper.willIgnoreDataPermission("test.select"));
    }

    /**
     * 验证忽略数据权限的业务代码抛出异常时仍会在 finally 中恢复线程状态。
     */
    @Test
    @DisplayName("异常后恢复数据权限忽略状态")
    void shouldRestoreIgnoreStateAfterException() {
        assertThrows(IllegalStateException.class, () -> DataPermissionHelper.ignore(() -> {
            throw new IllegalStateException("failed");
        }));

        assertFalse(InterceptorIgnoreHelper.willIgnoreDataPermission("test.select"));
    }
}
