package org.tuoyu.common.core.utils;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.StrUtil;
import org.apache.commons.lang3.Strings;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 字符串工具。
 * <p>
 * 继承 commons-lang3 的 {@link org.apache.commons.lang3.StringUtils}，继承的方法按 commons 的语义工作
 * （例如 {@code isBlank} 只把 {@link Character#isWhitespace(char)} 当作空白）。本类声明的方法中，
 * {@link #blankToDefault}、{@link #trim} 按 hutool 的空白字符判断（还包括不换行空格、全角空格等）；
 * 比较、查找与替换方法委托 commons 的 {@link Strings#CS}（区分大小写）与 {@link Strings#CI}（忽略大小写）。
 * <p>
 * 全部方法无状态，线程安全。
 *
 * @author tuoyu
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {

    /**
     * 逗号
     */
    public static final String SEPARATOR = ",";

    /**
     * 斜杠
     */
    public static final String SLASH = "/";

    /**
     * 冒号
     */
    public static final String COLON = ":";

    /**
     * 默认设置：分隔符 {@code /}、区分大小写、不修剪；只读使用，可以共享
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private StringUtils() {
    }

    /**
     * @param str          字符串
     * @param defaultValue 默认值
     * @return str 为空白（null、空串或只有 hutool 意义上的空白字符）时返回默认值，否则返回 str
     */
    public static String blankToDefault(String str, String defaultValue) {
        return StrUtil.blankToDefault(str, defaultValue);
    }

    /**
     * 去掉两端的空白字符（hutool 意义上的空白，含不换行空格、全角空格）。
     *
     * @param str 字符串
     * @return 去掉空白后的字符串；null 返回 null
     */
    public static String trim(String str) {
        return StrUtil.trim(str);
    }

    /**
     * 截取字符串：负数下标从末尾倒数，超出范围的取边界，起止颠倒时交换。
     *
     * @param str   字符串
     * @param start 开始位置（包含）
     * @param end   结束位置（不包含）
     * @return 截取的部分；str 为 null 时返回 null
     */
    public static String substring(String str, int start, int end) {
        return StrUtil.sub(str, start, end);
    }

    /**
     * 格式化文本：{@code {}} 按顺序替换为参数，{@code \{}} 转义；参数不足时保留剩余的占位符。
     *
     * @param template 模板
     * @param params   参数
     * @return 格式化后的文本；模板为 null 时返回 {@code "null"}
     */
    public static String format(String template, Object... params) {
        return StrUtil.format(template, params);
    }

    /**
     * @param link 链接
     * @return 是否为能构造 {@link java.net.URL} 的地址（JDK 认识的协议，例如 http、https、ftp、file）
     */
    public static boolean ishttp(String link) {
        return Validator.isUrl(link);
    }

    /**
     * 拆分字符串。
     *
     * @param str         字符串
     * @param sep         分隔符（正则表达式，末尾的空段按 {@link String#split(String)} 的规则丢弃）
     * @param filterBlank 是否跳过空白段
     * @param trim        是否去掉每段两端的空白
     * @return 可修改的列表；str 为 null 或空串时为空列表
     */
    public static List<String> str2List(String str, String sep, boolean filterBlank, boolean trim) {
        List<String> list = new ArrayList<>();
        if (isEmpty(str) || (filterBlank && isBlank(str))) {
            return list;
        }
        for (String part : str.split(sep)) {
            if (filterBlank && isBlank(part)) {
                continue;
            }
            list.add(trim ? trim(part) : part);
        }
        return list;
    }

    /**
     * @param str 字符串
     * @param sep 分隔符（正则表达式）
     * @return 拆分后的非空白段组成的集合
     */
    public static Set<String> str2Set(String str, String sep) {
        return new HashSet<>(str2List(str, sep, true, false));
    }

    /**
     * @param str 驼峰形式，例如 {@code userName}
     * @return 下划线形式，例如 {@code user_name}
     */
    public static String toUnderScoreCase(String str) {
        return StrUtil.toUnderlineCase(str);
    }

    /**
     * @param s 下划线形式，例如 {@code user_name}
     * @return 驼峰形式，例如 {@code userName}
     */
    public static String toCamelCase(String s) {
        return StrUtil.toCamelCase(s);
    }

    /**
     * @param name 下划线形式，例如 {@code HELLO_WORLD}
     * @return 首字母大写的驼峰形式，例如 {@code HelloWorld}；null 返回 null
     */
    public static String convertToCamelCase(String name) {
        return StrUtil.upperFirst(toCamelCase(name));
    }

    /**
     * 按 Ant 路径规则匹配：{@code ?} 一个字符，{@code *} 一层路径内任意字符，{@code **} 任意层；区分大小写。
     * 用于客户端的接口路径授权。
     *
     * @param str      路径
     * @param patterns 规则
     * @return 是否匹配任一规则；路径为空或没有规则时为 false
     */
    public static boolean matches(String str, List<String> patterns) {
        if (isEmpty(str) || patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param str 用逗号分隔的字符串
     * @return 拆分后的各段（保留空段与空白）
     */
    public static List<String> splitList(String str) {
        return splitTo(str, String::valueOf);
    }

    /**
     * @param str       字符串
     * @param separator 分隔符（字面量）
     * @return 拆分后的各段（保留空段与空白）
     */
    public static List<String> splitList(String str, String separator) {
        return splitTo(str, separator, String::valueOf);
    }

    /**
     * @param str    用逗号分隔的字符串
     * @param mapper 每段的转换函数，返回 null 的段丢弃
     * @param <T>    转换结果的类型
     * @return 可修改的列表
     */
    public static <T> List<T> splitTo(String str, Function<? super Object, T> mapper) {
        return splitTo(str, SEPARATOR, mapper);
    }

    /**
     * 按分隔符的字面量拆分（不是正则），不去空白、保留空段，例如 {@code "a,,b"} 得到 {@code a}、空串、{@code b}。
     *
     * @param str       字符串
     * @param separator 分隔符（字面量）
     * @param mapper    每段的转换函数，返回 null 的段丢弃
     * @param <T>       转换结果的类型
     * @return 可修改的列表；str 为空白时为空列表
     */
    public static <T> List<T> splitTo(String str, String separator, Function<? super Object, T> mapper) {
        List<T> list = new ArrayList<>();
        if (isBlank(str)) {
            return list;
        }
        for (String part : splitLiteral(str, separator)) {
            T value = mapper.apply(part);
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    /**
     * @param str      字符串
     * @param prefixes 前缀
     * @return 是否以任一前缀开头（忽略大小写）
     */
    public static boolean startWithAnyIgnoreCase(CharSequence str, CharSequence... prefixes) {
        if (prefixes == null) {
            return false;
        }
        for (CharSequence prefix : prefixes) {
            if (Strings.CI.startsWith(str, prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param iterable 元素
     * @return 逗号连接的字符串，null 元素作空串；iterable 为 null 时返回 null
     */
    public static String joinComma(Iterable<?> iterable) {
        return join(iterable, SEPARATOR);
    }

    /**
     * @param array 元素
     * @return 逗号连接的字符串，null 元素作空串；array 为 null 时返回 null
     */
    public static String joinComma(Object[] array) {
        return join(array, SEPARATOR);
    }

    /**
     * @param cs1 字符串
     * @param cs2 字符串
     * @return 是否相等（区分大小写），两个都为 null 时为 true
     */
    public static boolean equals(CharSequence cs1, CharSequence cs2) {
        return Strings.CS.equals(cs1, cs2);
    }

    /**
     * @param cs1 字符串
     * @param cs2 字符串
     * @return 忽略大小写是否相等，两个都为 null 时为 true
     */
    public static boolean equalsIgnoreCase(CharSequence cs1, CharSequence cs2) {
        return Strings.CI.equals(cs1, cs2);
    }

    /**
     * @param string        字符串
     * @param searchStrings 候选
     * @return 是否与任一候选相等（区分大小写）
     */
    public static boolean equalsAny(CharSequence string, CharSequence... searchStrings) {
        return Strings.CS.equalsAny(string, searchStrings);
    }

    /**
     * @param string        字符串
     * @param searchStrings 候选
     * @return 是否与任一候选相等（忽略大小写）
     */
    public static boolean equalsAnyIgnoreCase(CharSequence string, CharSequence... searchStrings) {
        return Strings.CI.equalsAny(string, searchStrings);
    }

    /**
     * @param seq       字符串
     * @param searchSeq 要查找的内容
     * @return 是否包含（区分大小写），任一为 null 时为 false
     */
    public static boolean contains(CharSequence seq, CharSequence searchSeq) {
        return Strings.CS.contains(seq, searchSeq);
    }

    /**
     * @param str       字符串
     * @param searchStr 要查找的内容
     * @return 是否包含（忽略大小写），任一为 null 时为 false
     */
    public static boolean containsIgnoreCase(CharSequence str, CharSequence searchStr) {
        return Strings.CI.contains(str, searchStr);
    }

    /**
     * @param cs                  字符串
     * @param searchCharSequences 要查找的内容
     * @return 是否包含任一内容（区分大小写）
     */
    public static boolean containsAny(CharSequence cs, CharSequence... searchCharSequences) {
        return Strings.CS.containsAny(cs, searchCharSequences);
    }

    /**
     * @param str    字符串
     * @param prefix 前缀
     * @return 是否以前缀开头（区分大小写）
     */
    public static boolean startsWith(CharSequence str, CharSequence prefix) {
        return Strings.CS.startsWith(str, prefix);
    }

    /**
     * @param str    字符串
     * @param prefix 前缀
     * @return 是否以前缀开头（忽略大小写）
     */
    public static boolean startsWithIgnoreCase(CharSequence str, CharSequence prefix) {
        return Strings.CI.startsWith(str, prefix);
    }

    /**
     * @param str    字符串
     * @param suffix 后缀
     * @return 是否以后缀结尾（区分大小写）
     */
    public static boolean endsWith(CharSequence str, CharSequence suffix) {
        return Strings.CS.endsWith(str, suffix);
    }

    /**
     * @param str    字符串
     * @param suffix 后缀
     * @return 是否以后缀结尾（忽略大小写）
     */
    public static boolean endsWithIgnoreCase(CharSequence str, CharSequence suffix) {
        return Strings.CI.endsWith(str, suffix);
    }

    /**
     * @param sequence      字符串
     * @param searchStrings 后缀
     * @return 是否以任一后缀结尾（区分大小写）
     */
    public static boolean endsWithAny(CharSequence sequence, CharSequence... searchStrings) {
        return Strings.CS.endsWithAny(sequence, searchStrings);
    }

    /**
     * @param seq       字符串
     * @param searchSeq 要查找的内容
     * @return 第一次出现的位置（区分大小写）；没有或任一为 null 时为 -1
     */
    public static int indexOf(CharSequence seq, CharSequence searchSeq) {
        return Strings.CS.indexOf(seq, searchSeq);
    }

    /**
     * @param str    字符串
     * @param remove 要去掉的内容
     * @return 去掉全部该内容后的字符串（区分大小写）
     */
    public static String remove(String str, String remove) {
        return Strings.CS.remove(str, remove);
    }

    /**
     * @param str    字符串
     * @param remove 要去掉的前缀
     * @return 以该前缀开头时去掉前缀，否则原样返回（区分大小写）
     */
    public static String removeStart(String str, String remove) {
        return Strings.CS.removeStart(str, remove);
    }

    /**
     * @param text         字符串
     * @param searchString 要替换的内容
     * @param replacement  替换为
     * @return 替换全部出现之后的字符串（区分大小写）
     */
    public static String replace(String text, String searchString, String replacement) {
        return Strings.CS.replace(text, searchString, replacement);
    }

    /**
     * @param text         字符串
     * @param searchString 要替换的内容
     * @param replacement  替换为
     * @return 只替换第一次出现之后的字符串（区分大小写）
     */
    public static String replaceOnce(String text, String searchString, String replacement) {
        return Strings.CS.replaceOnce(text, searchString, replacement);
    }

    /**
     * 按字面量拆分，保留全部段（包括开头、中间与末尾的空段）
     */
    private static List<String> splitLiteral(String str, String separator) {
        List<String> parts = new ArrayList<>();
        if (isEmpty(separator)) {
            parts.add(str);
            return parts;
        }
        int start = 0;
        int index = str.indexOf(separator);
        while (index >= 0) {
            parts.add(str.substring(start, index));
            start = index + separator.length();
            index = str.indexOf(separator, start);
        }
        parts.add(str.substring(start));
        return parts;
    }

}
