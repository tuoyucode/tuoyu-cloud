package org.tuoyu.common.core.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 脱敏：把值的一部分换成 {@code *}。
 * <p>
 * value 为 null 或空白时原样返回。值很短时优先保证掩码的长度，宁可少显示，也不让原值完整露出。
 *
 * @author tuoyu
 */
public final class DesensitizedUtils {

    private static final char MASK = '*';

    private DesensitizedUtils() {
    }

    /**
     * 固定长度的掩码：保留前 {@code prefixVisible} 个字符，接 {@code maskLength} 个 {@code *}，再接末尾最多
     * {@code suffixVisible} 个字符。值不够长时先减少末尾、再减少开头保留的字符，掩码长度不变。
     *
     * @param value         原值
     * @param prefixVisible 开头保留的字符数
     * @param suffixVisible 末尾保留的字符数
     * @param maskLength    掩码长度
     * @return 脱敏后的值
     */
    public static String mask(String value, int prefixVisible, int suffixVisible, int maskLength) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        int length = value.length();
        if (length <= maskLength) {
            return repeat(length);
        }
        if (length <= prefixVisible + maskLength) {
            return value.substring(0, length - maskLength) + repeat(maskLength);
        }
        int suffix = Math.min(suffixVisible, length - prefixVisible - maskLength);
        return value.substring(0, prefixVisible) + repeat(maskLength) + value.substring(length - suffix);
    }

    /**
     * 高安全级别：保留开头与末尾的字符，中间全部换成 {@code *}。值不够长时优先掩码后面的部分。
     *
     * @param value         原值
     * @param prefixVisible 开头保留的字符数
     * @param suffixVisible 末尾保留的字符数
     * @return 脱敏后的值
     */
    public static String maskHighSecurity(String value, int prefixVisible, int suffixVisible) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        int length = value.length();
        if (length <= prefixVisible) {
            return repeat(length);
        }
        if (length <= prefixVisible + suffixVisible) {
            return value.substring(0, length - prefixVisible) + repeat(prefixVisible);
        }
        return value.substring(0, prefixVisible) + repeat(length - prefixVisible - suffixVisible)
            + value.substring(length - suffixVisible);
    }

    private static String repeat(int count) {
        return String.valueOf(MASK).repeat(count);
    }

}
