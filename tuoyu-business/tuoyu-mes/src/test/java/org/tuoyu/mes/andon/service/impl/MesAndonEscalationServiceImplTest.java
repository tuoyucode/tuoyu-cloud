package org.tuoyu.mes.andon.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesAndonRule;
import org.tuoyu.mes.andon.escalation.AndonEscalationProperties;
import org.tuoyu.mes.andon.escalation.AndonEscalationRules;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.andon.mapper.MesAndonRuleMapper;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.resource.api.RemoteMessageService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("安灯超时升级：截止时间计算、响应与解决超时升级、并发只升级一次、最高级别只提醒、提醒失败不影响升级、逐个租户处理")
class MesAndonEscalationServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime REPORT = LocalDateTime.of(2026, 11, 10, 8, 0);
    private static final LocalDateTime NOW = REPORT.plusMinutes(20);

    private final MesAndonEventMapper eventMapper = MesTestSupport.mapper(MesAndonEventMapper.class);
    private final MesAndonRuleMapper ruleMapper = MesTestSupport.mapper(MesAndonRuleMapper.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);
    private final RemoteMessageService messageService = mock(RemoteMessageService.class);
    private final AndonEscalationProperties properties = new AndonEscalationProperties();

    private final MesAndonEscalationServiceImpl service = new MesAndonEscalationServiceImpl(eventMapper, ruleMapper,
        statusLogService, messageService, properties, MesTestSupport.noopTransactionManager());

    {
        service.setClock(Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        doReturn(List.of()).when(eventMapper).selectDeadlinePending(anyInt());
        doReturn(List.of()).when(eventMapper).selectOverdue(any(), anyInt());
    }

    @AfterEach
    void restore() {
        MesTestSupport.tenant(false);
    }

    private static MesAndonEvent ref(long eventId, String tenantId) {
        MesAndonEvent ref = new MesAndonEvent();
        ref.setEventId(eventId);
        ref.setTenantId(tenantId);
        return ref;
    }

    /** 已按规则 9 计算过截止时间、响应已超时的事件 */
    private static MesAndonEvent overdueEvent(String status, String level) {
        MesAndonEvent event = new MesAndonEvent();
        event.setEventId(1L);
        event.setEventCode("AD001");
        event.setTitle("主轴异响");
        event.setEventType("equipment");
        event.setEventLevel(level);
        event.setEventStatus(status);
        event.setReportTime(REPORT);
        event.setHandlerUserId(30L);
        event.setAndonRuleId(9L);
        event.setDeadlineChecked(AndonEscalationRules.CHECKED);
        event.setResponseDueTime(REPORT.plusMinutes(15));
        event.setResolveDueTime(REPORT.plusMinutes(60));
        event.setEscalateCount(0);
        return event;
    }

    private static MesAndonRule rule(String escalateLevel) {
        MesAndonRule rule = new MesAndonRule();
        rule.setAndonRuleId(9L);
        rule.setResponseMinutes(15);
        rule.setResolveMinutes(60);
        rule.setEscalateLevel(escalateLevel);
        rule.setNotifyUsers("10,11");
        rule.setStatus("0");
        return rule;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<MesAndonEvent> capturedUpdate() {
        ArgumentCaptor<Wrapper<MesAndonEvent>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(eventMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<MesAndonEvent> update = (LambdaUpdateWrapper<MesAndonEvent>) captor.getValue();
        update.getSqlSegment();
        return update;
    }

    @Test
    @DisplayName("计算截止时间：按匹配的规则从上报时间起算，以待计算与原级别为条件更新")
    void computesDeadlinesFromMatchingRule() {
        MesAndonEvent event = overdueEvent("reported", "normal");
        event.setDeadlineChecked(AndonEscalationRules.UNCHECKED);
        event.setResponseDueTime(null);
        event.setResolveDueTime(null);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(eventMapper.update(isNull(), any())).thenReturn(1);
        MesAndonRule general = rule("urgent");
        general.setAndonRuleId(8L);
        general.setResponseMinutes(30);
        MesAndonRule specific = rule("urgent");
        specific.setEventType("equipment");

        assertThat(service.computeDeadlines(1L, List.of(general, specific))).isTrue();

        LambdaUpdateWrapper<MesAndonEvent> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("response_due_time").contains("resolve_due_time").contains("deadline_checked");
        assertThat(update.getSqlSegment()).contains("deadline_checked").contains("event_level");
        assertThat(update.getParamNameValuePairs().values())
            .contains(9L, REPORT.plusMinutes(15), REPORT.plusMinutes(60), AndonEscalationRules.CHECKED, "normal");
    }

    @Test
    @DisplayName("已计算过或已解决的事件不再计算")
    void skipsCheckedOrResolvedEvents() {
        when(eventMapper.selectById(1L)).thenReturn(overdueEvent("reported", "normal"));
        assertThat(service.computeDeadlines(1L, List.of(rule("urgent")))).isFalse();

        MesAndonEvent resolved = overdueEvent("resolved", "normal");
        resolved.setDeadlineChecked(AndonEscalationRules.UNCHECKED);
        when(eventMapper.selectById(2L)).thenReturn(resolved);
        assertThat(service.computeDeadlines(2L, List.of(rule("urgent")))).isFalse();
        verify(eventMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("响应超时：升到规则中的级别，升级次数加一、记录响应超时的时间，写履历并在提交后提醒规则中的人与处理人")
    void escalatesResponseOverdue() {
        when(eventMapper.selectById(1L)).thenReturn(overdueEvent("assigned", "normal"));
        when(ruleMapper.selectById(9L)).thenReturn(rule("urgent"));
        when(eventMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.escalate(1L, NOW)).isTrue();

        LambdaUpdateWrapper<MesAndonEvent> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("escalate_count").contains("response_overdue_time").contains("event_level");
        // 以原级别、原升级次数、该时限未升级与状态为条件：多个实例同时检查时只有一个成功
        assertThat(update.getSqlSegment()).contains("event_level").contains("escalate_count")
            .contains("response_overdue_time IS NULL").contains("event_status IN");
        assertThat(update.getParamNameValuePairs().values()).contains(1, NOW, "urgent", "normal", 0, "reported", "assigned");
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(statusLogService).record(eq(BizType.ANDON_EVENT), eq(1L), eq("AD001"), eq("escalate"), eq("assigned"),
            eq("assigned"), reason.capture());
        assertThat(reason.getValue()).contains("响应超时").contains("15 分钟内开始处理").contains("由“一般”升为“紧急”");
        verify(messageService).publishMessage(eq(List.of(10L, 11L, 30L)), contains("AD001 响应超时"));
    }

    @Test
    @DisplayName("解决超时：处理中的事件超过解决截止时间时升级，记录解决超时的时间")
    void escalatesResolveOverdue() {
        MesAndonEvent event = overdueEvent("processing", "urgent");
        event.setEscalateCount(1);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(ruleMapper.selectById(9L)).thenReturn(rule("critical"));
        when(eventMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.escalate(1L, REPORT.plusMinutes(61))).isTrue();

        LambdaUpdateWrapper<MesAndonEvent> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("resolve_overdue_time").doesNotContain("response_overdue_time");
        assertThat(update.getParamNameValuePairs().values()).contains(2, "critical", "processing");
        verify(statusLogService).record(eq(BizType.ANDON_EVENT), eq(1L), eq("AD001"), eq("escalate"), eq("processing"),
            eq("processing"), contains("解决超时"));
    }

    @Test
    @DisplayName("已是最高级别：只提醒，不改紧急程度")
    void highestLevelOnlyReminds() {
        when(eventMapper.selectById(1L)).thenReturn(overdueEvent("reported", "critical"));
        when(ruleMapper.selectById(9L)).thenReturn(rule("critical"));
        when(eventMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.escalate(1L, NOW)).isTrue();

        assertThat(capturedUpdate().getSqlSet()).contains("escalate_count").doesNotContain("event_level");
        verify(statusLogService).record(eq(BizType.ANDON_EVENT), eq(1L), eq("AD001"), eq("escalate"), eq("reported"),
            eq("reported"), contains("已是最高级别，只提醒"));
        verify(messageService).publishMessage(anyList(), anyString());
    }

    @Test
    @DisplayName("规则只提醒（没有升级级别）或已停用：级别不变；停用的规则不再提醒规则中的人，只提醒处理人")
    void ruleWithoutLevelOnlyReminds() {
        when(eventMapper.selectById(1L)).thenReturn(overdueEvent("assigned", "normal"));
        MesAndonRule disabled = rule("urgent");
        disabled.setStatus("1");
        when(ruleMapper.selectById(9L)).thenReturn(disabled);
        when(eventMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.escalate(1L, NOW)).isTrue();

        assertThat(capturedUpdate().getSqlSet()).doesNotContain("event_level");
        verify(messageService).publishMessage(eq(List.of(30L)), contains("只提醒"));
    }

    @Test
    @DisplayName("多个实例同时升级：条件更新影响 0 行的实例跳过，不写履历、不重复提醒")
    void concurrentEscalationHappensOnce() {
        when(eventMapper.selectById(1L)).thenReturn(overdueEvent("reported", "normal"));
        when(ruleMapper.selectById(9L)).thenReturn(rule("urgent"));
        when(eventMapper.update(isNull(), any())).thenReturn(1, 0);

        boolean first = service.escalate(1L, NOW);
        boolean second = service.escalate(1L, NOW);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(statusLogService, times(1)).record(any(), anyLong(), anyString(), eq("escalate"), anyString(), anyString(), anyString());
        verify(messageService, times(1)).publishMessage(anyList(), anyString());
    }

    @Test
    @DisplayName("没有超时或时限已升级过：不更新")
    void notOverdueDoesNothing() {
        when(eventMapper.selectById(1L)).thenReturn(overdueEvent("reported", "normal"));
        assertThat(service.escalate(1L, REPORT.plusMinutes(10))).isFalse();

        MesAndonEvent handled = overdueEvent("reported", "normal");
        handled.setResponseOverdueTime(REPORT.plusMinutes(15));
        when(eventMapper.selectById(2L)).thenReturn(handled);
        assertThat(service.escalate(2L, NOW)).isFalse();
        verify(eventMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("提醒失败（消息服务不可用）不影响升级")
    void pushFailureDoesNotUndoEscalation() {
        when(eventMapper.selectById(1L)).thenReturn(overdueEvent("reported", "normal"));
        when(ruleMapper.selectById(9L)).thenReturn(rule("urgent"));
        when(eventMapper.update(isNull(), any())).thenReturn(1);
        doThrow(new IllegalStateException("resource 不可用")).when(messageService).publishMessage(anyList(), anyString());

        assertThat(service.escalate(1L, NOW)).isTrue();
        verify(statusLogService).record(any(), anyLong(), anyString(), eq("escalate"), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("没有提醒对象（规则无人员、事件未派单）时不推送")
    void noReceiversNoPush() {
        MesAndonEvent event = overdueEvent("reported", "normal");
        event.setHandlerUserId(null);
        when(eventMapper.selectById(1L)).thenReturn(event);
        MesAndonRule rule = rule("urgent");
        rule.setNotifyUsers(null);
        when(ruleMapper.selectById(9L)).thenReturn(rule);
        when(eventMapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.escalate(1L, NOW)).isTrue();
        verify(messageService, never()).publishMessage(anyList(), anyString());
    }

    @Test
    @DisplayName("一次检查：跨租户只查主键与租户，再逐个事件在它自己的租户中处理；一个事件失败不影响其他事件")
    void processesEachEventInItsOwnTenant() {
        MesTestSupport.tenant(true);
        doReturn(List.of(ref(1L, "t1"))).when(eventMapper).selectDeadlinePending(anyInt());
        doReturn(List.of(ref(2L, "t2"), ref(3L, "t3"), ref(4L, "t1"))).when(eventMapper).selectOverdue(any(), anyInt());
        List<String> seenTenants = new ArrayList<>();
        MesAndonEvent pending = overdueEvent("reported", "normal");
        pending.setDeadlineChecked(AndonEscalationRules.UNCHECKED);
        when(eventMapper.selectById(anyLong())).thenAnswer(invocation -> {
            seenTenants.add(TenantHelper.currentTenantId());
            long id = invocation.getArgument(0);
            if (id == 1L) {
                return pending;
            }
            if (id == 3L) {
                throw new IllegalStateException("数据库超时");
            }
            MesAndonEvent event = overdueEvent("reported", "normal");
            event.setEventId(id);
            return event;
        });
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule("urgent")));
        when(ruleMapper.selectById(9L)).thenReturn(rule("urgent"));
        when(eventMapper.update(isNull(), any())).thenReturn(1);

        int escalated = service.checkOverdue();

        assertThat(seenTenants).containsExactly("t1", "t2", "t3", "t1");
        assertThat(escalated).isEqualTo(2);
        verify(statusLogService, times(2)).record(any(), anyLong(), anyString(), eq("escalate"), anyString(), anyString(), anyString());
        verify(eventMapper).selectDeadlinePending(properties.getBatchSize());
        verify(eventMapper).selectOverdue(NOW, properties.getBatchSize());
    }

    @Test
    @DisplayName("提醒对象：规则中的人与处理人去重")
    void receiversAreDistinct() {
        MesAndonRule rule = rule("urgent");
        rule.setNotifyUsers("30,10");
        assertThat(MesAndonEscalationServiceImpl.receivers(rule, 30L)).containsExactly(30L, 10L);
        assertThat(MesAndonEscalationServiceImpl.receivers(null, null)).isEmpty();
    }

}
