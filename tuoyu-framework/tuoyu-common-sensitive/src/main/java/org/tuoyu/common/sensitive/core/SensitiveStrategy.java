package org.tuoyu.common.sensitive.core;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.DesensitizedUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.utils.DesensitizedUtils;

import java.util.function.Function;

/**
 * 脱敏策略
 * <p>
 * 输出时请使用 {@link #desensitize(String)}：它在策略出错或没有生效时整体掩码，保证不会输出原文（失败闭合）
 *
 * @author Yjoioooo
 * @version 3.6.0
 */
@Slf4j
@AllArgsConstructor
public enum SensitiveStrategy {

    /**
     * 身份证脱敏
     */
    ID_CARD(s -> DesensitizedUtil.idCardNum(s, 3, 4)),

    /**
     * 手机号脱敏
     */
    PHONE(DesensitizedUtil::mobilePhone),

    /**
     * 地址脱敏
     */
    ADDRESS(s -> DesensitizedUtil.address(s, 8)),

    /**
     * 邮箱脱敏
     */
    EMAIL(DesensitizedUtil::email),

    /**
     * 银行卡
     */
    BANK_CARD(DesensitizedUtil::bankCard),

    /**
     * 中文名
     */
    CHINESE_NAME(DesensitizedUtil::chineseName),

    /**
     * 固定电话
     */
    FIXED_PHONE(DesensitizedUtil::fixedPhone),

    /**
     * 用户ID
     */
    USER_ID(s -> Convert.toStr(DesensitizedUtil.userId())),

    /**
     * 密码
     */
    PASSWORD(DesensitizedUtil::password),

    /**
     * ipv4
     */
    IPV4(DesensitizedUtil::ipv4),

    /**
     * ipv6
     */
    IPV6(DesensitizedUtil::ipv6),

    /**
     * 中国大陆车牌，包含普通车辆、新能源车辆
     */
    CAR_LICENSE(DesensitizedUtil::carLicense),

    /**
     * 只显示第一个字符
     */
    FIRST_MASK(DesensitizedUtil::firstMask),

    /**
     * 通用字符串脱敏
     * 可配置前后可见长度和中间掩码长度
     * 默认示例：前4位可见，后4位可见，中间固定4个*
     */
    STRING_MASK(s -> DesensitizedUtils.mask(s, 4, 4, 4)),

    /**
     * 高安全级别脱敏（Token / 私钥）：前2位可见，后2位可见，中间全部掩码
     */
    MASK_HIGH_SECURITY(s -> DesensitizedUtils.maskHighSecurity(s, 2, 2)),

    /**
     * 清空为""
     */
    CLEAR(s -> DesensitizedUtil.clear()),

    /**
     * 清空为null
     */
    CLEAR_TO_NULL(s -> DesensitizedUtil.clearToNull());

    //可自行添加其他脱敏策略

    /**
     * 策略出错或没有生效时使用的整体掩码。固定长度，不暴露原文长度
     */
    public static final String FULL_MASK = "******";

    private final Function<String, String> desensitizer;

    /**
     * 原始的脱敏函数。部分函数遇到长度不足、格式不符的值时会原样返回（如没有 @ 的邮箱、过短的手机号），
     * 输出时请使用 {@link #desensitize(String)}
     */
    public Function<String, String> desensitizer() {
        return desensitizer;
    }

    /**
     * 失败闭合的脱敏：
     * <ul>
     *     <li>null 与空白字符串原样返回（不含任何信息）；</li>
     *     <li>脱敏函数抛出异常时返回 {@link #FULL_MASK}；</li>
     *     <li>脱敏结果与原文相同（策略没有生效）时返回 {@link #FULL_MASK}。</li>
     * </ul>
     *
     * @param value 原文
     * @return 脱敏后的值，绝不会是非空白的原文
     */
    public String desensitize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked;
        try {
            masked = desensitizer.apply(value);
        } catch (RuntimeException e) {
            // 不记录原文
            log.warn("脱敏策略 {} 执行出错，已整体掩码：{}", name(), e.getClass().getSimpleName());
            return FULL_MASK;
        }
        if (value.equals(masked)) {
            return FULL_MASK;
        }
        return masked;
    }
}
