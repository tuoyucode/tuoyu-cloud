package org.tuoyu.system.domain.bo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tuoyu.common.core.constant.RegexConstants;
import org.tuoyu.common.core.xss.Xss;
import org.tuoyu.common.sensitive.annotation.Sensitive;
import org.tuoyu.common.sensitive.core.SensitiveStrategy;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 个人中心修改资料的请求对象：字段为 null 时不修改，空串表示清空
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
public class SysUserProfileBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 昵称
     */
    @Xss(message = "用户昵称不能包含脚本字符")
    @Size(min = 0, max = 30, message = "用户昵称长度不能超过{max}个字符")
    private String nickName;

    /**
     * 邮箱
     */
    @Sensitive(strategy = SensitiveStrategy.EMAIL)
    @Email(message = "邮箱格式不正确")
    @Size(min = 0, max = 50, message = "邮箱长度不能超过{max}个字符")
    private String email;

    /**
     * 手机号（空串表示清空）
     */
    @Sensitive(strategy = SensitiveStrategy.PHONE)
    @Pattern(regexp = "^$|" + RegexConstants.MOBILE, message = "手机号格式不正确")
    private String phoneNumber;

    /**
     * 性别
     */
    private String gender;

    /**
     * 请求参数
     */
    private Map<String, Object> params = new HashMap<>();

}
