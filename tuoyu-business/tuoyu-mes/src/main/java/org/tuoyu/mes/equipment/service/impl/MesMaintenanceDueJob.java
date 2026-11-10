package org.tuoyu.mes.equipment.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.tenant.core.TenantDirectory;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.mes.equipment.config.MesMaintenanceProperties;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定时生成到期的保养工单（所有租户）。
 * <p>
 * 跨租户的做法：先在忽略租户的代码块中只查出已到生成日期的启用计划的主键与租户，不读取其他内容；
 * 再按租户用 {@link TenantHelper#dynamic(String, java.util.function.Supplier)} 逐个计划在该租户中生成
 * （{@link MaintenanceOrderGenerator}，每个计划一个事务），读写都带该租户的条件。开启多租户时只处理启用且未过期的租户。
 * <p>
 * 多实例部署时每个实例都会执行，同一计划的同一到期日期只生成一张工单（由生成时的条件更新保证）。
 * 任务在本服务自己的单线程调度器中执行，不依赖应用是否开启 {@code @EnableScheduling}；间隔等见 {@link MesMaintenanceProperties}。
 *
 * @author tuoyu
 */
@Slf4j
@Component
public class MesMaintenanceDueJob implements SmartLifecycle {

    private static final Duration MIN_INTERVAL = Duration.ofMinutes(1);

    private final MesMaintenancePlanMapper planMapper;

    private final MaintenanceOrderGenerator generator;

    private final MesMaintenanceProperties properties;

    private final ObjectProvider<TenantDirectory> tenantDirectory;

    private Clock clock = Clock.systemDefaultZone();

    private volatile ScheduledExecutorService scheduler;

    public MesMaintenanceDueJob(MesMaintenancePlanMapper planMapper, MaintenanceOrderGenerator generator,
                                MesMaintenanceProperties properties, ObjectProvider<TenantDirectory> tenantDirectory) {
        this.planMapper = planMapper;
        this.generator = generator;
        this.properties = properties;
        this.tenantDirectory = tenantDirectory;
    }

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * 执行一次：为所有（启用的）租户中已到生成日期的计划生成保养工单
     *
     * @return 生成的工单数
     */
    public int runOnce() {
        LocalDate today = LocalDate.now(clock);
        // 只取主键与租户，用于随后按租户逐个处理；不把计划的内容带出租户
        List<MesMaintenancePlan> due = TenantHelper.ignore(() -> planMapper.selectList(QueryBuilder.lambda(MesMaintenancePlan.class)
            .select(MesMaintenancePlan::getPlanId, MesMaintenancePlan::getTenantId)
            .eq(MesMaintenancePlan::getStatus, SystemConstants.NORMAL)
            .isNotNull(MesMaintenancePlan::getGenerateFromDate)
            .le(MesMaintenancePlan::getGenerateFromDate, today)
            .orderByAsc(MesMaintenancePlan::getPlanId)
            .build()));
        if (due.isEmpty()) {
            return 0;
        }
        Map<String, List<Long>> byTenant = new LinkedHashMap<>();
        for (MesMaintenancePlan plan : due) {
            byTenant.computeIfAbsent(plan.getTenantId(), key -> new ArrayList<>()).add(plan.getPlanId());
        }
        Set<String> active = activeTenants();
        int generated = 0;
        int failed = 0;
        for (Map.Entry<String, List<Long>> entry : byTenant.entrySet()) {
            String tenantId = entry.getKey();
            if (active != null && !active.contains(tenantId)) {
                log.debug("租户 {} 未启用或已过期，跳过它的保养计划", tenantId);
                continue;
            }
            for (Long planId : entry.getValue()) {
                try {
                    if (Boolean.TRUE.equals(TenantHelper.dynamic(tenantId, () -> generator.generate(planId, today)))) {
                        generated++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("保养计划 {}（租户 {}）生成到期工单失败", planId, tenantId, e);
                }
            }
        }
        if (generated > 0 || failed > 0) {
            log.info("生成到期的保养工单：{} 张，失败 {} 个计划", generated, failed);
        }
        return generated;
    }

    /**
     * 开启多租户时为启用且未过期的租户；未开启时为 null（不过滤）
     */
    private Set<String> activeTenants() {
        if (!TenantHelper.isEnabled()) {
            return null;
        }
        TenantDirectory directory = tenantDirectory.getIfAvailable();
        if (directory == null) {
            throw new IllegalStateException("开启多租户时生成保养工单需要租户清单（TenantDirectory）");
        }
        return new HashSet<>(TenantHelper.ignore(directory::activeTenantIds));
    }

    private void runSafely() {
        try {
            runOnce();
        } catch (RuntimeException e) {
            // 调度器中的任务抛出异常后不会再执行，这里记录后等待下一次
            log.warn("生成到期的保养工单失败，等待下一次执行", e);
        }
    }

    @Override
    public void start() {
        MesMaintenanceProperties.Generate config = properties.getGenerate();
        if (!config.isEnabled() || scheduler != null) {
            return;
        }
        Duration interval = config.getInterval() == null || config.getInterval().compareTo(MIN_INTERVAL) < 0
            ? MIN_INTERVAL : config.getInterval();
        Duration delay = config.getInitialDelay() == null || config.getInitialDelay().isNegative()
            ? Duration.ZERO : config.getInitialDelay();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "mes-maintenance-due");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::runSafely, delay.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        scheduler = executor;
    }

    @Override
    public void stop() {
        ScheduledExecutorService executor = scheduler;
        scheduler = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return scheduler != null;
    }

}
