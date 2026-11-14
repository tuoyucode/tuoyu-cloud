package org.tuoyu.common.core.utils.regex;

import cn.hutool.core.lang.Validator;

/**
 * 常用格式的校验。
 *
 * @author tuoyu
 */
public final class RegexValidator {

    private RegexValidator() {
    }

    /**
     * @param value 值
     * @return 是否为邮箱（忽略大小写，整串匹配）；null 为 false
     */
    public static boolean isEmail(CharSequence value) {
        return value != null && Validator.isEmail(value);
    }

    /**
     * @param value 值
     * @return 是否为中国大陆手机号（可以带 0、86、+86 前缀，整串匹配）；null 为 false
     */
    public static boolean isMobile(CharSequence value) {
        return value != null && Validator.isMobile(value);
    }

}
