package org.tuoyu.common.mybatis.helper;

import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.PermissionContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 透传数据权限状态：调用方是否处于“忽略数据权限”的代码块中，以及调用方接口的访问要求。
 * <p>
 * 单体中跨模块调用在同一线程执行，这两项天然生效；微服务中由本类透传，使两种方式下提供方的数据权限一致。
 * 当前用户不透传，提供方从登录令牌重新读取。
 *
 * @author tuoyu
 */
public class DataPermissionPropagator implements ContextPropagator {

    static final String IGNORE = "dataPermission.ignore";
    static final String PERMISSIONS = "dataPermission.permissions";
    static final String ROLES = "dataPermission.roles";

    private final PermissionContext permissionContext;

    /**
     * @param permissionContext 权限上下文，可以为 null（没有认证模块时）
     */
    public DataPermissionPropagator(PermissionContext permissionContext) {
        this.permissionContext = permissionContext;
    }

    @Override
    public String name() {
        return "dataPermission";
    }

    @Override
    public Map<String, String> capture() {
        Map<String, String> values = new HashMap<>();
        if (DataPermissionHelper.isIgnoring()) {
            values.put(IGNORE, "true");
        }
        AccessRequirement access = DataPermissionHelper.getAccess();
        if (access == null && permissionContext != null) {
            access = permissionContext.currentRequirement();
        }
        if (access != null && access.constrained()) {
            values.put(PERMISSIONS, String.join(",", access.permissions()));
            values.put(ROLES, String.join(",", access.roleKeys()));
        }
        return values;
    }

    @Override
    public Scope restore(Map<String, String> values) {
        boolean ignore = "true".equals(values.get(IGNORE));
        AccessRequirement previous = DataPermissionHelper.getAccess();
        AccessRequirement access = new AccessRequirement(split(values.get(PERMISSIONS)), split(values.get(ROLES)));
        DataPermissionHelper.setAccess(access);
        if (ignore) {
            DataPermissionIgnoreContext.enable();
        }
        return () -> {
            if (ignore) {
                DataPermissionIgnoreContext.disable();
            }
            DataPermissionHelper.setAccess(previous);
        };
    }

    private static Set<String> split(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
