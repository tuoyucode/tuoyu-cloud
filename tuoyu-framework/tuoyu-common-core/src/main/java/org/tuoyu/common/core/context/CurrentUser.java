package org.tuoyu.common.core.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前用户：业务代码与框架各模块（数据权限、审计字段填充、多租户、操作日志等）需要的用户信息。
 * <p>
 * 由 {@link UserContextProvider} 的实现（例如基于 Sa-Token 的实现）从登录会话中得到，通过 {@link UserContext} 获取。
 * 框架模块只依赖本类，不依赖具体的认证框架与 tuoyu-api-system 中的 LoginUser。
 *
 * @author tuoyu
 */
@Data
public class CurrentUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    /**
     * 登录账号
     */
    private String userName;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 用户类型（{@code UserType} 的标识，例如 sys_user）
     */
    private String userType;

    private Long deptId;

    private String deptName;

    /**
     * 部门类别编码
     */
    private String deptCategory;

    /**
     * 所属租户；未开启多租户时为默认租户
     */
    private String tenantId;

    /**
     * 是否超级管理员（不受数据权限限制）
     */
    private boolean superAdmin;

    /**
     * 登录使用的客户端
     */
    private String clientKey;

    /**
     * 登录的设备类型
     */
    private String deviceType;

    /**
     * 登录时的浏览器
     */
    private String browser;

    /**
     * 登录时的操作系统
     */
    private String os;

    private List<CurrentRole> roles = List.of();

    /**
     * 角色权限字符串（角色的 roleKey）
     */
    private Set<String> roleKeys = Set.of();

    /**
     * 菜单权限码
     */
    private Set<String> permissions = Set.of();

    /**
     * 权限码 → 拥有该权限的角色 ID（数据权限按接口所需权限缩小角色范围时使用）
     */
    private Map<String, List<Long>> dataScopeRoleMap = Map.of();

}
