package org.tuoyu.mes.andon.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesAndonRule;
import org.tuoyu.mes.andon.domain.bo.MesAndonRuleBo;
import org.tuoyu.mes.andon.domain.vo.MesAndonRuleVo;
import org.tuoyu.mes.andon.enums.AndonLevel;
import org.tuoyu.mes.andon.enums.AndonType;
import org.tuoyu.mes.andon.escalation.AndonEscalationRules;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.andon.mapper.MesAndonRuleMapper;
import org.tuoyu.mes.andon.service.IMesAndonRuleService;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 安灯升级规则
 * <p>
 * 规则只决定截止时间与超时后的动作，由超时检查使用（{@link MesAndonEscalationServiceImpl}）。
 * 新增、修改、删除规则后，把未解决事件的截止时间标为待计算，下一次检查按新规则补算；已经升级过的时限不会再次升级。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesAndonRuleServiceImpl implements IMesAndonRuleService {

    private final MesAndonRuleMapper ruleMapper;
    private final MesAndonEventMapper eventMapper;
    private final RemoteUserService remoteUserService;

    @Override
    public MesAndonRuleVo queryById(Long andonRuleId) {
        return BeanMapper.map(requireRule(andonRuleId), MesAndonRuleVo.class);
    }

    @Override
    public PageResult<MesAndonRuleVo> queryPageList(MesAndonRuleBo bo, PageParam pageParam) {
        boolean defaultOrder = !pageParam.hasSort();
        return PageResults.of(ruleMapper.selectPage(pageParam.toPage(MesAndonRule.class), QueryBuilder.lambda(MesAndonRule.class)
            .likeIfText(MesAndonRule::getRuleName, bo.getRuleName())
            .eqIfText(MesAndonRule::getEventType, bo.getEventType())
            .eqIfText(MesAndonRule::getEventLevel, bo.getEventLevel())
            .eqIfText(MesAndonRule::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesAndonRule::getAndonRuleId)
            .build()), MesAndonRuleVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesAndonRuleBo bo) {
        MesAndonRule rule = toEntity(bo);
        checkUnique(rule, null);
        rule.setAndonRuleId(null);
        if (rule.getStatus() == null) {
            rule.setStatus(SystemConstants.NORMAL);
        }
        ruleMapper.insert(rule);
        bo.setAndonRuleId(rule.getAndonRuleId());
        recheckOpenEvents();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesAndonRuleBo bo) {
        requireRule(bo.getAndonRuleId());
        MesAndonRule rule = toEntity(bo);
        checkUnique(rule, bo.getAndonRuleId());
        // 可以清空的字段（适用范围、时限、升级级别）写在条件中，空值也会写入；提醒人员不传时不修改，空数组表示清空；
        // 其余字段为空时不修改
        LambdaUpdateWrapper<MesAndonRule> update = Wrappers.<MesAndonRule>lambdaUpdate()
            .set(MesAndonRule::getEventType, rule.getEventType())
            .set(MesAndonRule::getEventLevel, rule.getEventLevel())
            .set(MesAndonRule::getResponseMinutes, rule.getResponseMinutes())
            .set(MesAndonRule::getResolveMinutes, rule.getResolveMinutes())
            .set(MesAndonRule::getEscalateLevel, rule.getEscalateLevel())
            .set(bo.getNotifyUserIds() != null, MesAndonRule::getNotifyUsers, rule.getNotifyUsers())
            .eq(MesAndonRule::getAndonRuleId, bo.getAndonRuleId());
        MesAndonRule fields = new MesAndonRule();
        fields.setRuleName(rule.getRuleName());
        fields.setStatus(rule.getStatus());
        fields.setRemark(rule.getRemark());
        if (ruleMapper.update(fields, update) == 0) {
            throw new ServiceException("修改升级规则失败，升级规则不存在");
        }
        recheckOpenEvents();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty() || ruleMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除升级规则失败，升级规则不存在");
        }
        recheckOpenEvents();
    }

    /**
     * 校验并转换：编码有效、至少有一个时限、解决时限不短于响应时限、升级后的级别高于适用的级别、提醒人员有效
     */
    MesAndonRule toEntity(MesAndonRuleBo bo) {
        String type = StringUtils.trimToNull(bo.getEventType());
        String level = StringUtils.trimToNull(bo.getEventLevel());
        String escalate = StringUtils.trimToNull(bo.getEscalateLevel());
        if (type != null) {
            CodeEnum.of(AndonType.class, type, "异常类型");
        }
        AndonLevel from = level == null ? null : CodeEnum.of(AndonLevel.class, level, "紧急程度");
        AndonLevel to = escalate == null ? null : CodeEnum.of(AndonLevel.class, escalate, "升级后的紧急程度");
        if (bo.getResponseMinutes() == null && bo.getResolveMinutes() == null) {
            throw new ServiceException("请至少填写响应时限或解决时限");
        }
        if (bo.getResponseMinutes() != null && bo.getResolveMinutes() != null
            && bo.getResolveMinutes() < bo.getResponseMinutes()) {
            throw new ServiceException("解决时限不能短于响应时限");
        }
        if (from != null && to != null && to.ordinal() <= from.ordinal()) {
            throw new ServiceException("升级后的紧急程度应高于“" + from.label() + "”；已是最高级别时不填，超时只提醒");
        }
        MesAndonRule rule = BeanMapper.map(bo, MesAndonRule.class);
        rule.setRuleName(StringUtils.trim(bo.getRuleName()));
        rule.setEventType(type);
        rule.setEventLevel(level);
        rule.setEscalateLevel(escalate);
        rule.setNotifyUsers(AndonEscalationRules.joinUserIds(checkNotifyUsers(bo.getNotifyUserIds())));
        return rule;
    }

    /**
     * 提醒人员必须是当前租户中状态正常的用户（按当前租户查询正常状态的用户：提醒消息中有事件编号与标题，不能发给其他租户的人）
     */
    private List<Long> checkNotifyUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinct = userIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.size() > MesAndonRuleBo.MAX_NOTIFY_USERS) {
            throw new ServiceException("提醒人员不能超过 " + MesAndonRuleBo.MAX_NOTIFY_USERS + " 人");
        }
        if (distinct.isEmpty()) {
            return List.of();
        }
        Set<Long> found = remoteUserService.selectListByIds(distinct).stream()
            .map(RemoteUserVo::getUserId)
            .collect(Collectors.toSet());
        if (!found.containsAll(distinct)) {
            throw new ServiceException("提醒人员中有不存在或已停用的用户，请重新选择");
        }
        return List.copyOf(distinct);
    }

    /**
     * 同一组异常类型与紧急程度（含“全部”）只能有一条规则，否则无法确定事件适用哪一条
     */
    private void checkUnique(MesAndonRule rule, Long excludeId) {
        LambdaQueryBuilder<MesAndonRule> query = QueryBuilder.lambda(MesAndonRule.class)
            .neIfPresent(MesAndonRule::getAndonRuleId, excludeId);
        if (rule.getEventType() == null) {
            query.isNull(MesAndonRule::getEventType);
        } else {
            query.eq(MesAndonRule::getEventType, rule.getEventType());
        }
        if (rule.getEventLevel() == null) {
            query.isNull(MesAndonRule::getEventLevel);
        } else {
            query.eq(MesAndonRule::getEventLevel, rule.getEventLevel());
        }
        if (ruleMapper.exists(query.build())) {
            throw new ServiceException("已有适用于相同异常类型与紧急程度的升级规则，请修改那条规则");
        }
    }

    /**
     * 未解决的事件按规则重新计算截止时间：标为待计算，由下一次超时检查补算
     */
    private void recheckOpenEvents() {
        eventMapper.update(null, Wrappers.<MesAndonEvent>lambdaUpdate()
            .set(MesAndonEvent::getDeadlineChecked, AndonEscalationRules.UNCHECKED)
            .eq(MesAndonEvent::getDeadlineChecked, AndonEscalationRules.CHECKED)
            .in(MesAndonEvent::getEventStatus, AndonEscalationRules.RESOLVE_WATCHED));
    }

    private MesAndonRule requireRule(Long andonRuleId) {
        MesAndonRule rule = andonRuleId == null ? null : ruleMapper.selectById(andonRuleId);
        if (rule == null) {
            throw new ServiceException("升级规则不存在");
        }
        return rule;
    }

}
