package org.tuoyu.mes.andon.escalation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesAndonRule;
import org.tuoyu.mes.andon.escalation.AndonEscalationRules.Deadline;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("安灯超时升级的判断：规则匹配、截止时间、响应与解决超时、升级后的级别")
class AndonEscalationRulesTest {

    private static final LocalDateTime REPORT = LocalDateTime.of(2026, 11, 10, 8, 0);

    private static MesAndonRule rule(long id, String type, String level) {
        MesAndonRule rule = new MesAndonRule();
        rule.setAndonRuleId(id);
        rule.setEventType(type);
        rule.setEventLevel(level);
        rule.setResponseMinutes(15);
        rule.setResolveMinutes(60);
        rule.setStatus("0");
        return rule;
    }

    private static MesAndonEvent event(String status) {
        MesAndonEvent event = new MesAndonEvent();
        event.setEventId(1L);
        event.setEventStatus(status);
        event.setEventLevel("normal");
        event.setReportTime(REPORT);
        event.setDeadlineChecked(AndonEscalationRules.CHECKED);
        event.setResponseDueTime(REPORT.plusMinutes(15));
        event.setResolveDueTime(REPORT.plusMinutes(60));
        return event;
    }

    @Test
    @DisplayName("同时指定类型与级别的规则优先，其次只指定级别、只指定类型，最后是适用全部的规则")
    void matchesMostSpecificRule() {
        MesAndonRule all = rule(1, null, null);
        MesAndonRule byType = rule(2, "equipment", null);
        MesAndonRule byLevel = rule(3, null, "normal");
        MesAndonRule both = rule(4, "equipment", "normal");
        List<MesAndonRule> rules = List.of(all, byType, byLevel, both);

        assertThat(AndonEscalationRules.match(rules, "equipment", "normal")).isSameAs(both);
        assertThat(AndonEscalationRules.match(rules, "quality", "normal")).isSameAs(byLevel);
        assertThat(AndonEscalationRules.match(rules, "equipment", "urgent")).isSameAs(byType);
        assertThat(AndonEscalationRules.match(rules, "quality", "urgent")).isSameAs(all);
        assertThat(AndonEscalationRules.match(List.of(byType, byLevel), "quality", "urgent")).isNull();
    }

    @Test
    @DisplayName("停用的规则不参与匹配")
    void ignoresDisabledRules() {
        MesAndonRule disabled = rule(1, "equipment", "normal");
        disabled.setStatus("1");
        MesAndonRule fallback = rule(2, null, null);

        assertThat(AndonEscalationRules.match(List.of(disabled, fallback), "equipment", "normal")).isSameAs(fallback);
        assertThat(AndonEscalationRules.match(List.of(disabled), "equipment", "normal")).isNull();
    }

    @Test
    @DisplayName("截止时间从上报时间起算；规则没有该时限或没有规则时不检查")
    void computesDueTime() {
        MesAndonRule rule = rule(1, null, null);
        rule.setResolveMinutes(null);

        assertThat(AndonEscalationRules.dueTime(REPORT, rule, Deadline.RESPONSE)).isEqualTo(REPORT.plusMinutes(15));
        assertThat(AndonEscalationRules.dueTime(REPORT, rule, Deadline.RESOLVE)).isNull();
        assertThat(AndonEscalationRules.dueTime(REPORT, null, Deadline.RESPONSE)).isNull();
        assertThat(AndonEscalationRules.dueTime(null, rule, Deadline.RESPONSE)).isNull();
    }

    @Test
    @DisplayName("响应超时：已上报、已派单时到达响应截止时间；开始处理后不检查响应时限")
    void detectsResponseOverdue() {
        assertThat(AndonEscalationRules.overdue(event("reported"), REPORT.plusMinutes(14))).isNull();
        assertThat(AndonEscalationRules.overdue(event("reported"), REPORT.plusMinutes(15))).isEqualTo(Deadline.RESPONSE);
        assertThat(AndonEscalationRules.overdue(event("assigned"), REPORT.plusMinutes(20))).isEqualTo(Deadline.RESPONSE);
        assertThat(AndonEscalationRules.overdue(event("processing"), REPORT.plusMinutes(20))).isNull();
    }

    @Test
    @DisplayName("解决超时：未解决时到达解决截止时间；响应时限已升级过时检查解决时限；已解决、已关闭不检查")
    void detectsResolveOverdue() {
        MesAndonEvent processing = event("processing");
        assertThat(AndonEscalationRules.overdue(processing, REPORT.plusMinutes(60))).isEqualTo(Deadline.RESOLVE);

        MesAndonEvent responded = event("assigned");
        responded.setResponseOverdueTime(REPORT.plusMinutes(15));
        assertThat(AndonEscalationRules.overdue(responded, REPORT.plusMinutes(30))).isNull();
        assertThat(AndonEscalationRules.overdue(responded, REPORT.plusMinutes(61))).isEqualTo(Deadline.RESOLVE);

        assertThat(AndonEscalationRules.overdue(event("resolved"), REPORT.plusMinutes(90))).isNull();
        assertThat(AndonEscalationRules.overdue(event("closed"), REPORT.plusMinutes(90))).isNull();
    }

    @Test
    @DisplayName("同一时限只升级一次；截止时间待计算时不判断超时")
    void eachDeadlineOnlyOnce() {
        MesAndonEvent both = event("processing");
        both.setResolveOverdueTime(REPORT.plusMinutes(60));
        assertThat(AndonEscalationRules.overdue(both, REPORT.plusMinutes(120))).isNull();

        MesAndonEvent unchecked = event("reported");
        unchecked.setDeadlineChecked(AndonEscalationRules.UNCHECKED);
        assertThat(AndonEscalationRules.overdue(unchecked, REPORT.plusMinutes(120))).isNull();
        assertThat(AndonEscalationRules.overdue(null, REPORT)).isNull();
    }

    @Test
    @DisplayName("升级到规则中的目标级别；目标不高于当前级别、为空或已是最高级别时保持不变")
    void escalatedLevel() {
        assertThat(AndonEscalationRules.escalatedLevel("normal", "urgent")).isEqualTo("urgent");
        assertThat(AndonEscalationRules.escalatedLevel("normal", "critical")).isEqualTo("critical");
        assertThat(AndonEscalationRules.escalatedLevel("urgent", "urgent")).isEqualTo("urgent");
        assertThat(AndonEscalationRules.escalatedLevel("critical", "urgent")).isEqualTo("critical");
        assertThat(AndonEscalationRules.escalatedLevel("urgent", null)).isEqualTo("urgent");
        assertThat(AndonEscalationRules.isHighestLevel("critical")).isTrue();
        assertThat(AndonEscalationRules.isHighestLevel("urgent")).isFalse();
    }

    @Test
    @DisplayName("提醒人员按逗号分隔保存，解析时去重并忽略无法识别的项")
    void parsesAndJoinsUserIds() {
        assertThat(AndonEscalationRules.joinUserIds(List.of(3L, 5L, 3L))).isEqualTo("3,5");
        assertThat(AndonEscalationRules.joinUserIds(List.of())).isNull();
        assertThat(AndonEscalationRules.parseUserIds(" 3, 5,x,,3 ")).containsExactly(3L, 5L);
        assertThat(AndonEscalationRules.parseUserIds(null)).isEmpty();
    }

    @Test
    @DisplayName("响应时限检查已上报、已派单；解决时限另外检查处理中")
    void watchedStatuses() {
        assertThat(Deadline.RESPONSE.watchedStatuses()).containsExactly("reported", "assigned");
        assertThat(Deadline.RESOLVE.watchedStatuses()).containsExactly("reported", "assigned", "processing");
        assertThat(Deadline.RESPONSE.minutesOf(rule(1, null, null))).isEqualTo(15);
        assertThat(Deadline.RESOLVE.minutesOf(null)).isNull();
    }

}
