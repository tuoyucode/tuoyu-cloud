package org.tuoyu.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.UniqueCodes;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.trace.domain.MesCodeRule;
import org.tuoyu.mes.trace.domain.bo.MesCodeRuleBo;
import org.tuoyu.mes.trace.domain.vo.MesCodeRuleVo;
import org.tuoyu.mes.trace.enums.ResetCycle;
import org.tuoyu.mes.trace.mapper.MesCodeRuleMapper;
import org.tuoyu.mes.trace.service.IMesCodeRuleService;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 编码规则
 * <p>
 * 日期格式必须能区分重置周期内的编号：例如按天重置时日期中必须有“日”，否则第二天流水号从 1 开始，会与前一天的编号重复。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesCodeRuleServiceImpl implements IMesCodeRuleService {

    /**
     * 允许的日期格式（空字符串表示不带日期）
     */
    static final List<String> DATE_PATTERNS = List.of("", "yyyyMMdd", "yyMMdd", "yyyyMM", "yyMM", "yyyy");

    /**
     * 系统单据使用的规则编码
     */
    static final Set<String> BUILTIN_RULES = Set.of(CodeRules.WORKORDER, CodeRules.TASK, CodeRules.REPORT,
        CodeRules.MATERIAL_ISSUE, CodeRules.MATERIAL_SUPPLEMENT, CodeRules.MATERIAL_RETURN, CodeRules.STOCK_IN,
        CodeRules.INSPECTION, CodeRules.DEFECT, CodeRules.ANDON, CodeRules.BATCH, CodeRules.SN,
        CodeRules.CHECK_RECORD, CodeRules.MAINTENANCE_ORDER);

    private final MesCodeRuleMapper ruleMapper;

    private final IMesCodeService codeService;

    @Override
    public MesCodeRuleVo queryById(Long ruleId) {
        return toVo(requireRule(ruleId));
    }

    @Override
    public PageResult<MesCodeRuleVo> queryPageList(MesCodeRuleBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesCodeRule> lqw = QueryBuilder.lambda(MesCodeRule.class)
            .likeIfText(MesCodeRule::getRuleCode, bo.getRuleCode())
            .likeIfText(MesCodeRule::getRuleName, bo.getRuleName())
            .eqIfText(MesCodeRule::getStatus, bo.getStatus())
            .orderByAsc(!pageParam.hasSort(), MesCodeRule::getRuleCode)
            .build();
        return PageResults.of(ruleMapper.selectPage(pageParam.toPage(MesCodeRule.class), lqw), MesCodeRuleServiceImpl::toVo);
    }

    @Override
    public String preview(Long ruleId) {
        return codeService.preview(requireRule(ruleId).getRuleCode());
    }

    @Override
    public void insertByBo(MesCodeRuleBo bo) {
        validate(bo);
        checkCodeUnique(bo.getRuleCode(), null);
        MesCodeRule rule = BeanMapper.map(bo, MesCodeRule.class);
        rule.setRuleId(null);
        rule.setDatePattern(StringUtils.blankToDefault(bo.getDatePattern(), null));
        if (rule.getStatus() == null) {
            rule.setStatus(SystemConstants.NORMAL);
        }
        UniqueCodes.write(() -> ruleMapper.insert(rule), duplicateCode(rule.getRuleCode()));
        bo.setRuleId(rule.getRuleId());
    }

    @Override
    public void updateByBo(MesCodeRuleBo bo) {
        validate(bo);
        MesCodeRule existing = requireRule(bo.getRuleId());
        if (!Objects.equals(existing.getRuleCode(), bo.getRuleCode())) {
            throw new ServiceException("规则编码创建后不能修改，需要新的编码时请新增规则");
        }
        MesCodeRule rule = BeanMapper.map(bo, MesCodeRule.class);
        // 日期格式清空时也要写入数据库：按实体更新会跳过 null，这里存空字符串，生成编号时视为不带日期
        rule.setDatePattern(StringUtils.blankToDefault(bo.getDatePattern(), ""));
        if (UniqueCodes.write(() -> ruleMapper.updateById(rule), duplicateCode(bo.getRuleCode())) == 0) {
            throw new ServiceException("修改编码规则失败，规则不存在");
        }
        if (!Objects.equals(existing.getResetCycle(), bo.getResetCycle())) {
            codeService.carryOver(bo.getRuleCode(), bo.getResetCycle());
        }
    }

    /**
     * 删除自定义的规则；内置规则供系统单据生成编号，只能修改或停用，不能删除
     */
    @Override
    public void deleteByIds(Collection<Long> ids) {
        List<String> builtin = ruleMapper.selectByIds(ids).stream()
            .map(MesCodeRule::getRuleCode)
            .filter(BUILTIN_RULES::contains)
            .sorted()
            .toList();
        if (!builtin.isEmpty()) {
            throw new ServiceException("内置编码规则 " + String.join("、", builtin) + " 供系统单据生成编号，不能删除，可以修改或停用");
        }
        if (ruleMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除编码规则失败，规则不存在");
        }
    }

    /**
     * 校验日期格式、重置周期以及两者是否匹配
     */
    static void validate(MesCodeRuleBo bo) {
        String pattern = StringUtils.blankToDefault(bo.getDatePattern(), "");
        if (!DATE_PATTERNS.contains(pattern)) {
            throw new ServiceException("日期格式只能是 yyyyMMdd、yyMMdd、yyyyMM、yyMM、yyyy 之一，或者留空");
        }
        ResetCycle cycle = CodeEnum.of(ResetCycle.class, bo.getResetCycle(), "重置周期");
        boolean ok = switch (cycle) {
            case NONE -> true;
            case YEAR -> !pattern.isEmpty();
            case MONTH -> pattern.contains("MM");
            case DAY -> pattern.contains("dd");
        };
        if (!ok) {
            String required = switch (cycle) {
                case YEAR -> "年";
                case MONTH -> "月";
                default -> "日";
            };
            throw new ServiceException("流水号" + cycle.label() + "重置时，日期格式中必须包含" + required
                + "，否则不同周期会生成相同的编号");
        }
        if (bo.getSerialLength() == null || bo.getSerialLength() < 1 || bo.getSerialLength() > 12) {
            throw new ServiceException("流水号位数应在 1 ~ 12 之间");
        }
    }

    private MesCodeRule requireRule(Long ruleId) {
        MesCodeRule rule = ruleId == null ? null : ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new ServiceException("编码规则不存在");
        }
        return rule;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = ruleMapper.exists(QueryBuilder.lambda(MesCodeRule.class)
            .func(UniqueCodes.sameCode(MesCodeRule::getRuleCode, code))
            .neIfPresent(MesCodeRule::getRuleId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException(duplicateCode(code));
        }
    }

    private static String duplicateCode(String code) {
        return "规则编码“" + code + "”已存在";
    }

    private static MesCodeRuleVo toVo(MesCodeRule rule) {
        MesCodeRuleVo vo = BeanMapper.map(rule, MesCodeRuleVo.class);
        vo.setBuiltin(BUILTIN_RULES.contains(rule.getRuleCode()));
        return vo;
    }

}
