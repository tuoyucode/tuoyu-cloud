package org.tuoyu.common.core.utils;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.ContextPropagators;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ThreadUtils：在虚拟线程中并行执行一批任务")
class ThreadUtilsTest {

    private static final ThreadLocal<String> VALUE = new ThreadLocal<>();

    private ApplicationContext previous;

    @BeforeEach
    void savePrevious() {
        previous = SpringUtil.getApplicationContext();
    }

    @AfterEach
    void restore() {
        new SpringUtil().setApplicationContext(previous);
        VALUE.remove();
        // 清除测试中可能留下的中断标记
        Thread.interrupted();
    }

    @Test
    @DisplayName("结果按提交顺序排列，任务在虚拟线程中执行")
    void resultsInOrder() {
        assertThat(ThreadUtils.virtualSubmitAll(() -> 1, () -> 2, () -> 3)).containsExactly(1, 2, 3);
        assertThat(ThreadUtils.virtualSubmitAll(() -> Thread.currentThread().isVirtual())).containsExactly(true);

        List<Supplier<String>> suppliers = List.of(() -> "a", () -> "b");
        List<String> results = ThreadUtils.virtualSubmitAll(suppliers);
        assertThat(results).containsExactly("a", "b");
        results.add("c");
        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("全部任务结束后才返回")
    void waitsForAll() {
        AtomicBoolean first = new AtomicBoolean();
        AtomicBoolean second = new AtomicBoolean();
        ThreadUtils.virtualInvokeAll(() -> {
            sleep(100);
            first.set(true);
        }, () -> {
            sleep(200);
            second.set(true);
        });
        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }

    @Test
    @DisplayName("任务失败：抛出带原因的 RuntimeException，消息含原因的消息；抛出前等待其余任务结束")
    void failure() {
        AtomicBoolean slowFinished = new AtomicBoolean();
        assertThatThrownBy(() -> ThreadUtils.virtualInvokeAll(
            () -> {
                throw new IllegalStateException("task-failed");
            },
            () -> {
                sleep(200);
                slowFinished.set(true);
            }))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("线程执行异常：task-failed")
            .hasCauseExactlyInstanceOf(IllegalStateException.class);
        assertThat(slowFinished).isTrue();

        assertThatThrownBy(() -> ThreadUtils.virtualSubmitAll(() -> 1, () -> {
            throw new IllegalArgumentException("bad");
        }))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("线程执行异常：bad")
            .hasCauseExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("等待时调用线程被中断：抛出 RuntimeException 并保留中断标记")
    void interrupted() {
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> ThreadUtils.virtualSubmitAll(() -> {
            sleep(100);
            return 1;
        }))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("线程执行被中断")
            .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("容器中有 ContextPropagators 时，任务线程中恢复提交时的上下文")
    void propagatesContext() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("contextPropagators",
            new ContextPropagators(List.of(new TestPropagator())));
        context.refresh();
        new SpringUtil().setApplicationContext(context);

        VALUE.set("tenant-a");
        assertThat(ThreadUtils.virtualSubmitAll(VALUE::get)).containsExactly("tenant-a");
        AtomicReference<String> seen = new AtomicReference<>();
        ThreadUtils.virtualInvokeAll(() -> seen.set(VALUE.get()));
        assertThat(seen).hasValue("tenant-a");
    }

    @Test
    @DisplayName("不在容器中时直接执行，不透传上下文")
    void withoutContainer() {
        new SpringUtil().setApplicationContext(null);
        VALUE.set("tenant-a");
        List<String> results = ThreadUtils.virtualSubmitAll(VALUE::get);
        assertThat(results).hasSize(1).containsNull();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 透传一个线程变量
     */
    private static final class TestPropagator implements ContextPropagator {

        private static final String KEY = "test.value";

        @Override
        public String name() {
            return "test";
        }

        @Override
        public Map<String, String> capture() {
            String value = VALUE.get();
            return value == null ? Map.of() : Map.of(KEY, value);
        }

        @Override
        public Scope restore(Map<String, String> values) {
            String before = VALUE.get();
            String value = values.get(KEY);
            if (value == null) {
                VALUE.remove();
            } else {
                VALUE.set(value);
            }
            return () -> {
                if (before == null) {
                    VALUE.remove();
                } else {
                    VALUE.set(before);
                }
            };
        }
    }
}
