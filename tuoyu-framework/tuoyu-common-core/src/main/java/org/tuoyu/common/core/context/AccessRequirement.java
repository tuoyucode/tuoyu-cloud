package org.tuoyu.common.core.context;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 当前接口声明的访问要求：接口上的权限码与角色（例如 Sa-Token 的 {@code @SaCheckPermission}）。
 * <p>
 * 数据权限用它缩小参与计算的角色：只按“授予了这些权限码或角色”的角色计算数据范围。
 *
 * @param permissions 权限码
 * @param roleKeys    角色
 * @author tuoyu
 */
public record AccessRequirement(Set<String> permissions, Set<String> roleKeys) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 没有声明访问要求
     */
    public static final AccessRequirement NONE = new AccessRequirement(Set.of(), Set.of());

    public AccessRequirement {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        roleKeys = roleKeys == null ? Set.of() : Set.copyOf(roleKeys);
    }

    /**
     * @return 是否声明了权限码或角色
     */
    public boolean constrained() {
        return !permissions.isEmpty() || !roleKeys.isEmpty();
    }

}
