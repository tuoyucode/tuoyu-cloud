package org.tuoyu.mes.common.code;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
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
import static org.mockito.Mockito.when;

@DisplayName("编号生成：规则、默认格式、流水号与周期")
class MesCodeServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final MesCodeRuleMapper ruleMapper = MesTestSupport.mapper(MesCodeRuleMapper.class);
    private final MesCodeSequenceMapper sequenceMapper = MesTestSupport.mapper(MesCodeSequenceMapper.class);

    private final MesCodeServiceImpl service = new MesCodeServiceImpl(ruleMapper, sequenceMapper,
        MesTestSupport.noopTransactionManager(),
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
