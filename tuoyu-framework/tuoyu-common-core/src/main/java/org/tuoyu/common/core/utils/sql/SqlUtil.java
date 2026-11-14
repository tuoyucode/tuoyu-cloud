package org.tuoyu.common.core.utils.sql;

import cn.hutool.core.exceptions.UtilException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 检查要拼进 SQL 的值。
 * <p>
 * 黑名单式检查：值中含单引号，或规范化（连续空白合并为一个空格、转为小写）后含任一关键字（子串）时拒绝。
 * 会误伤含这些子串的正常文本（例如 {@code brand new} 含 {@code and }），只用于不应出现这类文本的值（列名、ID 等）。
 *
 * @author tuoyu
 */
public final class SqlUtil {

    /**
     * 连续的空白：含不换行空格等 Unicode 分隔符、制表符与换行
     */
    private static final Pattern WHITESPACE = Pattern.compile("[\\p{Z}\\s]+");

    /**
     * 关键字，按顺序检查；多数带一个尾随空格
     */
    private static final List<String> KEYWORDS = List.of(
        "and ", "extractvalue", "updatexml", "sleep", "information_schema", "exec ", "insert ", "select ",
        "delete ", "update ", "drop ", "count ", "chr ", "mid ", "master ", "truncate ", "char ", "declare ",
        "or ", "union ", "like ", "+", "/*", "user()");

    private SqlUtil() {
    }

    /**
     * @param value 要检查的值；null 或空串时直接通过
     * @throws UtilException 含单引号或敏感关键字时
     */
    public static void filterKeyword(String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (value.indexOf('\'') >= 0) {
            throw new UtilException("请求参数包含非法字符【'】，已禁止执行");
        }
        String normalized = WHITESPACE.matcher(value).replaceAll(" ").toLowerCase(Locale.ROOT);
        for (String keyword : KEYWORDS) {
            if (normalized.contains(keyword)) {
                throw new UtilException("请求参数包含敏感关键词'" + keyword + "'，可能存在安全风险");
            }
        }
    }

}
