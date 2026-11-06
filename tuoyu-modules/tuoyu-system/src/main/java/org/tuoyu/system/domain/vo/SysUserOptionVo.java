package org.tuoyu.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.system.domain.SysUser;

import java.io.Serial;
import java.io.Serializable;

/**
 * 选择用户时的用户信息（工作流转办、委派、加签、抄送等）：只有识别用户所需的字段，
 * 不含手机号、邮箱等联系方式，所以只要登录即可查询
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysUser.class)
public class SysUserOptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户账号
     */
    private String userName;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 部门名称
     */
    @Translation(type = TransConstant.DEPT_ID_TO_NAME, mapper = "deptId")
    private String deptName;

}
