package org.tuoyu.common.mybatis.helper;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.mybatis.annotation.DataPermission;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据权限的线程上下文：当前正在执行的方法的 {@link DataPermission} 注解、显式设置的访问要求，以及“忽略数据权限”的代码块。
 * <ul>
 *     <li>当前注解保存在线程变量中，由数据权限切面在调用标注的方法期间设置、结束后恢复上一层；</li>
 *     <li>数据权限上下文（{@link #getContext()}）在 Web 请求中保存在请求属性里（请求结束即失效），
 *     不在请求中时（RPC 提供方、后台线程）保存在线程变量里；访问要求保存在其中；</li>
 *     <li>忽略数据权限借用 MyBatis-Plus 的线程级忽略策略，与租户等其他忽略项共存，结束后恢复进入前的状态。</li>
 * </ul>
 * 全部状态都在线程变量或请求属性中，不跨线程共享；跨线程、跨服务的透传由 {@link DataPermissionPropagator} 负责。
 *
 * @author tuoyu
 */
public final class DataPermissionHelper {

    /**
     * 请求属性中保存数据权限上下文的属性名
     */
    private static final String CONTEXT_ATTRIBUTE = DataPermissionHelper.class.getName() + ".context";

    /**
     * 上下文中访问要求的键
     */
    private static final String ACCESS_KEY = "access";

    private static final ThreadLocal<DataPermission> PERMISSION = new ThreadLocal<>();

    private static final ThreadLocal<Map<String, Object>> THREAD_CONTEXT = new ThreadLocal<>();

    private DataPermissionHelper() {
    }

    /**
     * @return 当前线程中正在执行的方法的注解；没有时为 null
     */
    public static DataPermission getPermission() {
        return PERMISSION.get();
    }

    /**
     * @param permission 注解，可以为 null（等同于 {@link #removePermission()}）
     */
    public static void setPermission(DataPermission permission) {
        if (permission == null) {
            PERMISSION.remove();
        } else {
            PERMISSION.set(permission);
        }
    }

    /**
     * 清除当前线程中的注解
     */
    public static void removePermission() {
        PERMISSION.remove();
    }

    /**
     * 数据权限上下文（可修改）。在 Web 请求中保存在请求属性里，第一次取时创建，同一个请求中多次取得到同一个对象；
     * 不在请求中时保存在线程变量里。
     *
     * @return 上下文
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            Map<String, Object> context = THREAD_CONTEXT.get();
            if (context == null) {
                context = new HashMap<>();
                THREAD_CONTEXT.set(context);
            }
            return context;
        }
        Object context = attributes.getAttribute(CONTEXT_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (context instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new HashMap<>();
        attributes.setAttribute(CONTEXT_ATTRIBUTE, created, RequestAttributes.SCOPE_REQUEST);
        return created;
    }

    /**
     * @return 上下文中显式设置的访问要求（RPC 提供方由调用方透传）；没有设置时为 null
     */
    public static AccessRequirement getAccess() {
        return getContext().get(ACCESS_KEY) instanceof AccessRequirement access ? access : null;
    }

    /**
     * @param access 访问要求；为 null 时删除
     */
    public static void setAccess(AccessRequirement access) {
        Map<String, Object> context = getContext();
        if (access != null) {
            context.put(ACCESS_KEY, access);
            return;
        }
        context.remove(ACCESS_KEY);
        // 线程变量中的上下文空了就删除，线程池中的线程不长期带着它
        if (context.isEmpty() && context == THREAD_CONTEXT.get()) {
            THREAD_CONTEXT.remove();
        }
    }

    /**
     * 当前线程是否处于忽略数据权限的状态：MyBatis-Plus 的线程级忽略策略存在且其中的数据权限为 true。
     * 不看 Mapper 上的 {@code @InterceptorIgnore}，那是按语句判断的。
     *
     * @return 是否忽略
     */
    public static boolean isIgnoring() {
        return DataPermissionIgnoreContext.isIgnoring();
    }

    /**
     * 在忽略数据权限的状态中执行，结束后（包括抛出异常时）恢复进入前的状态；异常原样抛出，可以嵌套
     *
     * @param action 要执行的代码
     */
    public static void ignore(Runnable action) {
        DataPermissionIgnoreContext.enable();
        try {
            action.run();
        } finally {
            DataPermissionIgnoreContext.disable();
        }
    }

    /**
     * 在忽略数据权限的状态中执行，结束后（包括抛出异常时）恢复进入前的状态；异常原样抛出，可以嵌套
     *
     * @param action 要执行的代码
     * @param <T>    结果类型
     * @return 代码的结果
     */
    public static <T> T ignore(Supplier<T> action) {
        DataPermissionIgnoreContext.enable();
        try {
            return action.get();
        } finally {
            DataPermissionIgnoreContext.disable();
        }
    }

}
