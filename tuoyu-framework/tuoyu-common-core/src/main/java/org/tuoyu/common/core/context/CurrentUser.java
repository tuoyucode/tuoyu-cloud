package org.tuoyu.common.core.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 当前用户：框架各模块（数据权限、审计字段填充、多租户等）需要的最小信息。
 * <p>
 * 由 {@link UserContext} 的实现（例如基于 Sa-Token 的实现）从登录会话中得到。框架模块只依赖本类，
 * 不依赖具体的认证框架与 tuoyu-api-system 中的 LoginUser。
 *
 * @author tuoyu
 */
@Data
public class CurrentUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String userName;

    private Long deptId;

    private String deptName;

    /**
     * 所属租户；未开启多租户时为默认租户
     */
    private String tenantId;

    /**
     * 是否超级管理员（不受数据权限限制）
     */
    private boolean superAdmin;

    private List<CurrentRole> roles = List.of();

    /**
     * 权限码 → 拥有该权限的角色 ID（数据权限按接口所需权限缩小角色范围时使用）
     */
    private Map<String, List<Long>> dataScopeRoleMap = Map.of();

}
