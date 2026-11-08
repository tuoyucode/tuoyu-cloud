package org.tuoyu.common.tenant.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * 租户的显式设置与忽略，按代码块嵌套：
 * <ul>
 *     <li>{@link #dynamic(String, Supplier)}：代码块中在指定租户执行（登录、{@code @TenantJob}、平台管理）；</li>
 *     <li>{@link #ignore(Supplier)}：代码块中忽略租户隔离（SQL 不加租户条件）。</li>
 * </ul>
 * 未开启多租户时两者都只是直接执行代码块。当前租户由 {@link #currentTenantId()} 给出（显式设置优先，其次是平台管理员切换的租户、登录用户所属的租户）。
 *
 * @author tuoyu
 */
public final class TenantHelper {

    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private static volatile DefaultTenantContext context;

    private TenantHelper() {
    }

    static void install(DefaultTenantContext tenantContext) {
        context = tenantContext;
    }

    /**
     * @return 是否开启了多租户
     */
    public static boolean isEnabled() {
        DefaultTenantContext current = context;
        return current != null && current.enabled();
    }

    /**
     * @return 当前租户；未开启多租户、处于忽略租户的代码块中或无法确定时为 null
     */
    public static String currentTenantId() {
        DefaultTenantContext current = context;
        return current == null ? null : current.currentTenantId();
    }

    /**
     * 在指定租户中执行
     */
    public static <T> T dynamic(String tenantId, Supplier<T> action) {
        return within(new Frame(tenantId, false), action);
    }

    /**
     * 在指定租户中执行
     */
    public static void dynamic(String tenantId, Runnable action) {
        within(new Frame(tenantId, false), () -> {
            action.run();
            return null;
        });
    }

    /**
     * 忽略租户隔离执行
     */
    public static <T> T ignore(Supplier<T> action) {
        return within(new Frame(null, true), action);
    }

    /**
     * 忽略租户隔离执行
     */
    public static void ignore(Runnable action) {
        within(new Frame(null, true), () -> {
            action.run();
            return null;
        });
    }

    /**
     * @return 当前是否处于忽略租户隔离的代码块中
     */
    public static boolean isIgnoring() {
        Frame frame = FRAMES.get().peek();
        return frame != null && frame.ignore();
    }

    /**
     * @return 代码块中显式指定的租户；没有时为 null
     */
    static String explicitTenantId() {
        Frame frame = FRAMES.get().peek();
        return frame == null || frame.ignore() ? null : frame.tenantId();
    }

    /**
     * 进入一个代码块（供透传恢复使用），返回的句柄退出代码块
     */
    static AutoCloseable enter(String tenantId, boolean ignore) {
        Deque<Frame> frames = FRAMES.get();
        frames.push(new Frame(tenantId, ignore));
        return () -> exit(frames);
    }

    private static <T> T within(Frame frame, Supplier<T> action) {
        Deque<Frame> frames = FRAMES.get();
        frames.push(frame);
        try {
            return action.get();
        } finally {
            exit(frames);
        }
    }

    private static void exit(Deque<Frame> frames) {
        frames.pop();
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
    }

    /**
     * @param tenantId 显式指定的租户
     * @param ignore   是否忽略租户隔离
     */
    private record Frame(String tenantId, boolean ignore) {
    }

}
