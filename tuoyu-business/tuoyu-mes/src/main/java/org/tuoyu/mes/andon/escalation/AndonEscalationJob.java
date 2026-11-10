package org.tuoyu.mes.andon.escalation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.tuoyu.mes.andon.service.IMesAndonEscalationService;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 定时执行安灯超时检查。
 * <p>
 * 使用框架的 {@code scheduledExecutorService}，不依赖 {@code @EnableScheduling}：微服务与单体中都能执行，
 * 也不会为整个应用开启 Spring 的定时任务。任务按固定间隔执行（上一次结束后再等一个间隔），单次失败只记录日志，
 * 不影响后续执行（周期任务抛出异常后线程池会停止调度它）。服务停止时取消调度，正在执行的一次会执行完。
 * <p>
 * 任务中没有登录用户与租户，由 {@link IMesAndonEscalationService} 逐个事件指定租户。
 *
 * @author tuoyu
 */
@Slf4j
@Component
public class AndonEscalationJob implements SmartLifecycle {

    private final IMesAndonEscalationService escalationService;

    private final AndonEscalationProperties properties;

    private final ScheduledExecutorService executor;

    private volatile ScheduledFuture<?> future;

    public AndonEscalationJob(IMesAndonEscalationService escalationService, AndonEscalationProperties properties,
                              @Qualifier("scheduledExecutorService") ScheduledExecutorService executor) {
        this.escalationService = escalationService;
        this.properties = properties;
        this.executor = executor;
    }

    @Override
    public synchronized void start() {
        if (future != null) {
            return;
        }
        if (!properties.isEnabled()) {
            log.info("安灯超时检查未在本服务中开启（tuoyu.mes.andon.escalation.enabled=false）");
            return;
        }
        properties.validate();
        future = executor.scheduleWithFixedDelay(this::runOnce, properties.getInitialDelay().toMillis(),
            properties.getInterval().toMillis(), TimeUnit.MILLISECONDS);
        log.info("安灯超时检查已启动，间隔 {} 秒", properties.getInterval().toSeconds());
    }

    /**
     * 执行一次检查；异常只记录日志，下一次照常执行
     */
    void runOnce() {
        try {
            int escalated = escalationService.checkOverdue();
            if (escalated > 0) {
                log.info("安灯超时检查：升级 {} 个事件", escalated);
            }
        } catch (RuntimeException e) {
            log.warn("安灯超时检查失败，下次继续：{}", e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        ScheduledFuture<?> current = future;
        future = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    @Override
    public boolean isRunning() {
        return future != null;
    }

}
