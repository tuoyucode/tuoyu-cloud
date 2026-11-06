package org.tuoyu.common.observability.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("链路标识：W3C traceparent 的生成与解析（ADR 0007）")
class TraceParentTest {

    private static final String SAMPLE = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    @DisplayName("解析合法的请求头，格式化后与原值相同")
    void parsesValidHeader() {
        TraceParent trace = TraceParent.parse(SAMPLE);
        assertNotNull(trace);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", trace.traceId());
        assertEquals("00f067aa0ba902b7", trace.spanId());
        assertEquals("01", trace.flags());
        assertEquals(SAMPLE, trace.format());
        assertEquals(trace, TraceParent.parse("  " + SAMPLE + " "), "前后空白忽略");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "",
        "abc",
        // 大写十六进制
        "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
        // traceId 全零、spanId 全零
        "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
        // 版本 ff 无效
        "ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        // 版本 00 不允许多余内容
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra",
        // 分隔符错误、长度不对、非十六进制
        "00_4bf92f3577b34da6a3ce929d0e0e4736_00f067aa0ba902b7_01",
        "00-4bf92f3577b34da6a3ce929d0e0e473-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e473g-00f067aa0ba902b7-01",
        // 注入换行等控制字符（日志伪造）
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-0\n"
    })
    @DisplayName("不合法的请求头返回 null，由调用方另起新链路，不抛异常")
    void rejectsInvalidHeader(String header) {
        assertNull(TraceParent.parse(header));
    }

    @Test
    @DisplayName("null 返回 null")
    void nullHeader() {
        assertNull(TraceParent.parse(null));
    }

    @Test
    @DisplayName("更高的版本：取前四段，忽略追加的字段")
    void acceptsFutureVersion() {
        TraceParent trace = TraceParent.parse("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-future");
        assertNotNull(trace);
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", trace.traceId());
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", trace.format(), "输出统一为版本 00");
        assertNull(TraceParent.parse("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01x"), "追加字段必须以 - 分隔");
    }

    @Test
    @DisplayName("新链路：格式合法、不重复，标志为 01")
    void generatesUniqueRoots() {
        Set<String> traceIds = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            TraceParent root = TraceParent.newRoot();
            assertEquals(root, TraceParent.parse(root.format()), "生成的值能被解析");
            assertEquals("01", root.flags());
            assertTrue(traceIds.add(root.traceId()), "traceId 重复");
        }
    }

    @Test
    @DisplayName("下一个执行位置：traceId 与标志不变，spanId 重新生成")
    void childKeepsTraceId() {
        TraceParent parent = TraceParent.parse(SAMPLE);
        TraceParent child = parent.child();
        assertEquals(parent.traceId(), child.traceId());
        assertEquals(parent.flags(), child.flags());
        assertNotEquals(parent.spanId(), child.spanId());
    }

    @Test
    @DisplayName("构造时校验字段")
    void validatesFields() {
        assertThrows(IllegalArgumentException.class, () -> new TraceParent("xyz", "00f067aa0ba902b7", "01"));
        assertThrows(IllegalArgumentException.class, () -> new TraceParent("4bf92f3577b34da6a3ce929d0e0e4736", "0000000000000000", "01"));
        assertThrows(IllegalArgumentException.class, () -> new TraceParent("4bf92f3577b34da6a3ce929d0e0e4736", "00f067aa0ba902b7", "1"));
    }

}
