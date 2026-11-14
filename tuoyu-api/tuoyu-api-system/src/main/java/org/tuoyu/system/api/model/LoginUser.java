package org.tuoyu.system.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录用户：登录时由系统模块组装，保存在 Sa-Token 会话中（Redis），并经跨模块调用传递。
 * <p>
 * 会话与跨服务传递都按字段序列化，字段名、类型与顺序是对外契约。
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 登录标识中用户类型与用户 ID 之间的分隔符
     */
    public static final String LOGIN_ID_SEPARATOR = "-";

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 租户编号
     */
    private String tenantId;

    /**
     * 部门 ID
     */
    private Long deptId;

    /**
     * 部门类别编码
     */
    private String deptCategory;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 令牌
     */
    private String token;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 登录时间（毫秒）
     */
    private Long loginTime;

    /**
     * 过期时间（毫秒）
     */
    private Long expireTime;

    /**
     * 登录 IP
     */
    private String ipaddr;

    /**
     * 登录地点
     */
    private String loginLocation;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String os;

    /**
     * 权限码
     */
    private Set<String> menuPermission;

    /**
     * 角色标识
     */
    private Set<String> rolePermission;

    /**
     * 账号
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 口令哈希：只在口令登录时带出，用于校验口令
     */
    private String password;

    /**
     * 正常状态的角色
     */
    private List<RoleDTO> roles;

    /**
     * 权限码 → 授予它的角色 ID（数据权限按接口的访问要求选取角色）
     */
    private Map<String, List<Long>> dataScopeRoleMap;

    /**
     * 岗位
     */
    private List<PostDTO> posts;

    /**
     * 当前使用的角色 ID
     */
    private Long roleId;

    /**
     * 客户端标识
     */
    private String clientKey;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 登录标识：用户类型与用户 ID 用 {@value #LOGIN_ID_SEPARATOR} 连接
     *
     * @return 登录标识
     * @throws IllegalArgumentException 用户类型或用户 ID 为空时
     */
    public String getLoginId() {
        if (userType == null) {
            throw new IllegalArgumentException("用户类型不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return userType + LOGIN_ID_SEPARATOR + userId;
    }

}
