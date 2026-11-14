package org.tuoyu.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.sensitive.annotation.Sensitive;
import org.tuoyu.common.sensitive.core.SensitiveStrategy;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.system.domain.SysUser;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图（用户管理）。联系方式按“修改用户”权限脱敏；口令哈希不参与 JSON
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysUser.class)
public class SysUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 账号
     */
    private String userName;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 邮箱
     */
    @Sensitive(strategy = SensitiveStrategy.EMAIL, perms = "system:user:edit")
    private String email;

    /**
     * 手机号
     */
    @Sensitive(strategy = SensitiveStrategy.PHONE, perms = "system:user:edit")
    private String phoneNumber;

    /**
     * 性别
     */
    private String gender;

    /**
     * 头像（对象存储 ID）
     */
    private Long avatar;

    /**
     * 头像地址
     */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "avatar")
    private String avatarUrl;

    /**
     * 口令哈希：只在服务内部校验口令时使用，既不输出也不从请求读取
     */
    @JsonIgnore
    private String password;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 最后登录 IP
     */
    private String loginIp;

    /**
     * 最后登录时间
     */
    private LocalDateTime loginDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 部门名称
     */
    @Translation(type = TransConstant.DEPT_ID_TO_NAME, mapper = "deptId")
    private String deptName;

    /**
     * 角色
     */
    private List<SysRoleVo> roles;

    /**
     * 角色 ID
     */
    private Long[] roleIds;

    /**
     * 岗位 ID
     */
    private Long[] postIds;

    /**
     * 当前角色 ID
     */
    private Long roleId;

}
