package org.tuoyu.common.core.utils;

import cn.hutool.core.exceptions.UtilException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.utils.sql.SqlUtil;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SqlUtil.filterKeyword：拼进 SQL 的值的黑名单检查")
class SqlUtilTest {

    @Test
    @DisplayName("空值与普通文本通过")
    void accepts() {
        for (String value : new String[]{null, "", "normal-business-value", "100", "role_ids", "selected", "count(*)",
            "orderly"}) {
            assertThatCode(() -> SqlUtil.filterKeyword(value)).as(String.valueOf(value)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("单引号直接拒绝")
    void quote() {
        assertThatThrownBy(() -> SqlUtil.filterKeyword("name='admin'"))
            .isInstanceOf(UtilException.class)
            .hasMessage("请求参数包含非法字符【'】，已禁止执行");
    }

    @Test
    @DisplayName("关键字按子串匹配，大小写与各种空白（含制表符、不换行空格）规范化后比较；按关键字表的顺序报告")
    void keywords() {
        assertRejected("union select password", "select ");
        assertRejected("UNION\tSELECT password", "select ");
        assertRejected("a OR b", "or ");
        assertRejected("1 and 1", "and ");
        assertRejected("brand new", "and ");
        assertRejected("sleeping", "sleep");
        assertRejected("extractvalue(1)", "extractvalue");
        assertRejected("information_schema.tables", "information_schema");
        assertRejected("drop  table x", "drop ");
        assertRejected("a+b", "+");
        assertRejected("x/**/y", "/*");
        assertRejected("user()", "user()");
        assertRejected("x like y", "like ");
    }

    private static void assertRejected(String value, String keyword) {
        assertThatThrownBy(() -> SqlUtil.filterKeyword(value))
            .as(value)
            .isInstanceOf(UtilException.class)
            .hasMessage("请求参数包含敏感关键词'" + keyword + "'，可能存在安全风险");
    }
}
