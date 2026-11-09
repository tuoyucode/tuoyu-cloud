package org.tuoyu.mes.common.code;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.trace.domain.MesCodeRule;
import org.tuoyu.mes.trace.domain.MesCodeSequence;
import org.tuoyu.mes.trace.mapper.MesCodeRuleMapper;
import org.tuoyu.mes.trace.mapper.MesCodeSequenceMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 编号生成：流水号保存在数据库（{@code mes_code_sequence}，按租户、规则与周期各一行），
 * 在独立的短事务中“先加一再读取”，行锁保证多个实例并发时编号不重复；外层事务回滚时流水号不回退（编号允许跳号）。
 *
 * @author tuoyu
 */
@Service
public class MesCodeServiceImpl implements IMesCodeService {

    /**
     * 不重置时的周期标识
     */
    static final String ALL_PERIODS = "all";

    private static final int MAX_BATCH = 10000;

    private final MesCodeRuleMapper ruleMapper;

    private final MesCodeSequenceMapper sequenceMapper;

    private final TransactionTemplate requiresNew;

    private final Clock clock;

    @Autowired
    public MesCodeServiceImpl(MesCodeRuleMapper ruleMapper, MesCodeSequenceMapper sequenceMapper,
                              PlatformTransactionManager transactionManager) {
        this(ruleMapper, sequenceMapper, transactionManager, Clock.systemDefaultZone());
    }

    MesCodeServiceImpl(MesCodeRuleMapper ruleMapper, MesCodeSequenceMapper sequenceMapper,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.ruleMapper = ruleMapper;
        this.sequenceMapper = sequenceMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Override
    public String next(String ruleCode) {
        return next(ruleCode, 1).get(0);
    }

    @Override
    public List<String> next(String ruleCode, int count) {
        if (count < 1 || count > MAX_BATCH) {
            throw new ServiceException("一次生成的编号数量应在 1 ~ " + MAX_BATCH + " 之间");
        }
        CodeRules.Format format = resolve(ruleCode);
        LocalDateTime now = LocalDateTime.now(clock);
        String period = periodKey(format.resetCycle(), now);
        Long last = requiresNew.execute(status -> increment(ruleCode, period, count));
        long first = last - count + 1;
        List<String> codes = new ArrayList<>(count);
        for (long value = first; value <= last; value++) {
            codes.add(format(format, now, value));
        }
        return codes;
    }

    @Override
    public String preview(String ruleCode) {
        return format(resolve(ruleCode), LocalDateTime.now(clock), 1);
    }

    /**
     * 当前租户启用的规则；没有时用默认格式
     */
    CodeRules.Format resolve(String ruleCode) {
        if (StringUtils.isBlank(ruleCode)) {
            throw new ServiceException("编码规则不能为空");
        }
        MesCodeRule rule = ruleMapper.selectList(QueryBuilder.lambda(MesCodeRule.class)
                .eq(MesCodeRule::getRuleCode, ruleCode)
                .eq(MesCodeRule::getStatus, SystemConstants.NORMAL)
                .build())
            .stream().findFirst().orElse(null);
        if (rule == null) {
            return CodeRules.defaultFormat(ruleCode);
        }
        return new CodeRules.Format(StringUtils.blankToDefault(rule.getPrefix(), ""), rule.getDatePattern(),
            rule.getSerialLength() == null ? 4 : rule.getSerialLength(), StringUtils.blankToDefault(rule.getResetCycle(), "day"));
    }

    /**
     * 流水号加 count，返回加完后的值；该周期还没有记录时插入一行
     */
    private long increment(String ruleCode, String period, int count) {
        for (int attempt = 0; attempt < 2; attempt++) {
            int rows = sequenceMapper.update(null, Wrappers.<MesCodeSequence>lambdaUpdate()
                .setSql("current_value = current_value + " + count)
                .eq(MesCodeSequence::getRuleCode, ruleCode)
                .eq(MesCodeSequence::getPeriodKey, period));
            if (rows > 0) {
                // 与加一在同一个事务（同一个连接）中读取，读到的是本次加完后的值
                return sequenceMapper.selectList(QueryBuilder.lambda(MesCodeSequence.class)
                        .select(MesCodeSequence::getCurrentValue)
                        .eq(MesCodeSequence::getRuleCode, ruleCode)
                        .eq(MesCodeSequence::getPeriodKey, period)
                        .build())
                    .stream().findFirst().map(MesCodeSequence::getCurrentValue)
                    .orElseThrow(() -> new ServiceException("生成编号失败，请重试"));
            }
            try {
                MesCodeSequence sequence = new MesCodeSequence();
                sequence.setRuleCode(ruleCode);
                sequence.setPeriodKey(period);
                sequence.setCurrentValue((long) count);
                sequenceMapper.insert(sequence);
                return count;
            } catch (DuplicateKeyException e) {
                // 另一个实例刚插入了该周期的记录，回到加一
            }
        }
        throw new ServiceException("生成编号失败，请重试");
    }

    static String periodKey(String resetCycle, LocalDateTime now) {
        return switch (StringUtils.blankToDefault(resetCycle, "day")) {
            case "none" -> ALL_PERIODS;
            case "year" -> now.format(DateTimeFormatter.ofPattern("yyyy"));
            case "month" -> now.format(DateTimeFormatter.ofPattern("yyyyMM"));
            default -> now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        };
    }

    static String format(CodeRules.Format format, LocalDateTime now, long value) {
        StringBuilder code = new StringBuilder(StringUtils.blankToDefault(format.prefix(), ""));
        if (StringUtils.isNotBlank(format.datePattern())) {
            try {
                code.append(now.format(DateTimeFormatter.ofPattern(format.datePattern())));
            } catch (IllegalArgumentException e) {
                throw new ServiceException("编码规则的日期格式不正确：" + format.datePattern());
            }
        }
        String serial = Long.toString(value);
        int length = Math.max(1, format.serialLength());
        code.append("0".repeat(Math.max(0, length - serial.length()))).append(serial);
        return code.toString();
    }

}
