package org.tuoyu.common.core.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前用户的数据：由 {@link UserContextProvider} 的实现填充，框架各模块经 {@link UserContext} 读取。
 * <p>
 * 集合字段默认为空集合而不是 null。
 *
 * @author tuoyu
 */
@Data
public class CurrentUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
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
     * 用户类型（{@code UserType} 的标识）
     */
    private String userType;

    /**
     * 部门 ID
     */
    private Long deptId;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 部门类别
     */
    private String deptCategory;

    /**
     * 所属租户
     */
    private String tenantId;

    /**
     * 是否为超级管理员
     */
    private boolean superAdmin;

    /**
     * 登录使用的客户端
     */
    private String clientKey;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 角色
     */
    private List<CurrentRole> roles = List.of();

    /**
     * 角色权限字符串
     */
    private Set<String> roleKeys = Set.of();

    /**
     * 权限码
     */
    private Set<String> permissions = Set.of();

    /**
     * 权限码 → 拥有该权限的角色 ID
     */
    private Map<String, List<Long>> dataScopeRoleMap = Map.of();

}
