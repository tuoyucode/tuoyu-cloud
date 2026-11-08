package org.tuoyu.common.observability.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.web.server.GracefulShutdownResult;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 停机的前半段：在 Spring 开始停止各个组件<b>之前</b>，先下线、再排空 HTTP 请求。
 * <ol>
 *     <li>健康检查改为“不接收流量”（readiness 为 REFUSING_TRAFFIC，/actuator/health 返回 503）；</li>
 *     <li>执行全部 {@link OfflineAction}（从注册中心注销等）；</li>
 *     <li>等待 {@code tuoyu.shutdown.offline-delay}：网关的负载均衡缓存、反向代理的健康检查需要时间发现本实例已下线，
 *     这段时间内仍正常处理新到的请求；</li>
 *     <li>Web 容器停止接收新请求，等待处理中的请求完成（最多 {@code tuoyu.shutdown.timeout}）。</li>
 * </ol>
 * 它在 {@link ContextClosedEvent} 中最先执行。Dubbo 在同一个事件中（排在最后）注销并关闭，
 * 所以处理中的 HTTP 请求在排空期间仍可以发起 Dubbo 调用；之后 Spring 再停止消息消费、线程池等组件。
 *
 * @author tuoyu
 */
public class GracefulShutdownCoordinator implements ApplicationListener<ContextClosedEvent>, ApplicationContextAware, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);

    private final GracefulShutdownProperties properties;

    private final List<OfflineAction> offlineActions;

    private final Supplier<WebServer> webServer;

    private final AtomicBoolean started = new AtomicBoolean();

    private ApplicationContext context;

    /**
     * @param properties     停机配置
     * @param offlineActions 下线动作
     * @param webServer      需要排空的 Web 容器；没有或未开启 server.shutdown=graceful 时返回 null
     */
    public GracefulShutdownCoordinator(GracefulShutdownProperties properties, List<OfflineAction> offlineActions,
                                       Supplier<WebServer> webServer) {
        this.properties = properties;
        this.offlineActions = List.copyOf(offlineActions);
        this.webServer = webServer;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.context = applicationContext;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        // 只处理本容器的关闭（子容器关闭时也会收到事件），且只执行一次
        if (event.getApplicationContext() != context || !started.compareAndSet(false, true)) {
            return;
        }
        long begin = System.nanoTime();
        log.info("[停机] 开始优雅停机：先下线，{} 秒后停止接收新请求，最多等待 {} 秒处理中的请求",
            properties.getOfflineDelay().toSeconds(), properties.getTimeout().toSeconds());
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
        for (OfflineAction action : offlineActions) {
            try {
                action.offline();
                log.info("[停机] 已下线：{}", action.name());
            } catch (RuntimeException e) {
                log.warn("[停机] 下线失败（继续停机）：{}，原因：{}", action.name(), e.toString());
            }
        }
        sleep(properties.getOfflineDelay());
        drain();
        log.info("[停机] 下线与请求排空完成，用时 {} 毫秒；接下来停止 RPC、消息消费与线程池",
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
    }

    /**
     * Web 容器停止接收新请求，等待处理中的请求完成。之后 Spring 自身的优雅停机阶段会再执行一次，届时已没有请求，立即完成
     */
    private void drain() {
        WebServer server = webServer.get();
        if (server == null) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<GracefulShutdownResult> result = new AtomicReference<>();
        server.shutDownGracefully(outcome -> {
            result.set(outcome);
            latch.countDown();
        });
        try {
            if (!latch.await(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("[停机] 等待处理中的请求超时（{} 秒），继续停机", properties.getTimeout().toSeconds());
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (result.get() == GracefulShutdownResult.REQUESTS_ACTIVE) {
            log.warn("[停机] 仍有请求未完成，继续停机");
        }
    }

    private static void sleep(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
