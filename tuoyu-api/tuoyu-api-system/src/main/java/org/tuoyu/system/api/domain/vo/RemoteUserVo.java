package org.tuoyu.system.api.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 跨模块传递的用户（不含口令）
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class RemoteUserVo implements Serializable {

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
     * 状态（0 正常 1 停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
