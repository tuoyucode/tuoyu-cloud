package org.tuoyu.common.mybatis.helper;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import org.tuoyu.common.core.utils.reflect.ReflectUtils;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

/**
 * 当前线程“忽略数据权限”的状态，借用 MyBatis-Plus 的线程级忽略策略（{@link InterceptorIgnoreHelper}）。
 * <p>
 * 一个线程只有一个忽略策略对象，租户等其他忽略项也在其中，所以要与已有的策略共存：
 * <ul>
 *     <li>{@link #enable()}：之后数据权限拦截器对任何语句都不改写。当前线程已有策略时，装入它的副本，副本中
 *     {@code dataPermission} 为 true、其他忽略项照抄；没有策略时装入一个只忽略数据权限的策略。
 *     不直接修改已有的策略对象：调用方可能把同一个策略对象交给多个线程，修改它会让其他线程同时忽略数据权限；</li>
 *     <li>{@link #disable()}：恢复对应的 {@code enable()} 之前的状态。之前没有策略时清除线程级策略，
 *     之前有策略时重新装入原来的对象（其中的忽略项，包括外层已经忽略的数据权限，都原样保留）。</li>
 * </ul>
 * 两个方法必须成对调用，可以嵌套：每层进入前的状态记在线程级的栈中，栈空时删除这个线程变量。
 * <p>
 * MyBatis-Plus 没有公开读取当前策略对象的方法，这里用反射读取 {@code InterceptorIgnoreHelper} 的私有静态字段
 * {@code IGNORE_STRATEGY_LOCAL}；升级 MyBatis-Plus 时由 {@code DataPermissionHelperTest} 守护。
 *
 * @author tuoyu
 */
final class DataPermissionIgnoreContext {

    private static final String STRATEGY_FIELD = "IGNORE_STRATEGY_LOCAL";

    /**
     * 每层 {@code enable()} 进入前的策略对象（没有策略时为 {@link #NO_STRATEGY}）
     */
    private static final ThreadLocal<Deque<Object>> PREVIOUS = new ThreadLocal<>();

    /**
     * 栈中表示“进入前没有策略”的标记（{@link ArrayDeque} 不能存 null）
     */
    private static final Object NO_STRATEGY = new Object();

    private DataPermissionIgnoreContext() {
    }

    /**
     * 进入忽略数据权限的状态
     */
    static void enable() {
        IgnoreStrategy current = currentStrategy();
        Deque<Object> stack = PREVIOUS.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            PREVIOUS.set(stack);
        }
        stack.push(current == null ? NO_STRATEGY : current);
        InterceptorIgnoreHelper.handle(current == null
            ? IgnoreStrategy.builder().dataPermission(true).build()
            : copyIgnoringDataPermission(current));
    }

    /**
     * 恢复对应的 {@link #enable()} 之前的状态；没有对应的 {@code enable()} 时不做任何事
     */
    static void disable() {
        Deque<Object> stack = PREVIOUS.get();
        if (stack == null || stack.isEmpty()) {
            PREVIOUS.remove();
            return;
        }
        Object previous = stack.pop();
        if (stack.isEmpty()) {
            PREVIOUS.remove();
        }
        if (previous == NO_STRATEGY) {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        } else {
            InterceptorIgnoreHelper.handle((IgnoreStrategy) previous);
        }
    }

    /**
     * @return 当前线程是否忽略数据权限（线程级策略中的 {@code dataPermission} 为 true）
     */
    static boolean isIgnoring() {
        IgnoreStrategy current = currentStrategy();
        return current != null && Boolean.TRUE.equals(current.getDataPermission());
    }

    private static IgnoreStrategy copyIgnoringDataPermission(IgnoreStrategy source) {
        return IgnoreStrategy.builder()
            .tenantLine(source.getTenantLine())
            .dynamicTableName(source.getDynamicTableName())
            .blockAttack(source.getBlockAttack())
            .illegalSql(source.getIllegalSql())
            .dataPermission(true)
            .others(source.getOthers() == null ? null : new HashMap<>(source.getOthers()))
            .build();
    }

    private static IgnoreStrategy currentStrategy() {
        Object value = StrategyLocal.LOCAL.get();
        return value instanceof IgnoreStrategy strategy ? strategy : null;
    }

    /**
     * 首次使用时才解析反射字段；找不到时明确报错（MyBatis-Plus 改了内部实现）
     */
    private static final class StrategyLocal {

        private static final ThreadLocal<?> LOCAL = resolve();

        private StrategyLocal() {
        }

        private static ThreadLocal<?> resolve() {
            Field field = ReflectUtils.getField(InterceptorIgnoreHelper.class, STRATEGY_FIELD);
            Object value = field == null ? null : ReflectUtils.getStaticFieldValue(field);
            if (value instanceof ThreadLocal<?> local) {
                return local;
            }
            throw new IllegalStateException("无法读取 MyBatis-Plus 的线程级忽略策略（InterceptorIgnoreHelper."
                + STRATEGY_FIELD + "），请检查 MyBatis-Plus 的版本");
        }

    }

}
