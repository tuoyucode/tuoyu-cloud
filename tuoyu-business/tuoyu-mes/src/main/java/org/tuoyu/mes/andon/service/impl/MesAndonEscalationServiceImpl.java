package org.tuoyu.mes.andon.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.common.tenant.core.TenantDirectory;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesAndonRule;
import org.tuoyu.mes.andon.enums.AndonLevel;
import org.tuoyu.mes.andon.escalation.AndonEscalationProperties;
import org.tuoyu.mes.andon.escalation.AndonEscalationRules;
import org.tuoyu.mes.andon.escalation.AndonEscalationRules.Deadline;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.andon.mapper.MesAndonRuleMapper;
import org.tuoyu.mes.andon.service.IMesAndonEscalationService;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.resource.api.RemoteMessageService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 安灯超时升级。
 * <p>
 * 每次检查分两步，都先在忽略租户的条件下只查出事件的主键与租户，再对每个事件在它自己的租户中、单独的事务里处理
 * （一个事件失败不影响其他事件）。开启多租户时只处理启用且未过期的租户（{@link TenantDirectory}），
 * 这个条件写在查询中，停用租户的事件不会占用每批的数量：
 * <ol>
 *     <li>计算截止时间：为截止时间待计算的未解决事件匹配升级规则（按异常类型与紧急程度），
 *     截止时间 = 上报时间 + 规则中的时限；没有适用的规则时不检查。</li>
 *     <li>超时升级：超过截止时间、还没有为这个时限升级的事件，提高紧急程度（已是最高级别或规则只提醒时不变）、
 *     记录升级次数与时间、写状态履历（动作 escalate），事务提交后提醒规则中的人员与当前处理人。</li>
 * </ol>
 * 多个实例同时检查时不加锁：两步都是带原值条件的更新（截止时间以“待计算”与原级别为条件；升级以原级别、原升级次数、
 * 该时限未升级与状态为条件），影响 0 行说明已被其他实例处理或事件已变化，跳过即可。每个时限有自己的升级时间字段，
 * 同一时限只升级、提醒一次。
 * <p>
 * 升级只改紧急程度，不改截止时间：截止时间来自上报时适用的规则（规则变化时重新计算），升级后的提醒对象也取这条规则。
 * 提醒是可有可无的调用，经 {@link RemoteFallback} 降级，推送失败不影响升级。
 *
 * @author tuoyu
 */
@Slf4j
@Service
public class MesAndonEscalationServiceImpl implements IMesAndonEscalationService {

    static final String PUSH_FALLBACK = "resource.message.andon-escalation";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MesAndonEventMapper eventMapper;
    private final MesAndonRuleMapper ruleMapper;
    private final IMesStatusLogService statusLogService;
    private final RemoteMessageService remoteMessageService;
    private final AndonEscalationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ObjectProvider<TenantDirectory> tenantDirectory;

    private Clock clock = Clock.systemDefaultZone();

    public MesAndonEscalationServiceImpl(MesAndonEventMapper eventMapper, MesAndonRuleMapper ruleMapper,
                                         IMesStatusLogService statusLogService, RemoteMessageService remoteMessageService,
                                         AndonEscalationProperties properties, PlatformTransactionManager transactionManager,
                                         ObjectProvider<TenantDirectory> tenantDirectory) {
        this.eventMapper = eventMapper;
        this.ruleMapper = ruleMapper;
        this.statusLogService = statusLogService;
        this.remoteMessageService = remoteMessageService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.tenantDirectory = tenantDirectory;
    }

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public int checkOverdue() {
        LocalDateTime now = LocalDateTime.now(clock);
        int batch = properties.getBatchSize();
        List<String> tenants = activeTenants();
        if (tenants != null && tenants.isEmpty()) {
            log.debug("没有启用的租户，跳过安灯超时检查");
            return 0;
        }
        // 跨租户只查主键与租户，处理时再在各自的租户中读取完整数据
        List<MesAndonEvent> pending = TenantHelper.ignore(() -> eventMapper.selectDeadlinePending(tenants, batch));
        Map<String, List<MesAndonRule>> rulesByTenant = new HashMap<>();
        for (MesAndonEvent ref : pending) {
            inTenant(ref, "计算截止时间", () -> computeDeadlines(ref.getEventId(),
                rulesByTenant.computeIfAbsent(String.valueOf(ref.getTenantId()), key -> enabledRules())));
        }
        List<MesAndonEvent> overdue = TenantHelper.ignore(() -> eventMapper.selectOverdue(tenants, now, batch));
        int escalated = 0;
        for (MesAndonEvent ref : overdue) {
            if (inTenant(ref, "超时升级", () -> escalate(ref.getEventId(), now))) {
                escalated++;
            }
        }
        return escalated;
    }

    /**
     * 开启多租户时为启用且未过期的租户（可能为空）；未开启时为 null（不过滤）
     */
    private List<String> activeTenants() {
        if (!TenantHelper.isEnabled()) {
            return null;
        }
        TenantDirectory directory = tenantDirectory.getIfAvailable();
        if (directory == null) {
            throw new IllegalStateException("开启多租户时安灯超时检查需要租户清单（TenantDirectory）");
        }
        List<String> tenants = TenantHelper.ignore(directory::activeTenantIds);
        return tenants == null ? List.of() : List.copyOf(new LinkedHashSet<>(tenants));
    }

    /**
     * 在事件所属的租户中、单独的事务里处理一个事件；失败只记录日志，继续处理其他事件
     */
    private boolean inTenant(MesAndonEvent ref, String step, Supplier<Boolean> action) {
        try {
            Boolean done = TenantHelper.dynamic(ref.getTenantId(),
                (Supplier<Boolean>) () -> transactionTemplate.execute(status -> action.get()));
            return Boolean.TRUE.equals(done);
        } catch (RuntimeException e) {
            log.warn("安灯事件 {} {}失败：{}", ref.getEventId(), step, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 当前租户中启用的升级规则，按主键排序（每次检查中每个租户只查一次）
     */
    private List<MesAndonRule> enabledRules() {
        return ruleMapper.selectList(QueryBuilder.lambda(MesAndonRule.class)
            .eq(MesAndonRule::getStatus, SystemConstants.NORMAL)
            .orderByAsc(MesAndonRule::getAndonRuleId)
            .build());
    }

    /**
     * 按适用的规则计算截止时间；以“待计算”与原级别为条件更新，已被其他实例计算或级别已变化时跳过
     *
     * @return 是否由本次计算
     */
    boolean computeDeadlines(Long eventId, List<MesAndonRule> rules) {
        MesAndonEvent event = eventMapper.selectById(eventId);
        if (event == null || !AndonEscalationRules.UNCHECKED.equals(event.getDeadlineChecked())
            || !AndonEscalationRules.RESOLVE_WATCHED.contains(event.getEventStatus())) {
            return false;
        }
        MesAndonRule rule = AndonEscalationRules.match(rules, event.getEventType(), event.getEventLevel());
        return eventMapper.update(null, Wrappers.<MesAndonEvent>lambdaUpdate()
            .set(MesAndonEvent::getAndonRuleId, rule == null ? null : rule.getAndonRuleId())
            .set(MesAndonEvent::getResponseDueTime, AndonEscalationRules.dueTime(event.getReportTime(), rule, Deadline.RESPONSE))
            .set(MesAndonEvent::getResolveDueTime, AndonEscalationRules.dueTime(event.getReportTime(), rule, Deadline.RESOLVE))
            .set(MesAndonEvent::getDeadlineChecked, AndonEscalationRules.CHECKED)
            .eq(MesAndonEvent::getEventId, eventId)
            .eq(MesAndonEvent::getDeadlineChecked, AndonEscalationRules.UNCHECKED)
            .eq(MesAndonEvent::getEventLevel, event.getEventLevel())) > 0;
    }

    /**
     * 升级一个超时的事件
     *
     * @return 是否由本次升级（没有超时、已被其他实例升级或事件状态已变化时为 false）
     */
    boolean escalate(Long eventId, LocalDateTime now) {
        MesAndonEvent event = eventMapper.selectById(eventId);
        Deadline deadline = AndonEscalationRules.overdue(event, now);
        if (deadline == null) {
            return false;
        }
        MesAndonRule rule = event.getAndonRuleId() == null ? null : ruleMapper.selectById(event.getAndonRuleId());
        if (rule != null && !SystemConstants.NORMAL.equals(rule.getStatus())) {
            // 规则刚被停用：不按它升级，只提醒处理人（截止时间会按规则的变化重新计算）
            rule = null;
        }
        String from = event.getEventLevel();
        String to = AndonEscalationRules.escalatedLevel(from, rule == null ? null : rule.getEscalateLevel());
        int count = event.getEscalateCount() == null ? 0 : event.getEscalateCount();
        SFunction<MesAndonEvent, LocalDateTime> handledColumn = deadline == Deadline.RESPONSE
            ? MesAndonEvent::getResponseOverdueTime : MesAndonEvent::getResolveOverdueTime;
        LambdaUpdateWrapper<MesAndonEvent> update = Wrappers.<MesAndonEvent>lambdaUpdate()
            .set(MesAndonEvent::getEscalateCount, count + 1)
            .set(handledColumn, now)
            .set(!to.equals(from), MesAndonEvent::getEventLevel, to)
            .eq(MesAndonEvent::getEventId, eventId)
            .eq(MesAndonEvent::getEventLevel, from)
            .eq(MesAndonEvent::getEscalateCount, count)
            .isNull(handledColumn)
            .in(MesAndonEvent::getEventStatus, deadline.watchedStatuses());
        if (eventMapper.update(null, update) == 0) {
            return false;
        }
        String reason = reason(event, deadline, from, to);
        statusLogService.record(BizType.ANDON_EVENT, eventId, event.getEventCode(), "escalate", event.getEventStatus(),
            event.getEventStatus(), reason);
        List<Long> receivers = receivers(rule, event.getHandlerUserId());
        if (!receivers.isEmpty()) {
            String message = "安灯事件 " + event.getEventCode() + " " + reason + "，请尽快处理：" + event.getTitle();
            afterCommit(() -> RemoteFallback.run(PUSH_FALLBACK, () -> remoteMessageService.publishMessage(receivers, message)));
        }
        return true;
    }

    /**
     * 履历与提醒中的原因，例如“响应超时：规定上报后 15 分钟内开始处理（截止 2026-11-10 08:15），紧急程度由“一般”升为“紧急””
     */
    static String reason(MesAndonEvent event, Deadline deadline, String from, String to) {
        LocalDateTime due = deadline == Deadline.RESPONSE ? event.getResponseDueTime() : event.getResolveDueTime();
        StringBuilder text = new StringBuilder(deadline.label()).append("：规定上报后 ");
        if (event.getReportTime() != null && due != null) {
            text.append(Duration.between(event.getReportTime(), due).toMinutes()).append(" 分钟内");
        }
        text.append(deadline.requirement());
        if (due != null) {
            text.append("（截止 ").append(due.format(TIME)).append("）");
        }
        if (!to.equals(from)) {
            text.append("，紧急程度由“").append(levelLabel(from)).append("”升为“").append(levelLabel(to)).append("”");
        } else if (AndonEscalationRules.isHighestLevel(from)) {
            text.append("，已是最高级别，只提醒");
        } else {
            text.append("，只提醒");
        }
        return text.toString();
    }

    private static String levelLabel(String level) {
        return CodeEnum.find(AndonLevel.class, level).map(AndonLevel::label).orElse(level);
    }

    /**
     * 提醒对象：规则中的人员与当前处理人，去重
     */
    static List<Long> receivers(MesAndonRule rule, Long handlerUserId) {
        Set<Long> users = new LinkedHashSet<>(rule == null ? List.of() : AndonEscalationRules.parseUserIds(rule.getNotifyUsers()));
        if (handlerUserId != null) {
            users.add(handlerUserId);
        }
        return List.copyOf(users);
    }

    /**
     * 在事务提交后执行（提醒不应早于升级生效）；没有事务时立即执行
     */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

}
