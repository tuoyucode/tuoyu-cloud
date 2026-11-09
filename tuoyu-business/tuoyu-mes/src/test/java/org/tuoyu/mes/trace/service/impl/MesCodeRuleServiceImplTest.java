package org.tuoyu.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.mes.trace.domain.MesCodeRule;
import org.tuoyu.mes.trace.domain.bo.MesCodeRuleBo;
import org.tuoyu.mes.trace.mapper.MesCodeRuleMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("编码规则：日期格式与重置周期、编码唯一、预览")
class MesCodeRuleServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesCodeRuleMapper ruleMapper = MesTestSupport.mapper(MesCodeRuleMapper.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);

    private final MesCodeRuleServiceImpl service = new MesCodeRuleServiceImpl(ruleMapper, codeService);

    private static MesCodeRuleBo bo(String code, String pattern, String cycle) {
        MesCodeRuleBo bo = new MesCodeRuleBo();
        bo.setRuleCode(code);
        bo.setRuleName("规则");
        bo.setPrefix("WO");
        bo.setDatePattern(pattern);
        bo.setSerialLength(4);
        bo.setResetCycle(cycle);
        return bo;
    }

    private static MesCodeRule rule(long id, String code) {
        MesCodeRule rule = new MesCodeRule();
        rule.setRuleId(id);
        rule.setRuleCode(code);
        return rule;
    }

    @ParameterizedTest(name = "日期格式“{0}”、按 {1} 重置：允许")
    @CsvSource(value = {"yyyyMMdd,day", "yyMMdd,day", "yyyyMM,month", "yyMMdd,month", "yyyy,year", "yyMM,year",
        "NULL,none", "yyyyMMdd,none"}, nullValues = "NULL")
    @DisplayName("日期格式能区分重置周期时允许")
    void acceptsMatchingPattern(String pattern, String cycle) {
        assertThatCode(() -> MesCodeRuleServiceImpl.validate(bo("R", pattern, cycle))).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "日期格式“{0}”、按 {1} 重置：拒绝")
    @CsvSource(value = {"yyyyMM,day", "yyyy,month", "NULL,year", "NULL,day"}, nullValues = "NULL")
    @DisplayName("日期格式不能区分重置周期时拒绝，否则不同周期会生成相同的编号")
    void rejectsPatternCoarserThanCycle(String pattern, String cycle) {
        assertThatThrownBy(() -> MesCodeRuleServiceImpl.validate(bo("R", pattern, cycle)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("日期格式中必须包含");
    }

    @Test
    @DisplayName("只允许约定的日期格式")
    void rejectsUnknownPattern() {
        assertThatThrownBy(() -> MesCodeRuleServiceImpl.validate(bo("R", "yyyy-MM-dd", "none")))
            .hasMessageContaining("日期格式只能是");
    }

    @Test
    @DisplayName("重置周期必须是约定的值")
    void rejectsUnknownCycle() {
        assertThatThrownBy(() -> MesCodeRuleServiceImpl.validate(bo("R", "yyyyMMdd", "week")))
            .hasMessageContaining("重置周期不正确");
    }

    @Test
    @DisplayName("流水号位数超出 1 ~ 12 时拒绝")
    void rejectsSerialLength() {
        MesCodeRuleBo bo = bo("R", "yyyyMMdd", "day");
        bo.setSerialLength(13);
        assertThatThrownBy(() -> MesCodeRuleServiceImpl.validate(bo)).hasMessageContaining("1 ~ 12");
    }

    @Test
    @DisplayName("新增：补齐状态，空日期格式保存为空")
    void insert() {
        MesCodeRuleBo bo = bo("PURCHASE", " ", "none");

        service.insertByBo(bo);

        ArgumentCaptor<MesCodeRule> captor = ArgumentCaptor.forClass(MesCodeRule.class);
        verify(ruleMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
        assertThat(captor.getValue().getDatePattern()).isNull();
        assertThat(captor.getValue().getRuleCode()).isEqualTo("PURCHASE");
    }

    @Test
    @DisplayName("规则编码已存在时拒绝新增")
    void rejectsDuplicate() {
        when(ruleMapper.exists(any(Wrapper.class))).thenReturn(true);

        assertThatThrownBy(() -> service.insertByBo(bo("WORKORDER", "yyyyMMdd", "day")))
            .hasMessageContaining("WORKORDER").hasMessageContaining("已存在");
        verify(ruleMapper, never()).insert(any(MesCodeRule.class));
    }

    @Test
    @DisplayName("修改时不能改规则编码")
    void rejectsCodeChange() {
        when(ruleMapper.selectById(1L)).thenReturn(rule(1L, "WORKORDER"));
        MesCodeRuleBo bo = bo("WO2", "yyyyMMdd", "day");
        bo.setRuleId(1L);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不能修改");
    }

    @Test
    @DisplayName("修改时清空日期格式也会写入（保存为空字符串）")
    void updateClearsPattern() {
        when(ruleMapper.selectById(1L)).thenReturn(rule(1L, "WORKORDER"));
        when(ruleMapper.updateById(any(MesCodeRule.class))).thenReturn(1);
        MesCodeRuleBo bo = bo("WORKORDER", null, "none");
        bo.setRuleId(1L);

        service.updateByBo(bo);

        ArgumentCaptor<MesCodeRule> captor = ArgumentCaptor.forClass(MesCodeRule.class);
        verify(ruleMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDatePattern()).isEmpty();
    }

    @Test
    @DisplayName("修改不存在的规则时提示不存在")
    void updateMissing() {
        MesCodeRuleBo bo = bo("WORKORDER", "yyyyMMdd", "day");
        bo.setRuleId(1L);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("预览按规则编码生成，内置规则带标记")
    void previewAndBuiltin() {
        when(ruleMapper.selectById(1L)).thenReturn(rule(1L, "SN"));
        when(codeService.preview("SN")).thenReturn("SN261108000001");

        assertThat(service.preview(1L)).isEqualTo("SN261108000001");
        assertThat(service.queryById(1L).isBuiltin()).isTrue();
    }

    @Test
    @DisplayName("自定义规则不是内置规则")
    void customNotBuiltin() {
        when(ruleMapper.selectById(2L)).thenReturn(rule(2L, "PURCHASE"));

        assertThat(service.queryById(2L).isBuiltin()).isFalse();
    }

    @Test
    @DisplayName("预览不存在的规则时提示不存在")
    void previewMissing() {
        assertThatThrownBy(() -> service.preview(9L)).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("删除不存在的规则时提示不存在，内置规则可以删除")
    void delete() {
        assertThatThrownBy(() -> service.deleteByIds(List.of(1L))).hasMessageContaining("不存在");

        when(ruleMapper.deleteByIds(any())).thenReturn(1);
        assertThatCode(() -> service.deleteByIds(List.of(1L))).doesNotThrowAnyException();
    }

}
