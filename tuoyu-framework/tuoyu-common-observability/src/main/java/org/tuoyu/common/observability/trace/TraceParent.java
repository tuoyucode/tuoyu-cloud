package org.tuoyu.common.observability.trace;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 一个执行位置的链路标识，格式遵循 W3C Trace Context 的 {@code traceparent}：
 * {@code 版本-traceId-spanId-标志}，例如 {@code 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}。
 * <ul>
 *     <li>traceId：32 位小写十六进制，一次请求经过的所有服务、线程共用；</li>
 *     <li>spanId：16 位小写十六进制，每个执行位置（服务、异步线程）各自生成；</li>
 *     <li>标志：2 位十六进制，本项目只原样传递（自己生成时为 01）。</li>
 * </ul>
 * 采用 W3C 格式是为了 1.1 接入 OpenTelemetry 时，现有服务产生的请求头可以直接被识别。
 *
 * @param traceId 链路 ID
 * @param spanId  当前执行位置的 ID
 * @param flags   标志位
 * @author tuoyu
 */
public record TraceParent(String traceId, String spanId, String flags) {

    /**
     * W3C 规定的请求头名称
     */
    public static final String HEADER = "traceparent";

    /**
     * 本项目输出的版本
     */
    static final String VERSION = "00";

    /**
     * 自己开始一条新链路时的标志位（sampled）
     */
    static final String DEFAULT_FLAGS = "01";

    /**
     * 版本 00 的固定长度：2 + 1 + 32 + 1 + 16 + 1 + 2
     */
    private static final int LENGTH = 55;

    public TraceParent {
        if (!isHex(traceId, 32) || isAllZero(traceId)) {
            throw new IllegalArgumentException("traceId 必须是 32 位非全零的小写十六进制：" + traceId);
        }
        if (!isHex(spanId, 16) || isAllZero(spanId)) {
            throw new IllegalArgumentException("spanId 必须是 16 位非全零的小写十六进制：" + spanId);
        }
        if (!isHex(flags, 2)) {
            throw new IllegalArgumentException("标志位必须是 2 位小写十六进制：" + flags);
        }
    }

    /**
     * 解析请求头。格式不合法时返回 null（调用方另起一条新链路），不抛出异常：请求头来自外部，不能因此拒绝请求。
     * <p>
     * 按规范：版本 ff 无效；版本 00 长度必须恰好 55；更高的版本允许在后面追加以 {@code -} 分隔的字段，只取前四段。
     *
     * @param header 请求头的值
     * @return 解析结果；不合法时为 null
     */
    public static TraceParent parse(String header) {
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (value.length() < LENGTH) {
            return null;
        }
        String version = value.substring(0, 2);
        if (!isHex(version, 2) || "ff".equals(version)) {
            return null;
        }
        if (VERSION.equals(version) ? value.length() != LENGTH : value.length() > LENGTH && value.charAt(LENGTH) != '-') {
            return null;
        }
        if (value.charAt(2) != '-' || value.charAt(35) != '-' || value.charAt(52) != '-') {
            return null;
        }
        String traceId = value.substring(3, 35);
        String spanId = value.substring(36, 52);
        String flags = value.substring(53, 55);
        if (!isHex(traceId, 32) || isAllZero(traceId) || !isHex(spanId, 16) || isAllZero(spanId) || !isHex(flags, 2)) {
            return null;
        }
        return new TraceParent(traceId, spanId, flags);
    }

    /**
     * 开始一条新链路
     */
    public static TraceParent newRoot() {
        return new TraceParent(randomHex(2), randomHex(1), DEFAULT_FLAGS);
    }

    /**
     * 继续同一条链路的下一个执行位置：traceId 与标志不变，spanId 重新生成
     */
    public TraceParent child() {
        return new TraceParent(traceId, randomHex(1), flags);
    }

    /**
     * @return 请求头格式（版本 00）
     */
    public String format() {
        return VERSION + '-' + traceId + '-' + spanId + '-' + flags;
    }

    @Override
    public String toString() {
        return format();
    }

    /**
     * 生成 longs × 16 位的随机十六进制串，保证不是全零
     */
    private static String randomHex(int longs) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder builder = new StringBuilder(longs * 16);
        boolean allZero = true;
        for (int i = 0; i < longs; i++) {
            long value = random.nextLong();
            if (i == longs - 1 && allZero && value == 0) {
                value = 1;
            }
            allZero &= value == 0;
            String hex = Long.toHexString(value);
            builder.append("0".repeat(16 - hex.length())).append(hex);
        }
        return builder.toString();
    }

    private static boolean isHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

}
