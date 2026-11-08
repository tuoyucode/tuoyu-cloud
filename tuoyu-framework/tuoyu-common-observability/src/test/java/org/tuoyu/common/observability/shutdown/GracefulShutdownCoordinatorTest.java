package org.tuoyu.common.observability.shutdown;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.web.server.GracefulShutdownCallback;
import org.springframework.boot.web.server.GracefulShutdownResult;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("优雅停机：先下线、再排空请求，然后才停止 RPC 等组件")
class GracefulShutdownCoordinatorTest {

    @Test
    @DisplayName("顺序：就绪状态改为不接收流量 → 下线 → 等待 → 排空 Web 请求 → 其他关闭监听（如 Dubbo）")
    void runsInOrder() {
        List<String> steps = new CopyOnWriteArrayList<>();
        GracefulShutdownProperties properties = new GracefulShutdownProperties();
        properties.setOfflineDelay(Duration.ofMillis(200));
        long[] offlineAt = new long[1];
        long[] drainAt = new long[1];

        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GracefulShutdownCoordinator.class, () -> new GracefulShutdownCoordinator(properties,
            List.of(action("注册中心", () -> {
                steps.add("offline");
                offlineAt[0] = System.nanoTime();
            })),
            () -> new FakeWebServer(() -> {
                steps.add("drain");
                drainAt[0] = System.nanoTime();
            }, GracefulShutdownResult.IDLE)));
        context.registerBean("readiness", ReadinessListener.class, () -> new ReadinessListener(steps));
        context.registerBean("dubboLike", LastClosedListener.class, () -> new LastClosedListener(steps));
        context.refresh();
        context.close();

        assertEquals(List.of("readiness:REFUSING_TRAFFIC", "offline", "drain", "dubbo"), steps);
        assertTrue(drainAt[0] - offlineAt[0] >= Duration.ofMillis(200).toNanos(), "下线后等待 offline-delay 再停止接收请求");
    }

    @Test
    @DisplayName("下线动作失败不中断停机；没有 Web 容器时跳过排空")
    void continuesWhenOfflineFails() {
        List<String> steps = new CopyOnWriteArrayList<>();
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(GracefulShutdownCoordinator.class, () -> new GracefulShutdownCoordinator(new GracefulShutdownProperties(),
            List.of(action("失败的", () -> {
                throw new IllegalStateException("注册中心不可用");
            }), action("成功的", () -> steps.add("offline"))),
            () -> null));
        context.registerBean("dubboLike", LastClosedListener.class, () -> new LastClosedListener(steps));
        context.refresh();
        context.close();

        assertEquals(List.of("offline", "dubbo"), steps);
    }

    @Test
    @DisplayName("请求排空超时：记录警告后继续停机，不无限等待")
    void drainTimeout() {
        GracefulShutdownProperties properties = new GracefulShutdownProperties();
        properties.setTimeout(Duration.ofMillis(100));
        List<String> steps = new CopyOnWriteArrayList<>();
        GenericApplicationContext context = new GenericApplicationContext();
        // 永远不回调的 Web 容器
        context.registerBean(GracefulShutdownCoordinator.class, () -> new GracefulShutdownCoordinator(properties, List.of(),
            () -> new FakeWebServer(() -> steps.add("drain"), null)));
        context.registerBean("dubboLike", LastClosedListener.class, () -> new LastClosedListener(steps));
        context.refresh();
        long begin = System.nanoTime();
        context.close();

        assertEquals(List.of("drain", "dubbo"), steps);
        assertTrue(System.nanoTime() - begin < Duration.ofSeconds(5).toNanos());
    }

    private static OfflineAction action(String name, Runnable runnable) {
        return new OfflineAction() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void offline() {
                runnable.run();
            }
        };
    }

    /**
     * 记录就绪状态的变化
     */
    static class ReadinessListener implements ApplicationListener<AvailabilityChangeEvent<ReadinessState>> {

        private final List<String> steps;

        ReadinessListener(List<String> steps) {
            this.steps = steps;
        }

        @Override
        public void onApplicationEvent(AvailabilityChangeEvent<ReadinessState> event) {
            steps.add("readiness:" + event.getState());
        }

    }

    /**
     * 模拟 Dubbo：在容器关闭事件中最后执行
     */
    static class LastClosedListener implements ApplicationListener<ContextClosedEvent>, Ordered {

        private final List<String> steps;

        LastClosedListener(List<String> steps) {
            this.steps = steps;
        }

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            steps.add("dubbo");
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }

    }

    /**
     * 模拟 Web 容器：排空时执行给定动作，按给定结果回调（null 表示不回调）
     */
    record FakeWebServer(Runnable onDrain, GracefulShutdownResult result) implements WebServer {

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public int getPort() {
            return 0;
        }

        @Override
        public void shutDownGracefully(GracefulShutdownCallback callback) {
            onDrain.run();
            if (result != null) {
                callback.shutdownComplete(result);
            }
        }

    }

}
