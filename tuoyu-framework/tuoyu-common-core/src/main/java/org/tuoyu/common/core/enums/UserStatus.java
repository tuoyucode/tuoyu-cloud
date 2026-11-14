package org.tuoyu.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户状态。
 *
 * @author tuoyu
 */
@Getter
@AllArgsConstructor
public enum UserStatus {

    /**
     * 正常
     */
    OK("0"),

    /**
     * 停用
     */
    DISABLE("1");

    /**
     * 存储值
     */
    private final String code;

}
