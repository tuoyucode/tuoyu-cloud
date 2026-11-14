package org.tuoyu.common.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StringUtils：本类声明的方法与常量")
class StringUtilsTest {

    @Test
    @DisplayName("常量")
    void constants() {
        assertThat(StringUtils.SEPARATOR).isEqualTo(",");
        assertThat(StringUtils.SLASH).isEqualTo("/");
        assertThat(StringUtils.COLON).isEqualTo(":");
    }

    @Test
    @DisplayName("blankToDefault 与 trim 按 hutool 的空白字符（含不换行空格、全角空格）；继承的 isBlank 按 commons 的规则")
    void blankAndTrim() {
        assertThat(StringUtils.blankToDefault(null, "d")).isEqualTo("d");
        assertThat(StringUtils.blankToDefault(" \t", "d")).isEqualTo("d");
        assertThat(StringUtils.blankToDefault(" 　", "d")).isEqualTo("d");
        assertThat(StringUtils.blankToDefault(" a ", "d")).isEqualTo(" a ");

        assertThat(StringUtils.trim(" value ")).isEqualTo("value");
        assertThat(StringUtils.trim(" a　")).isEqualTo("a");
        assertThat(StringUtils.trim(null)).isNull();

        assertThat(StringUtils.isBlank(" ")).isFalse();
        assertThat(StringUtils.isBlank(" \t")).isTrue();
        assertThat(StringUtils.isEmpty("")).isTrue();
        assertThat(StringUtils.isNotEmpty("value")).isTrue();
    }

    @Test
    @DisplayName("substring：负数从末尾倒数，超出范围截断，起止颠倒时交换，null 返回 null")
    void substring() {
        assertThat(StringUtils.substring("abcd", 1, 3)).isEqualTo("bc");
        assertThat(StringUtils.substring("abcd", -2, 4)).isEqualTo("cd");
        assertThat(StringUtils.substring("abcd", 0, 10)).isEqualTo("abcd");
        assertThat(StringUtils.substring("abcd", 3, 1)).isEqualTo("bc");
        assertThat(StringUtils.substring("abcd", 2, 2)).isEmpty();
        assertThat(StringUtils.substring("", 0, 1)).isEmpty();
        assertThat(StringUtils.substring(null, 0, 1)).isNull();
    }

    @Test
    @DisplayName("format：{} 按顺序替换，参数不足时保留占位符，\\{} 转义")
    void format() {
        assertThat(StringUtils.format("id={}", 12)).isEqualTo("id=12");
        assertThat(StringUtils.format("{}/{}", "a", "b")).isEqualTo("a/b");
        assertThat(StringUtils.format("{}-{}", "a")).isEqualTo("a-{}");
        assertThat(StringUtils.format("x\\{}y {}", 1)).isEqualTo("x{}y 1");
        assertThat(StringUtils.format("plain")).isEqualTo("plain");
        assertThat(StringUtils.format((String) null)).isEqualTo("null");
    }

    @Test
    @DisplayName("ishttp：能构造 URL 即为 true（不限于 http）")
    void ishttp() {
        assertThat(StringUtils.ishttp("https://example.com/path")).isTrue();
        assertThat(StringUtils.ishttp("http://localhost:8080")).isTrue();
        assertThat(StringUtils.ishttp("ftp://example.com")).isTrue();
        assertThat(StringUtils.ishttp("javascript:alert(1)")).isFalse();
        assertThat(StringUtils.ishttp("example.com")).isFalse();
        assertThat(StringUtils.ishttp("")).isFalse();
        assertThat(StringUtils.ishttp(null)).isFalse();
    }

    @Test
    @DisplayName("str2List：分隔符是正则；可过滤空白段、去掉首尾空白；结果可修改")
    void str2List() {
        assertThat(StringUtils.str2List(" a, ,b ", ",", true, true)).containsExactly("a", "b");
        assertThat(StringUtils.str2List(" a, ,b ", ",", true, false)).containsExactly(" a", "b ");
        assertThat(StringUtils.str2List("a,,b", ",", false, false)).containsExactly("a", "", "b");
        assertThat(StringUtils.str2List("a|b;c", "[|;]", true, false)).containsExactly("a", "b", "c");
        assertThat(StringUtils.str2List("  ", ",", true, true)).isEmpty();
        assertThat(StringUtils.str2List("  ", ",", false, true)).containsExactly("");
        assertThat(StringUtils.str2List(null, ",", false, false)).isEmpty();
        List<String> list = StringUtils.str2List("a", ",", true, true);
        list.add("b");
        assertThat(list).containsExactly("a", "b");

        assertThat(StringUtils.str2Set("a,b,a, ", ",")).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("命名转换")
    void naming() {
        assertThat(StringUtils.toUnderScoreCase("userName")).isEqualTo("user_name");
        assertThat(StringUtils.toCamelCase("user_name")).isEqualTo("userName");
        assertThat(StringUtils.toCamelCase("userName")).isEqualTo("userName");
        assertThat(StringUtils.convertToCamelCase("HELLO_WORLD")).isEqualTo("HelloWorld");
        assertThat(StringUtils.convertToCamelCase("user_name")).isEqualTo("UserName");
        assertThat(StringUtils.convertToCamelCase(null)).isNull();
    }

    @Test
    @DisplayName("matches：Ant 路径规则，区分大小写；* 不跨层，** 跨层")
    void matches() {
        assertThat(StringUtils.matches("/system/user/list", List.of("/login", "/system/**"))).isTrue();
        assertThat(StringUtils.matches("/system/user/list", List.of("/system/*"))).isFalse();
        assertThat(StringUtils.matches("/system/user", List.of("/system/*"))).isTrue();
        assertThat(StringUtils.matches("/system/user", List.of("/system/use?"))).isTrue();
        assertThat(StringUtils.matches("/System/user", List.of("/system/**"))).isFalse();
        assertThat(StringUtils.matches("", List.of("/**"))).isFalse();
        assertThat(StringUtils.matches(null, List.of("/**"))).isFalse();
        assertThat(StringUtils.matches("/system", List.of())).isFalse();
        assertThat(StringUtils.matches("/system", null)).isFalse();
    }

    @Test
    @DisplayName("splitList / splitTo：分隔符按字面量，保留空段与空白，丢弃转换结果为 null 的段")
    void split() {
        assertThat(StringUtils.splitList("a,b")).containsExactly("a", "b");
        assertThat(StringUtils.splitList("a,,b")).containsExactly("a", "", "b");
        assertThat(StringUtils.splitList("a, b")).containsExactly("a", " b");
        assertThat(StringUtils.splitList("a|b", "|")).containsExactly("a", "b");
        assertThat(StringUtils.splitList("a.b", ".")).containsExactly("a", "b");
        assertThat(StringUtils.splitList(" ")).isEmpty();
        assertThat(StringUtils.splitList(null)).isEmpty();

        List<Long> ids = StringUtils.splitTo("1,,2", value -> value.toString().isEmpty() ? null : Long.valueOf(value.toString()));
        assertThat(ids).containsExactly(1L, 2L);
        ids.add(3L);
        assertThat(StringUtils.splitTo("1|2", "|", value -> Integer.valueOf(value.toString()))).containsExactly(1, 2);
    }

    @Test
    @DisplayName("startWithAnyIgnoreCase")
    void startWithAny() {
        assertThat(StringUtils.startWithAnyIgnoreCase("Bearer token", "basic", "bearer")).isTrue();
        assertThat(StringUtils.startWithAnyIgnoreCase("Token x", "basic", "bearer")).isFalse();
    }

    @Test
    @DisplayName("joinComma：null 返回 null，null 元素作空串")
    void joinComma() {
        assertThat(StringUtils.joinComma(List.of("a", "b"))).isEqualTo("a,b");
        assertThat(StringUtils.joinComma(new Integer[]{1, 2})).isEqualTo("1,2");
        assertThat(StringUtils.joinComma(Arrays.asList("a", null, "c"))).isEqualTo("a,,c");
        assertThat(StringUtils.joinComma((Iterable<?>) null)).isNull();
        assertThat(StringUtils.joinComma((Object[]) null)).isNull();
    }

    @Test
    @DisplayName("委托 commons Strings.CS / Strings.CI 的比较、查找与替换")
    void delegates() {
        assertThat(StringUtils.equals("a", "a")).isTrue();
        assertThat(StringUtils.equals(null, null)).isTrue();
        assertThat(StringUtils.equals("a", "A")).isFalse();
        assertThat(StringUtils.equalsIgnoreCase("a", "A")).isTrue();
        assertThat(StringUtils.equalsAny("a", "b", "a")).isTrue();
        assertThat(StringUtils.equalsAny("A", "b", "a")).isFalse();
        assertThat(StringUtils.equalsAnyIgnoreCase("A", "b", "a")).isTrue();
        assertThat(StringUtils.contains("abc", "b")).isTrue();
        assertThat(StringUtils.contains(null, "b")).isFalse();
        assertThat(StringUtils.containsIgnoreCase("Hello", "ELL")).isTrue();
        assertThat(StringUtils.containsAny("abc", "x", "c")).isTrue();
        assertThat(StringUtils.containsAny("abc", "x", "y")).isFalse();
        assertThat(StringUtils.startsWith("ENC_value", "ENC_")).isTrue();
        assertThat(StringUtils.startsWithIgnoreCase("HTTPS://x", "https://")).isTrue();
        assertThat(StringUtils.endsWith("{x}", "}")).isTrue();
        assertThat(StringUtils.endsWithIgnoreCase("report.XLSX", ".xlsx")).isTrue();
        assertThat(StringUtils.endsWithAny("common.yaml", ".yml", ".yaml")).isTrue();
        assertThat(StringUtils.endsWithAny("common.YML", ".yml", ".yaml")).isFalse();
        assertThat(StringUtils.indexOf("abcabc", "ca")).isEqualTo(2);
        assertThat(StringUtils.indexOf(null, "a")).isEqualTo(-1);
        assertThat(StringUtils.remove("a=b=c", "=")).isEqualTo("abc");
        assertThat(StringUtils.removeStart("/path", "/")).isEqualTo("path");
        assertThat(StringUtils.removeStart("path", "/")).isEqualTo("path");
        assertThat(StringUtils.replace("a.b.c", ".", "/")).isEqualTo("a/b/c");
        assertThat(StringUtils.replace("abc", "", "x")).isEqualTo("abc");
        assertThat(StringUtils.replace(null, "a", "b")).isNull();
        assertThat(StringUtils.replaceOnce("a_b_c", "_", "-")).isEqualTo("a-b_c");
    }
}
