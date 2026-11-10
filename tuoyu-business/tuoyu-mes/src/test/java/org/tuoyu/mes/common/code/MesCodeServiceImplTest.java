package org.tuoyu.mes.common.code;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.tuoyu.mes.trace.domain.MesCodeRule;
import org.tuoyu.mes.trace.domain.MesCodeSequence;
import org.tuoyu.mes.trace.mapper.MesCodeRuleMapper;
import org.tuoyu.mes.trace.mapper.MesCodeSequenceMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("编号生成：规则、默认格式、流水号与周期")
class MesCodeServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final MesCodeRuleMapper ruleMapper = MesTestSupport.mapper(MesCodeRuleMapper.class);
    private final MesCodeSequenceMapper sequenceMapper = MesTestSupport.mapper(MesCodeSequenceMapper.class);

    private final PlatformTransactionManager transactionManager = MesTestSupport.noopTransactionManager();

    private final MesCodeServiceImpl service = new MesCodeServiceImpl(ruleMapper, sequenceMapper,
        transactionManager,
        Clock.fixed(LocalDateTime.of(2026, 11, 8, 10, 0).atZone(ZONE).toInstant(), ZONE));

    private static MesCodeSequence sequence(long value) {
        MesCodeSequence sequence = new MesCodeSequence();
        sequence.setCurrentValue(value);
        return sequence;
    }

    @Test
    @DisplayName("没有规则时按默认格式生成：前缀 + 日期 + 补零的流水号")
    void defaultFormat() {
        when(sequenceMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(sequenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sequence(12)));

        assertThat(service.next(CodeRules.WORKORDER)).isEqualTo("WO202611080012");
    }

    @Test
    @DisplayName("按租户配置的规则生成")
    void configuredRule() {
        MesCodeRule rule = new MesCodeRule();
        rule.setPrefix("GD-");
        rule.setDatePattern("yyMM");
        rule.setSerialLength(3);
        rule.setResetCycle("month");
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rule));
        when(sequenceMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(sequenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sequence(7)));

        assertThat(service.next(CodeRules.WORKORDER)).isEqualTo("GD-2611007");
    }

    @Test
    @DisplayName("该周期还没有流水号时插入第一条；并发插入冲突时回到加一")
    void firstOfPeriod() {
        when(sequenceMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        when(sequenceMapper.insert(any(MesCodeSequence.class))).thenReturn(1);

        assertThat(service.next(CodeRules.SN)).isEqualTo("SN261108000001");

        when(sequenceMapper.update(isNull(), any(Wrapper.class))).thenReturn(0, 1);
        when(sequenceMapper.insert(any(MesCodeSequence.class))).thenThrow(new DuplicateKeyException("dup"));
        when(sequenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sequence(2)));
        assertThat(service.next(CodeRules.SN)).isEqualTo("SN261108000002");
    }

    @Test
    @DisplayName("插入第一行与加一各用独立的事务：插入冲突不会使加一所在的事务失效（PostgreSQL），也不会与加一的间隙锁死锁（MySQL）")
    void insertRunsInItsOwnTransaction() {
        when(sequenceMapper.update(isNull(), any(Wrapper.class))).thenReturn(0, 1);
        when(sequenceMapper.insert(any(MesCodeSequence.class))).thenThrow(new DuplicateKeyException("dup"));
        when(sequenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sequence(5)));

        assertThat(service.next(CodeRules.ANDON)).isEqualTo("AD202611080005");
        // 加一（0 行）、插入（冲突）、再加一，各自开启一个新事务
        verify(transactionManager, times(3)).getTransaction(any());
    }

    @Test
    @DisplayName("多次插入冲突、加一仍为 0 行时提示重试，不会无限重试")
    void givesUpAfterAttempts() {
        when(sequenceMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        when(sequenceMapper.insert(any(MesCodeSequence.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.next(CodeRules.ANDON)).hasMessageContaining("请重试");
        verify(sequenceMapper, times(MesCodeServiceImpl.MAX_ATTEMPTS)).insert(any(MesCodeSequence.class));
    }

    @Test
    @DisplayName("接续：新周期已有记录时抬高到用过的最大流水号，没有记录时插入")
    void carryOver() {
        when(sequenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sequence(12), sequence(30), sequence(7)));
        when(sequenceMapper.exists(any(Wrapper.class))).thenReturn(true);

        service.carryOver(CodeRules.WORKORDER, "none");
        verify(sequenceMapper).update(isNull(), any(Wrapper.class));
        verify(sequenceMapper, never()).insert(any(MesCodeSequence.class));

        when(sequenceMapper.exists(any(Wrapper.class))).thenReturn(false);
        service.carryOver(CodeRules.WORKORDER, "none");
        ArgumentCaptor<MesCodeSequence> inserted = ArgumentCaptor.forClass(MesCodeSequence.class);
        verify(sequenceMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getPeriodKey()).isEqualTo("all");
        assertThat(inserted.getValue().getCurrentValue()).isEqualTo(30L);
    }

    @Test
    @DisplayName("接续：该规则还没有用过流水号时不做任何事")
    void carryOverNothing() {
        service.carryOver(CodeRules.WORKORDER, "day");

        verify(sequenceMapper, never()).insert(any(MesCodeSequence.class));
        verify(sequenceMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("批量生成连续的编号")
    void batch() {
        when(sequenceMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(sequenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sequence(13)));

        assertThat(service.next(CodeRules.BATCH, 3)).containsExactly("B261108011", "B261108012", "B261108013");
        assertThatThrownBy(() -> service.next(CodeRules.BATCH, 0)).hasMessageContaining("数量");
    }

    @Test
    @DisplayName("周期标识按重置周期取日期")
    void periodKeys() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 8, 10, 0);
        assertThat(MesCodeServiceImpl.periodKey("none", now)).isEqualTo("all");
        assertThat(MesCodeServiceImpl.periodKey("year", now)).isEqualTo("2026");
        assertThat(MesCodeServiceImpl.periodKey("month", now)).isEqualTo("202611");
        assertThat(MesCodeServiceImpl.periodKey("day", now)).isEqualTo("20261108");
    }

    @Test
    @DisplayName("预览不占用流水号；日期格式错误时提示")
    void previewAndInvalidPattern() {
        assertThat(service.preview(CodeRules.ANDON)).isEqualTo("AD202611080001");
        assertThatThrownBy(() -> MesCodeServiceImpl.format(new CodeRules.Format("X", "ppp", 2, "day"),
            LocalDateTime.now(), 1)).hasMessageContaining("日期格式");
        assertThatThrownBy(() -> service.next(" ")).hasMessageContaining("不能为空");
    }

}
