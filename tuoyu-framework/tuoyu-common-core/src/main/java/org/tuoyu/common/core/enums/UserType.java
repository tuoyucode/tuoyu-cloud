package org.tuoyu.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户类型。注册接口按 {@link #values()} 精确匹配请求中的用户类型。
 *
 * @author tuoyu
 */
@Getter
@AllArgsConstructor
public enum UserType {

    /**
     * 后台管理用户
     */
    SYS_USER("sys_user"),

    /**
     * 移动端用户
     */
    APP_USER("app_user");

    /**
     * 类型标识
     */
    private final String userType;

}
