package org.tuoyu.mes.andon.escalation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.mes.andon.service.IMesAndonEscalationService;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("安灯超时检查的定时执行：按配置的间隔调度，单次失败不影响后续，停止时取消")
class AndonEscalationJobTest {

    private final IMesAndonEscalationService service = mock(IMesAndonEscalationService.class);
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private final AndonEscalationProperties properties = new AndonEscalationProperties();
    private final AndonEscalationJob job = new AndonEscalationJob(service, properties, executor);

    @Test
    @DisplayName("启动时按固定间隔调度（默认启动 30 秒后开始、每分钟一次），停止时取消")
    void schedulesAndCancels() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any());

        job.start();

        assertThat(job.isRunning()).isTrue();
        verify(executor).scheduleWithFixedDelay(any(Runnable.class), eq(30_000L), eq(60_000L), eq(TimeUnit.MILLISECONDS));
        job.stop();
        assertThat(job.isRunning()).isFalse();
        verify(future).cancel(false);
    }

    @Test
    @DisplayName("关闭时不调度")
    void disabledDoesNotSchedule() {
        properties.setEnabled(false);

        job.start();

        assertThat(job.isRunning()).isFalse();
        verify(executor, never()).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("配置无效（间隔太短、批量超出范围）时拒绝启动并说明原因")
    void rejectsInvalidConfiguration() {
        properties.setInterval(Duration.ofSeconds(1));
        assertThatThrownBy(job::start).hasMessageContaining("interval");

        properties.setInterval(Duration.ofMinutes(1));
        properties.setBatchSize(0);
        assertThatThrownBy(properties::validate).hasMessageContaining("batch-size");

        properties.setBatchSize(200);
        properties.setInitialDelay(Duration.ofSeconds(-1));
        assertThatThrownBy(properties::validate).hasMessageContaining("initial-delay");
    }

    @Test
    @DisplayName("单次检查失败只记录日志，不抛出（否则线程池会停止后续调度）")
    void runOnceSwallowsFailures() {
        when(service.checkOverdue()).thenThrow(new IllegalStateException("数据库不可用"));
        assertThatCode(job::runOnce).doesNotThrowAnyException();

        doReturn(3).when(service).checkOverdue();
        assertThatCode(job::runOnce).doesNotThrowAnyException();
    }

}
