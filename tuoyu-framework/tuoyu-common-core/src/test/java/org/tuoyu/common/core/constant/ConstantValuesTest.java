package org.tuoyu.common.core.constant;

import cn.hutool.core.lang.RegexPool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("常量的取值")
class ConstantValuesTest {

    @Test
    @DisplayName("缓存名：Redis 缓存管理器按 名称#ttl 解析，取值变化会让已有缓存失效")
    void cacheNames() {
        assertThat(CacheNames.SYS_CONFIG).isEqualTo("sys_config");
        assertThat(CacheNames.SYS_DICT).isEqualTo("sys_dict");
        assertThat(CacheNames.SYS_DICT_TYPE).isEqualTo("sys_dict_type");
        assertThat(CacheNames.SYS_CLIENT).isEqualTo("global:sys_client#30d");
        assertThat(CacheNames.SYS_USER_NAME).isEqualTo("sys_user_name#30d");
        assertThat(CacheNames.SYS_NICKNAME).isEqualTo("sys_nickname#30d");
        assertThat(CacheNames.SYS_DEPT).isEqualTo("sys_dept#30d");
        assertThat(CacheNames.SYS_OSS).isEqualTo("sys_oss#30d");
        assertThat(CacheNames.SYS_ROLE_CUSTOM).isEqualTo("sys_role_custom#30d");
        assertThat(CacheNames.SYS_DEPT_AND_CHILD).isEqualTo("sys_dept_and_child#30d");
        assertThat(CacheNames.SYS_OSS_CONFIG).isEqualTo("global:sys_oss_config");
        assertThat(CacheNames.ONLINE_TOKEN_KEY).isEqualTo("online_tokens:");
        assertThat(CacheNames.PWD_ERR_CNT_KEY).isEqualTo("pwd_err_cnt:");
    }

    @Test
    @DisplayName("通用常量")
    void constants() {
        assertThat(Constants.WWW).isEqualTo("www.");
        assertThat(Constants.HTTP).isEqualTo("http://");
        assertThat(Constants.HTTPS).isEqualTo("https://");
        assertThat(Constants.SUCCESS).isEqualTo("0");
        assertThat(Constants.FAIL).isEqualTo("1");
        assertThat(Constants.LOGIN_SUCCESS).isEqualTo("Success");
        assertThat(Constants.LOGOUT).isEqualTo("Logout");
        assertThat(Constants.REGISTER).isEqualTo("Register");
        assertThat(Constants.LOGIN_FAIL).isEqualTo("Error");
        assertThat(Constants.CAPTCHA_EXPIRATION).isEqualTo(2);
        assertThat(Constants.TOP_PARENT_ID).isEqualTo(0L);
        assertThat(Constants.ENCRYPT_HEADER).isEqualTo("ENC_");
    }

    @Test
    @DisplayName("全局 Redis 键前缀")
    void globalKeys() {
        assertThat(GlobalConstants.GLOBAL_REDIS_KEY).isEqualTo("global:");
        assertThat(GlobalConstants.CAPTCHA_CODE_KEY).isEqualTo("global:captcha_codes:");
        assertThat(GlobalConstants.VERIFY_CODE_KEY).isEqualTo("global:verify_codes:");
        assertThat(GlobalConstants.VERIFY_ATTEMPT_KEY).isEqualTo("global:verify_attempts:");
        assertThat(GlobalConstants.LOGIN_FAIL_IP_KEY).isEqualTo("global:login_fail_ip:");
        assertThat(GlobalConstants.REPEAT_SUBMIT_KEY).isEqualTo("global:repeat_submit:");
        assertThat(GlobalConstants.RATE_LIMIT_KEY).isEqualTo("global:rate_limit:");
        assertThat(GlobalConstants.SOCIAL_AUTH_CODE_KEY).isEqualTo("global:social_auth_codes:");
    }

    @Test
    @DisplayName("HTTP 状态码（全局异常处理与 ServiceException 的错误码）")
    void httpStatus() {
        assertThat(HttpStatus.BAD_REQUEST).isEqualTo(400);
        assertThat(HttpStatus.UNAUTHORIZED).isEqualTo(401);
        assertThat(HttpStatus.FORBIDDEN).isEqualTo(403);
        assertThat(HttpStatus.NOT_FOUND).isEqualTo(404);
        assertThat(HttpStatus.BAD_METHOD).isEqualTo(405);
        assertThat(HttpStatus.CONFLICT).isEqualTo(409);
        assertThat(HttpStatus.PAYLOAD_TOO_LARGE).isEqualTo(413);
        assertThat(HttpStatus.UNSUPPORTED_TYPE).isEqualTo(415);
        assertThat(HttpStatus.TOO_MANY_REQUESTS).isEqualTo(429);
        assertThat(HttpStatus.ERROR).isEqualTo(500);
        assertThat(HttpStatus.SERVICE_UNAVAILABLE).isEqualTo(503);
    }

    @Test
    @DisplayName("正则常量：字典类型、权限标识、手机号")
    void regexConstants() {
        assertThat(RegexConstants.DICTIONARY_TYPE).isEqualTo("^[a-z][a-z0-9_]*$");
        assertThat("sys_user_sex").matches(RegexConstants.DICTIONARY_TYPE);
        assertThat("Sys_user").doesNotMatch(RegexConstants.DICTIONARY_TYPE);
        assertThat("1abc").doesNotMatch(RegexConstants.DICTIONARY_TYPE);

        assertThat(RegexConstants.PERMISSION_STRING).isEqualTo("^$|^[a-zA-Z0-9_]+:[a-zA-Z0-9_*]+:[a-zA-Z0-9_*]+$");
        assertThat("").matches(RegexConstants.PERMISSION_STRING);
        assertThat("system:user:list").matches(RegexConstants.PERMISSION_STRING);
        assertThat("system:*:*").matches(RegexConstants.PERMISSION_STRING);
        assertThat("*:user:list").doesNotMatch(RegexConstants.PERMISSION_STRING);
        assertThat("system:user").doesNotMatch(RegexConstants.PERMISSION_STRING);

        assertThat(RegexConstants.MOBILE).isEqualTo(RegexPool.MOBILE);
    }

    @Test
    @DisplayName("系统常量：状态、菜单类型、超级管理员、日志中去掉的口令字段")
    void systemConstants() {
        assertThat(SystemConstants.NORMAL).isEqualTo("0");
        assertThat(SystemConstants.DISABLE).isEqualTo("1");
        assertThat(SystemConstants.YES).isEqualTo("Y");
        assertThat(SystemConstants.NO).isEqualTo("N");
        assertThat(SystemConstants.TYPE_DIR).isEqualTo("M");
        assertThat(SystemConstants.TYPE_MENU).isEqualTo("C");
        assertThat(SystemConstants.TYPE_BUTTON).isEqualTo("F");
        assertThat(SystemConstants.LAYOUT).isEqualTo("Layout");
        assertThat(SystemConstants.PARENT_VIEW).isEqualTo("ParentView");
        assertThat(SystemConstants.INNER_LINK).isEqualTo("InnerLink");
        assertThat(SystemConstants.SUPER_ADMIN_USER_ID).isEqualTo(1761100000000000001L);
        assertThat(SystemConstants.SUPER_ADMIN_ROLE_ID).isEqualTo(1761300000000000001L);
        assertThat(SystemConstants.SUPER_ADMIN_ROLE_KEY).isEqualTo("superadmin");
        assertThat(SystemConstants.ROOT_DEPT_ANCESTORS).isEqualTo("0");
        assertThat(SystemConstants.EXCLUDE_PROPERTIES)
            .containsExactly("password", "oldPassword", "newPassword", "confirmPassword");
    }
}
