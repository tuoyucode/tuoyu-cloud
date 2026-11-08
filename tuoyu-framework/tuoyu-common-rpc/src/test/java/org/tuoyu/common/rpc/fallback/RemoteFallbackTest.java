package org.tuoyu.common.rpc.fallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("跨模块调用的统一降级")
class RemoteFallbackTest {

    @BeforeEach
    void reset() {
        RemoteFallback.reset();
    }

    @Test
    @DisplayName("调用成功：返回结果，不计数")
    void success() {
        assertEquals("url", RemoteFallback.call("test.ok", () -> "url", ""));
        assertEquals(0, RemoteFallback.count("test.ok"));
    }

    @Test
    @DisplayName("调用失败（业务异常、超时等任何运行时异常）：返回降级值并计数")
    void fallbackOnFailure() {
        assertEquals("", RemoteFallback.call("test.url", () -> {
            throw new ServiceException("文件服务不可用");
        }, ""));
        assertEquals(List.of(), RemoteFallback.callOrElseGet("test.url", () -> {
            throw new IllegalStateException("timeout");
        }, List::of));
        assertEquals(2, RemoteFallback.count("test.url"));
        assertEquals(2L, RemoteFallback.counts().get("test.url"));
    }

    @Test
    @DisplayName("没有返回值的调用：失败时忽略并计数")
    void runIgnoresFailure() {
        AtomicBoolean called = new AtomicBoolean();
        RemoteFallback.run("test.push", () -> called.set(true));
        RemoteFallback.run("test.push", () -> {
            throw new IllegalStateException("推送失败");
        });
        assertTrue(called.get());
        assertEquals(1, RemoteFallback.count("test.push"));
    }

    @Test
    @DisplayName("Error 不降级，原样抛出")
    void errorPropagates() {
        assertThrows(AssertionError.class, () -> RemoteFallback.run("test.error", () -> {
            throw new AssertionError("bug");
        }));
        assertEquals(0, RemoteFallback.count("test.error"));
    }

    @Test
    @DisplayName("在调用线程中执行（不切换线程），线程变量等上下文与直接调用相同")
    void runsInCallerThread() {
        Thread caller = Thread.currentThread();
        assertEquals(caller, RemoteFallback.call("test.thread", Thread::currentThread, (Thread) null));
    }

    @Test
    @DisplayName("名称必填")
    void requiresName() {
        assertThrows(IllegalArgumentException.class, () -> RemoteFallback.run(" ", () -> {
        }));
    }

}
