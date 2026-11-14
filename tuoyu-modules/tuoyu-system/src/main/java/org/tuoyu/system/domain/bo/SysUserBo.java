package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tuoyu.common.core.xss.Xss;
import org.tuoyu.system.domain.SysUser;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户请求对象（新增、修改、列表条件）
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
@AutoMapper(target = SysUser.class, reverseConvertGenerate = false)
public class SysUserBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 部门 ID
     */
    private Long deptId;

    /**
     * 账号
     */
    @Xss(message = "用户账号不能包含脚本字符")
    @NotBlank(message = "用户账号不能为空")
    @Size(min = 2, max = 30, message = "用户账号长度必须在{min}到{max}个字符之间")
    private String userName;

    /**
     * 昵称
     */
    @Xss(message = "用户昵称不能包含脚本字符")
    @NotBlank(message = "用户昵称不能为空")
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过{max}个字符")
    private String nickName;

    /**
     * 用户类型
     */
    private String userType;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    /**
     * 手机号
     */
    private String phoneNumber;

    /**
     * 性别
     */
    private String gender;

    /**
     * 口令（新增时由接口层哈希后传入；修改用户时不写入）
     */
    private String password;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 角色：修改时 null 表示不修改，空数组表示清空
     */
    private Long[] roleIds;

    /**
     * 岗位：修改时 null 表示不修改，空数组表示清空
     */
    private Long[] postIds;

    /**
     * 角色 ID（已分配、未分配用户列表的条件）
     */
    private Long roleId;

    /**
     * 只查这些用户（逗号分隔的用户 ID）
     */
    private String userIds;

    /**
     * 排除这些用户（逗号分隔的用户 ID）
     */
    private String excludeUserIds;

    /**
     * 创建者：只供服务内部使用（注册时没有登录用户），不取请求中的值
     */
    private Long createBy;

    /**
     * 更新者：只供服务内部使用，不取请求中的值
     */
    private Long updateBy;

    /**
     * 请求参数（创建时间区间 beginTime、endTime）
     */
    private Map<String, Object> params = new HashMap<>();

}
