package org.tuoyu.common.core.constant;

import cn.hutool.core.lang.RegexPool;

/**
 * 校验注解中使用的正则表达式（{@code @Pattern(regexp = ...)}）。
 *
 * @author tuoyu
 */
public interface RegexConstants {

    /**
     * 字典类型：小写字母开头，只含小写字母、数字与下划线
     */
    String DICTIONARY_TYPE = "^[a-z][a-z0-9_]*$";

    /**
     * 权限标识：空串，或 {@code 模块:资源:操作} 三段，后两段可以是 {@code *}
     */
    String PERMISSION_STRING = "^$|^[a-zA-Z0-9_]+:[a-zA-Z0-9_*]+:[a-zA-Z0-9_*]+$";

    /**
     * 中国大陆手机号，可以带 0、86、+86 前缀
     */
    String MOBILE = RegexPool.MOBILE;

}
