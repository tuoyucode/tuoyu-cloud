package org.tuoyu.common.rpc.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * 需要容错的跨模块调用：调用失败时返回降级值，记录日志与计数，不影响主流程。
 * <pre>{@code
 * String url = RemoteFallback.call("resource.file-url", () -> remoteFileService.selectUrlByIds(ids), "");
 * RemoteFallback.run("resource.message.welcome", () -> remoteMessageService.publishMessage(userIds, text));
 * }</pre>
 * 规则：
 * <ul>
 *     <li><b>只用于结果可有可无的调用</b>（提示消息、通知邮件、显示用的翻译）。权限、数据权限、租户、登录、写入业务数据等
 *     调用不能降级，异常必须原样到达调用方；</li>
 *     <li>在调用线程中执行，不切换线程：登录、租户等上下文与直接调用完全相同，两种部署方式的语义一致。
 *     任何 {@link RuntimeException}（提供方的业务异常、微服务中的超时与网络错误）都触发降级；{@link Error} 不降级；</li>
 *     <li>超时：微服务中由 Dubbo 的调用超时负责（{@code TUOYU_DUBBO_TIMEOUT}，默认 3 秒），超时异常触发降级；
 *     单体中是进程内调用，没有超时；</li>
 *     <li>日志：同一名称每分钟最多一条 WARN（附带期间的失败次数），其余为 DEBUG，避免依赖的服务故障时刷屏；</li>
 *     <li>计数：{@link #counts()}；有 Micrometer 时同时记入指标 {@value #METRIC}（标签 {@code call}），可在 Prometheus 中告警。</li>
 * </ul>
 *
 * @author tuoyu
 */
public final class RemoteFallback {

    /**
     * Micrometer 指标名
     */
    public static final String METRIC = "tuoyu.remote.fallback";

    private static final Logger log = LoggerFactory.getLogger(RemoteFallback.class);

    private static final long WARN_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private static final boolean MICROMETER_PRESENT =
        ClassUtils.isPresent("io.micrometer.core.instrument.Metrics", RemoteFallback.class.getClassLoader());

    private static final Map<String, Stats> STATS = new ConcurrentHashMap<>();

    private RemoteFallback() {
    }

    /**
     * 执行调用，失败时返回降级值
     *
     * @param name     调用名称，用于日志与计数，建议 {@code 提供方模块.用途}，例如 {@code resource.file-url}
     * @param call     调用
     * @param fallback 失败时的返回值
     */
    public static <T> T call(String name, Supplier<T> call, T fallback) {
        requireName(name);
        Objects.requireNonNull(call, "call");
        try {
            return call.get();
        } catch (RuntimeException e) {
            record(name, e);
            return fallback;
        }
    }

    /**
     * 执行调用，失败时计算降级值（降级值需要计算时使用）
     *
     * @param name     调用名称
     * @param call     调用
     * @param fallback 失败时计算返回值，它抛出的异常不再降级
     */
    public static <T> T callOrElseGet(String name, Supplier<T> call, Supplier<? extends T> fallback) {
        requireName(name);
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(fallback, "fallback");
        try {
            return call.get();
        } catch (RuntimeException e) {
            record(name, e);
            return fallback.get();
        }
    }

    /**
     * 执行没有返回值的调用，失败时忽略（记录日志与计数）
     *
     * @param name 调用名称
     * @param call 调用
     */
    public static void run(String name, Runnable call) {
        requireName(name);
        Objects.requireNonNull(call, "call");
        try {
            call.run();
        } catch (RuntimeException e) {
            record(name, e);
        }
    }

    /**
     * @return 各调用名称自启动以来的降级次数（按名称排序）
     */
    public static Map<String, Long> counts() {
        Map<String, Long> counts = new TreeMap<>();
        STATS.forEach((name, stats) -> counts.put(name, stats.total.sum()));
        return counts;
    }

    /**
     * @return 某个调用名称自启动以来的降级次数
     */
    public static long count(String name) {
        Stats stats = STATS.get(name);
        return stats == null ? 0 : stats.total.sum();
    }

    /**
     * 只供测试使用：清空计数
     */
    static void reset() {
        STATS.clear();
    }

    private static void record(String name, RuntimeException e) {
        Stats stats = STATS.computeIfAbsent(name, key -> new Stats());
        stats.total.increment();
        stats.sinceLastWarn.increment();
        if (MICROMETER_PRESENT) {
            Metrics.increment(name);
        }
        long now = System.nanoTime();
        long last = stats.lastWarnAt.get();
        if ((last == 0 || now - last >= WARN_INTERVAL_NANOS) && stats.lastWarnAt.compareAndSet(last, now)) {
            long failures = stats.sinceLastWarn.sumThenReset();
            log.warn("跨模块调用 [{}] 失败，已降级（自上一条警告以来共 {} 次）：{}", name, failures, e.toString());
        } else {
            log.debug("跨模块调用 [{}] 失败，已降级", name, e);
        }
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("降级调用必须有名称，用于日志与计数");
        }
    }

    private static final class Stats {

        private final LongAdder total = new LongAdder();

        private final LongAdder sinceLastWarn = new LongAdder();

        private final AtomicLong lastWarnAt = new AtomicLong();

    }

    /**
     * 隔离 Micrometer 的类：没有 Micrometer 时不加载
     */
    private static final class Metrics {

        private static void increment(String name) {
            // Spring Boot 默认把自动配置的指标注册表加入全局注册表
            io.micrometer.core.instrument.Metrics.counter(METRIC, "call", name).increment();
        }

    }

}
