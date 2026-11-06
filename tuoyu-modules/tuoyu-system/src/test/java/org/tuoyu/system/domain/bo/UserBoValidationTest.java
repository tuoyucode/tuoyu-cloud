package org.tuoyu.system.domain.bo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("用户与个人资料的参数校验")
class UserBoValidationTest {

    private static final ValidatorFactory FACTORY = Validation.byDefaultProvider().configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .buildValidatorFactory();

    private final Validator validator = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    @DisplayName("个人资料：手机号为空字符串（清空）与不传都通过，格式错误时拒绝")
    void profilePhoneCanBeCleared() {
        SysUserProfileBo profile = new SysUserProfileBo();
        assertTrue(validator.validateProperty(profile, "phoneNumber").isEmpty());
        profile.setPhoneNumber("");
        assertTrue(validator.validateProperty(profile, "phoneNumber").isEmpty());
        profile.setPhoneNumber("13800138000");
        assertTrue(validator.validateProperty(profile, "phoneNumber").isEmpty());
        profile.setPhoneNumber("12345");
        assertFalse(validator.validateProperty(profile, "phoneNumber").isEmpty());
        profile.setPhoneNumber(" ");
        assertFalse(validator.validateProperty(profile, "phoneNumber").isEmpty());
    }

    @Test
    @DisplayName("新增、修改用户：角色可以为空数组（修改时表示清空）")
    void userRolesCanBeEmpty() {
        SysUserBo user = new SysUserBo();
        user.setRoleIds(new Long[0]);
        assertTrue(validator.validateProperty(user, "roleIds").isEmpty());
        user.setRoleIds(null);
        assertTrue(validator.validateProperty(user, "roleIds").isEmpty());
    }

}
