package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.tenant.core.TenantDirectory;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.mes.equipment.config.MesMaintenanceProperties;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("定时生成保养工单：跨租户只查主键与租户，再逐个在所属租户中生成")
class MesMaintenanceDueJobTest {

    static {
        MesTestSupport.init();
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final MesMaintenancePlanMapper planMapper = MesTestSupport.mapper(MesMaintenancePlanMapper.class);
    private final MaintenanceOrderGenerator generator = mock(MaintenanceOrderGenerator.class);
    private final TenantDirectory directory = mock(TenantDirectory.class);
    private final MesMaintenanceProperties properties = new MesMaintenanceProperties();

    private MesMaintenanceDueJob job(boolean withDirectory) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (withDirectory) {
            beans.addBean("tenantDirectory", directory);
        }
        MesMaintenanceDueJob job = new MesMaintenanceDueJob(planMapper, generator, properties,
            beans.getBeanProvider(TenantDirectory.class));
        job.setClock(Clock.fixed(TODAY.atTime(1, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        return job;
    }

    private static MesMaintenancePlan due(long planId, String tenantId) {
        MesMaintenancePlan plan = new MesMaintenancePlan();
        plan.setPlanId(planId);
        plan.setTenantId(tenantId);
        return plan;
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.tenant(false);
    }

    @Test
    @DisplayName("开启多租户：在忽略租户的条件下查出到期计划，跳过未启用的租户，在所属租户中逐个生成；单个失败不影响其他")
    void perTenant() {
        MesTestSupport.tenant(true);
        List<Boolean> ignoredWhenListing = new ArrayList<>();
        when(planMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            ignoredWhenListing.add(TenantHelper.isIgnoring());
            return List.of(due(1L, "t1"), due(2L, "t2"), due(3L, "t1"), due(4L, "t3"));
        });
        when(directory.activeTenantIds()).thenReturn(List.of("t1", "t2"));
        List<String> tenants = new ArrayList<>();
        when(generator.generate(any(), eq(TODAY))).thenAnswer(invocation -> {
            tenants.add(TenantHelper.currentTenantId());
            if (invocation.<Long>getArgument(0) == 2L) {
                throw new IllegalStateException("模拟失败");
            }
            return true;
        });

        assertThat(job(true).runOnce()).isEqualTo(2);

        assertThat(ignoredWhenListing).containsExactly(true);
        assertThat(tenants).containsExactly("t1", "t1", "t2");
        verify(generator, never()).generate(eq(4L), any());
    }

    @Test
    @DisplayName("开启多租户但没有租户清单时拒绝执行（不会在未知的租户中生成）；没有到期计划时直接返回")
    void requiresDirectory() {
        MesTestSupport.tenant(true);
        when(planMapper.selectList(any(Wrapper.class))).thenReturn(List.of(due(1L, "t1")));
        assertThatThrownBy(() -> job(false).runOnce()).hasMessageContaining("租户清单");

        when(planMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThat(job(false).runOnce()).isZero();
    }

    @Test
    @DisplayName("未开启多租户：不过滤租户，全部计划都处理")
    void withoutTenancy() {
        when(planMapper.selectList(any(Wrapper.class))).thenReturn(List.of(due(1L, "default"), due(2L, "default")));
        when(generator.generate(any(), eq(TODAY))).thenReturn(true, false);

        assertThat(job(false).runOnce()).isEqualTo(1);
        verify(directory, never()).activeTenantIds();
    }

    @Test
    @DisplayName("调度：关闭时不启动；开启时启动单线程调度器，停止后释放")
    void lifecycle() {
        properties.getGenerate().setEnabled(false);
        MesMaintenanceDueJob disabled = job(false);
        disabled.start();
        assertThat(disabled.isRunning()).isFalse();

        properties.getGenerate().setEnabled(true);
        properties.getGenerate().setInitialDelay(Duration.ofHours(1));
        properties.getGenerate().setInterval(Duration.ofSeconds(1));
        MesMaintenanceDueJob enabled = job(false);
        enabled.start();
        assertThat(enabled.isRunning()).isTrue();
        enabled.stop();
        assertThat(enabled.isRunning()).isFalse();
    }

}
