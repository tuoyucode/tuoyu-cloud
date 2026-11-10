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
import java.util.Objects;

/**
 * 编号生成：流水号保存在数据库（{@code mes_code_sequence}，按租户、规则与周期各一行，租户、规则、周期上有唯一键），
 * 在独立的短事务中“先加一再读取”，行锁保证多个实例并发时编号不重复；外层事务回滚时流水号不回退（编号允许跳号）。
 * <p>
 * 周期的第一行在另一个独立事务中插入：PostgreSQL 中语句失败会使整个事务失效，插入冲突不能与随后的加一放在同一事务；
 * MySQL 中对不存在的行加一会加间隙锁，两个请求再各自插入会互相等待而死锁。插入冲突说明其他请求已经插入，回到加一。
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

    /**
     * 加一与插入第一行的最多尝试次数
     */
    static final int MAX_ATTEMPTS = 3;

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
        long last = increment(ruleCode, period, count);
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

    @Override
    public void carryOver(String ruleCode, String resetCycle) {
        String period = periodKey(resetCycle, LocalDateTime.now(clock));
        long max = requiresNew.execute(status -> sequenceMapper.selectList(QueryBuilder.lambda(MesCodeSequence.class)
                .select(MesCodeSequence::getCurrentValue)
                .eq(MesCodeSequence::getRuleCode, ruleCode)
                .build())
            .stream().map(MesCodeSequence::getCurrentValue).filter(Objects::nonNull)
            .mapToLong(Long::longValue).max().orElse(0));
        if (max <= 0) {
            return;
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            boolean exists = Boolean.TRUE.equals(requiresNew.execute(status -> raiseTo(ruleCode, period, max)));
            if (exists) {
                return;
            }
            try {
                requiresNew.executeWithoutResult(status -> insertFirst(ruleCode, period, max));
                return;
            } catch (DuplicateKeyException e) {
                // 另一个请求刚插入了该周期的记录，回到抬高
            }
        }
        throw new ServiceException("接续流水号失败，请重试");
    }

    /**
     * 把该周期的流水号抬高到不小于 value；返回该周期的记录是否存在
     */
    private boolean raiseTo(String ruleCode, String period, long value) {
        boolean exists = sequenceMapper.exists(QueryBuilder.lambda(MesCodeSequence.class)
            .eq(MesCodeSequence::getRuleCode, ruleCode)
            .eq(MesCodeSequence::getPeriodKey, period)
            .build());
        if (exists) {
            sequenceMapper.update(null, Wrappers.<MesCodeSequence>lambdaUpdate()
                .set(MesCodeSequence::getCurrentValue, value)
                .eq(MesCodeSequence::getRuleCode, ruleCode)
                .eq(MesCodeSequence::getPeriodKey, period)
                .lt(MesCodeSequence::getCurrentValue, value));
        }
        return exists;
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
     * 流水号加 count，返回加完后的值。该周期还没有记录时在单独的事务中插入第一行（直接占用 1 ~ count）
     */
    private long increment(String ruleCode, String period, int count) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long value = requiresNew.execute(status -> addAndGet(ruleCode, period, count));
            if (value != null) {
                return value;
            }
            try {
                requiresNew.executeWithoutResult(status -> insertFirst(ruleCode, period, count));
                return count;
            } catch (DuplicateKeyException e) {
                // 另一个请求刚插入了该周期的记录，回到加一
            }
        }
        throw new ServiceException("生成编号失败，请重试");
    }

    /**
     * 加 count 后读取；该周期没有记录时返回 null
     */
    private Long addAndGet(String ruleCode, String period, int count) {
        int rows = sequenceMapper.update(null, Wrappers.<MesCodeSequence>lambdaUpdate()
            .setSql("current_value = current_value + " + count)
            .eq(MesCodeSequence::getRuleCode, ruleCode)
            .eq(MesCodeSequence::getPeriodKey, period));
        if (rows == 0) {
            return null;
        }
        // 与加一在同一个事务（同一个连接）中读取，读到的是本次加完后的值
        return sequenceMapper.selectList(QueryBuilder.lambda(MesCodeSequence.class)
                .select(MesCodeSequence::getCurrentValue)
                .eq(MesCodeSequence::getRuleCode, ruleCode)
                .eq(MesCodeSequence::getPeriodKey, period)
                .build())
            .stream().findFirst().map(MesCodeSequence::getCurrentValue)
            .orElseThrow(() -> new ServiceException("生成编号失败，请重试"));
    }

    private void insertFirst(String ruleCode, String period, long count) {
        MesCodeSequence sequence = new MesCodeSequence();
        sequence.setRuleCode(ruleCode);
        sequence.setPeriodKey(period);
        sequence.setCurrentValue(count);
        sequenceMapper.insert(sequence);
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
