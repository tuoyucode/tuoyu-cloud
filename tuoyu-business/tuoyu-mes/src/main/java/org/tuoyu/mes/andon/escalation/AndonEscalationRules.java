package org.tuoyu.mes.andon.escalation;

import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesAndonRule;
import org.tuoyu.mes.andon.enums.AndonLevel;
import org.tuoyu.mes.andon.enums.AndonStatus;
import org.tuoyu.mes.common.CodeEnum;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 安灯超时升级的判断规则（纯函数，不访问数据库）：
 * <ul>
 *     <li>规则匹配：同时指定异常类型与紧急程度的规则优先，其次是只指定紧急程度的、只指定异常类型的，最后是两者都不指定（适用全部事件）的；</li>
 *     <li>截止时间：从上报时间起算，响应时限要求在此前开始处理，解决时限要求在此前解决；</li>
 *     <li>超时：响应超时只在已上报、已派单时检查，解决超时在已上报、已派单、处理中时检查；每个时限只升级一次；</li>
 *     <li>升级：规则中的目标级别高于当前级别时升到目标级别，否则（已是最高级别、目标不高于当前或规则只提醒）级别不变、只提醒。</li>
 * </ul>
 *
 * @author tuoyu
 */
public final class AndonEscalationRules {

    /**
     * 检查响应时限的状态：还没有开始处理
     */
    public static final List<String> RESPONSE_WATCHED = List.of(AndonStatus.REPORTED.code(), AndonStatus.ASSIGNED.code());

    /**
     * 检查解决时限的状态：还没有解决（也是“未解决”查询条件的状态）
     */
    public static final List<String> RESOLVE_WATCHED = List.of(AndonStatus.REPORTED.code(), AndonStatus.ASSIGNED.code(),
        AndonStatus.PROCESSING.code());

    /**
     * 截止时间已计算
     */
    public static final String CHECKED = "1";

    /**
     * 截止时间待计算
     */
    public static final String UNCHECKED = "0";

    private AndonEscalationRules() {
    }

    /**
     * 超时的时限
     */
    public enum Deadline {

        /**
         * 响应时限：上报后多久内必须开始处理
         */
        RESPONSE("响应超时", "开始处理"),

        /**
         * 解决时限：上报后多久内必须解决
         */
        RESOLVE("解决超时", "解决");

        private final String label;

        private final String requirement;

        Deadline(String label, String requirement) {
            this.label = label;
            this.requirement = requirement;
        }

        public String label() {
            return label;
        }

        /**
         * 时限要求完成的动作（用于提示，例如“开始处理”）
         */
        public String requirement() {
            return requirement;
        }

        /**
         * 检查这个时限的状态
         */
        public List<String> watchedStatuses() {
            return this == RESPONSE ? RESPONSE_WATCHED : RESOLVE_WATCHED;
        }

        /**
         * 规则中这个时限的分钟数
         */
        public Integer minutesOf(MesAndonRule rule) {
            return rule == null ? null : this == RESPONSE ? rule.getResponseMinutes() : rule.getResolveMinutes();
        }

    }

    /**
     * 为事件选择适用的规则：只在启用的规则中选，按 {@link #score} 取最具体的一条。
     * 同一组合的规则在保存时已保证唯一；万一出现重复，取靠前的一条（调用方按主键排序）
     *
     * @param rules 当前租户的规则（可含停用的）
     * @param type  事件的异常类型
     * @param level 事件的紧急程度
     * @return 没有适用的规则时为 null
     */
    public static MesAndonRule match(Collection<MesAndonRule> rules, String type, String level) {
        MesAndonRule best = null;
        int bestScore = 0;
        for (MesAndonRule rule : rules) {
            int score = score(rule, type, level);
            if (score > bestScore) {
                best = rule;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * 匹配程度：0 不适用；4 类型与级别都指定且一致；3 只指定级别且一致；2 只指定类型且一致；1 都不指定（适用全部事件）
     */
    static int score(MesAndonRule rule, String type, String level) {
        if (rule == null || !SystemConstants.NORMAL.equals(rule.getStatus())) {
            return 0;
        }
        boolean hasType = StringUtils.isNotBlank(rule.getEventType());
        boolean hasLevel = StringUtils.isNotBlank(rule.getEventLevel());
        if (hasType && !rule.getEventType().equals(type) || hasLevel && !rule.getEventLevel().equals(level)) {
            return 0;
        }
        if (hasType && hasLevel) {
            return 4;
        }
        if (hasLevel) {
            return 3;
        }
        return hasType ? 2 : 1;
    }

    /**
     * 截止时间：上报时间加上规则中的分钟数；没有规则、规则没有这个时限或没有上报时间时为 null
     */
    public static LocalDateTime dueTime(LocalDateTime reportTime, MesAndonRule rule, Deadline deadline) {
        Integer minutes = deadline.minutesOf(rule);
        return reportTime == null || minutes == null ? null : reportTime.plusMinutes(minutes);
    }

    /**
     * 事件此刻是否有超时未升级的时限：先看响应时限，再看解决时限（两个同时超时时，下一次检查再处理解决时限）
     *
     * @return 超时的时限；没有时为 null
     */
    public static Deadline overdue(MesAndonEvent event, LocalDateTime now) {
        if (event == null || !CHECKED.equals(event.getDeadlineChecked())) {
            return null;
        }
        if (isOverdue(event.getResponseDueTime(), event.getResponseOverdueTime(), now)
            && RESPONSE_WATCHED.contains(event.getEventStatus())) {
            return Deadline.RESPONSE;
        }
        if (isOverdue(event.getResolveDueTime(), event.getResolveOverdueTime(), now)
            && RESOLVE_WATCHED.contains(event.getEventStatus())) {
            return Deadline.RESOLVE;
        }
        return null;
    }

    private static boolean isOverdue(LocalDateTime due, LocalDateTime handled, LocalDateTime now) {
        return due != null && handled == null && !now.isBefore(due);
    }

    /**
     * 升级后的紧急程度：目标级别高于当前级别时取目标级别，否则保持当前级别（只提醒）
     *
     * @param current 当前级别
     * @param target  规则中的目标级别，为空表示只提醒
     */
    public static String escalatedLevel(String current, String target) {
        AndonLevel from = CodeEnum.find(AndonLevel.class, current).orElse(null);
        AndonLevel to = CodeEnum.find(AndonLevel.class, target).orElse(null);
        if (from == null || to == null || to.ordinal() <= from.ordinal()) {
            return current;
        }
        return to.code();
    }

    /**
     * 解析提醒人员（逗号分隔的用户 ID），忽略空白与无法识别的项，去重并保持顺序
     */
    public static List<Long> parseUserIds(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && trimmed.chars().allMatch(Character::isDigit) && trimmed.length() < 19) {
                ids.add(Long.parseLong(trimmed));
            }
        }
        return List.copyOf(ids);
    }

    /**
     * 提醒人员保存为逗号分隔的用户 ID；没有时为 null
     */
    public static String joinUserIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 是否已是最高级别
     */
    public static boolean isHighestLevel(String level) {
        AndonLevel[] levels = AndonLevel.values();
        return levels[levels.length - 1].code().equals(level);
    }

}
