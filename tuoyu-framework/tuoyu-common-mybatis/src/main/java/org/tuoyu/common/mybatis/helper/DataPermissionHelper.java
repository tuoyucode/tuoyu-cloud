package org.tuoyu.common.mybatis.helper;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.utils.reflect.ReflectUtils;
import org.tuoyu.common.mybatis.annotation.DataPermission;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据权限助手
 * <p>
 * 数据权限上下文（SpEL 变量、访问要求）在请求中保存在请求属性里，请求结束即失效；
 * 不在请求中时（RPC 提供方、后台线程）保存在线程变量里，由 {@link DataPermissionPropagator} 恢复并在调用结束后清理。
 *
 * @author Lion Li
 * @version 3.5.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unchecked")
public class DataPermissionHelper {

    public static final String DATA_PERMISSION_KEY = "data:permission";
    private static final String ACCESS_KEY = "data:permission:access";

    private static final ThreadLocal<DataPermission> PERMISSION_CACHE = new ThreadLocal<>();

    /**
     * 不在请求中时使用的上下文
     */
    private static final ThreadLocal<Map<String, Object>> LOCAL_CONTEXT = ThreadLocal.withInitial(HashMap::new);

    /**
     * 获取当前执行mapper权限注解
     *
     * @return 返回当前执行mapper权限注解
     */
    public static DataPermission getPermission() {
        return PERMISSION_CACHE.get();
    }

    /**
     * 设置当前执行mapper权限注解
     *
     * @param dataPermission 数据权限注解
     */
    public static void setPermission(DataPermission dataPermission) {
        PERMISSION_CACHE.set(dataPermission);
    }

    /**
     * 删除当前执行mapper权限注解
     */
    public static void removePermission() {
        PERMISSION_CACHE.remove();
    }

    /**
     * 从上下文中获取指定键的变量值，并将其转换为指定的类型
     *
     * @param key 变量的键
     * @param <T> 变量值的类型
     * @return 指定键的变量值，如果不存在则返回 null
     */
    public static <T> T getVariable(String key) {
        Map<String, Object> context = getContext();
        return (T) context.get(key);
    }

    /**
     * 向上下文中设置指定键的变量值（SpEL 中以 {@code #key} 引用）。不会透传到其他服务
     *
     * @param key   要设置的变量的键
     * @param value 要设置的变量值
     */
    public static void setVariable(String key, Object value) {
        Map<String, Object> context = getContext();
        context.put(key, value);
    }

    /**
     * 获取显式设置的访问要求（RPC 提供方由调用方透传而来）；没有设置时为 null，由当前请求的接口决定
     *
     * @return 访问要求
     */
    public static AccessRequirement getAccess() {
        return getVariable(ACCESS_KEY);
    }

    /**
     * 设置访问要求；传 null 表示清除
     *
     * @param access 访问要求
     */
    public static void setAccess(AccessRequirement access) {
        if (access == null) {
            getContext().remove(ACCESS_KEY);
        } else {
            setVariable(ACCESS_KEY, access);
        }
    }

    /**
     * 获取数据权限上下文
     *
     * @return 请求中为请求属性中的 Map，否则为线程变量中的 Map
     */
    public static Map<String, Object> getContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return LOCAL_CONTEXT.get();
        }
        Object context = attributes.getAttribute(DATA_PERMISSION_KEY, RequestAttributes.SCOPE_REQUEST);
        if (context instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new HashMap<>();
        attributes.setAttribute(DATA_PERMISSION_KEY, created, RequestAttributes.SCOPE_REQUEST);
        return created;
    }

    /**
     * @return 当前线程是否处于忽略数据权限的代码块中
     */
    public static boolean isIgnoring() {
        Object local = ReflectUtils.getStaticFieldValue(ReflectUtils.getField(InterceptorIgnoreHelper.class, "IGNORE_STRATEGY_LOCAL"));
        return local instanceof ThreadLocal<?> threadLocal
            && threadLocal.get() instanceof IgnoreStrategy strategy
            && Boolean.TRUE.equals(strategy.getDataPermission());
    }

    /**
     * 在忽略数据权限中执行
     *
     * @param handle 处理执行方法
     */
    public static void ignore(Runnable handle) {
        DataPermissionIgnoreContext.enable();
        try {
            handle.run();
        } finally {
            DataPermissionIgnoreContext.disable();
        }
    }

    /**
     * 在忽略数据权限中执行
     *
     * @param handle 处理执行方法
     * @return 执行结果
     */
    public static <T> T ignore(Supplier<T> handle) {
        DataPermissionIgnoreContext.enable();
        try {
            return handle.get();
        } finally {
            DataPermissionIgnoreContext.disable();
        }
    }

}
