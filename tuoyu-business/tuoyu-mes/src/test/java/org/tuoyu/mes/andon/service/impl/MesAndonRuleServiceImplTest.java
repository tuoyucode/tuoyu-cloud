package org.tuoyu.mes.andon.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesAndonRule;
import org.tuoyu.mes.andon.domain.bo.MesAndonRuleBo;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.andon.mapper.MesAndonRuleMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.domain.vo.RemoteUserVo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("安灯升级规则：时限与级别的校验、提醒人员校验、适用范围唯一、规则变化后重新计算截止时间")
class MesAndonRuleServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesAndonRuleMapper ruleMapper = MesTestSupport.mapper(MesAndonRuleMapper.class);
    private final MesAndonEventMapper eventMapper = MesTestSupport.mapper(MesAndonEventMapper.class);
    private final RemoteUserService userService = mock(RemoteUserService.class);

    private final MesAndonRuleServiceImpl service = new MesAndonRuleServiceImpl(ruleMapper, eventMapper, userService);

    private static MesAndonRuleBo bo() {
        MesAndonRuleBo bo = new MesAndonRuleBo();
        bo.setRuleName("一般异常");
        bo.setEventLevel("normal");
        bo.setResponseMinutes(15);
        bo.setResolveMinutes(60);
        bo.setEscalateLevel("urgent");
        return bo;
    }

    private static RemoteUserVo user(long id) {
        RemoteUserVo user = new RemoteUserVo();
        user.setUserId(id);
        return user;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<MesAndonEvent> capturedRecheck() {
        ArgumentCaptor<Wrapper<MesAndonEvent>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(eventMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<MesAndonEvent> update = (LambdaUpdateWrapper<MesAndonEvent>) captor.getValue();
        update.getSqlSegment();
        return update;
    }

    @Test
    @DisplayName("新增：提醒人员去重后保存为逗号分隔，未解决的事件标为待计算截止时间")
    void insertsAndRechecksOpenEvents() {
        when(userService.selectListByIds(anyCollection())).thenReturn(List.of(user(3L), user(5L)));
        MesAndonRuleBo bo = bo();
        bo.setNotifyUserIds(List.of(3L, 5L, 3L));

        service.insertByBo(bo);

        ArgumentCaptor<MesAndonRule> captor = ArgumentCaptor.forClass(MesAndonRule.class);
        verify(ruleMapper).insert(captor.capture());
        assertThat(captor.getValue().getNotifyUsers()).isEqualTo("3,5");
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
        LambdaUpdateWrapper<MesAndonEvent> recheck = capturedRecheck();
        assertThat(recheck.getSqlSet()).contains("deadline_checked");
        assertThat(recheck.getParamNameValuePairs().values()).contains("0", "1", "reported", "assigned", "processing");
    }

    @Test
    @DisplayName("提醒人员中有其他租户、停用或不存在的用户时拒绝")
    void rejectsUnknownNotifyUsers() {
        when(userService.selectListByIds(anyCollection())).thenReturn(List.of(user(3L)));
        MesAndonRuleBo bo = bo();
        bo.setNotifyUserIds(List.of(3L, 99L));

        assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("不存在或已停用");
        verify(ruleMapper, never()).insert(any(MesAndonRule.class));
    }

    @Test
    @DisplayName("至少要有一个时限；解决时限不能短于响应时限")
    void validatesLimits() {
        MesAndonRuleBo none = bo();
        none.setResponseMinutes(null);
        none.setResolveMinutes(null);
        assertThatThrownBy(() -> service.toEntity(none)).hasMessageContaining("至少填写");

        MesAndonRuleBo reversed = bo();
        reversed.setResolveMinutes(10);
        assertThatThrownBy(() -> service.toEntity(reversed)).hasMessageContaining("不能短于响应时限");
    }

    @Test
    @DisplayName("升级后的级别必须高于适用的级别；已是最高级别时只能不填（只提醒）")
    void validatesEscalateLevel() {
        MesAndonRuleBo same = bo();
        same.setEscalateLevel("normal");
        assertThatThrownBy(() -> service.toEntity(same)).hasMessageContaining("应高于");

        MesAndonRuleBo critical = bo();
        critical.setEventLevel("critical");
        critical.setEscalateLevel("critical");
        assertThatThrownBy(() -> service.toEntity(critical)).hasMessageContaining("应高于");

        critical.setEscalateLevel(null);
        assertThat(service.toEntity(critical).getEscalateLevel()).isNull();
    }

    @Test
    @DisplayName("异常类型与级别必须是有效的值，空串按“全部”处理")
    void validatesCodes() {
        MesAndonRuleBo badType = bo();
        badType.setEventType("weather");
        assertThatThrownBy(() -> service.toEntity(badType)).hasMessageContaining("异常类型不正确");

        MesAndonRuleBo blank = bo();
        blank.setEventType(" ");
        assertThat(service.toEntity(blank).getEventType()).isNull();
    }

    @Test
    @DisplayName("同一组异常类型与紧急程度已有规则时拒绝")
    void rejectsDuplicateScope() {
        when(ruleMapper.exists(any())).thenReturn(true);

        assertThatThrownBy(() -> service.insertByBo(bo())).hasMessageContaining("已有适用于相同");
        verify(ruleMapper, never()).insert(any(MesAndonRule.class));
    }

    @Test
    @DisplayName("修改：可清空的字段（适用范围、时限、升级级别、提醒人员）写入空值，并重新计算截止时间")
    @SuppressWarnings("unchecked")
    void updatesClearableFields() {
        MesAndonRule stored = new MesAndonRule();
        stored.setAndonRuleId(7L);
        when(ruleMapper.selectById(7L)).thenReturn(stored);
        when(ruleMapper.update(any(MesAndonRule.class), any())).thenReturn(1);
        MesAndonRuleBo bo = bo();
        bo.setAndonRuleId(7L);
        bo.setEscalateLevel(null);
        bo.setResolveMinutes(null);
        bo.setNotifyUserIds(List.of());

        service.updateByBo(bo);

        ArgumentCaptor<Wrapper<MesAndonRule>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(ruleMapper).update(any(MesAndonRule.class), captor.capture());
        LambdaUpdateWrapper<MesAndonRule> update = (LambdaUpdateWrapper<MesAndonRule>) captor.getValue();
        update.getSqlSegment();
        assertThat(update.getSqlSet()).contains("escalate_level").contains("resolve_minutes").contains("notify_users");
        capturedRecheck();
    }

    @Test
    @DisplayName("修改时不传提醒人员表示不修改")
    @SuppressWarnings("unchecked")
    void keepsNotifyUsersWhenNotSent() {
        MesAndonRule stored = new MesAndonRule();
        stored.setAndonRuleId(7L);
        when(ruleMapper.selectById(7L)).thenReturn(stored);
        when(ruleMapper.update(any(MesAndonRule.class), any())).thenReturn(1);
        MesAndonRuleBo bo = bo();
        bo.setAndonRuleId(7L);

        service.updateByBo(bo);

        ArgumentCaptor<Wrapper<MesAndonRule>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(ruleMapper).update(any(MesAndonRule.class), captor.capture());
        LambdaUpdateWrapper<MesAndonRule> update = (LambdaUpdateWrapper<MesAndonRule>) captor.getValue();
        update.getSqlSegment();
        assertThat(update.getSqlSet()).doesNotContain("notify_users");
    }

    @Test
    @DisplayName("修改、删除不存在的规则时提示不存在")
    void missingRule() {
        MesAndonRuleBo bo = bo();
        bo.setAndonRuleId(404L);
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.deleteByIds(List.of(404L))).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.queryById(404L)).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("删除后重新计算未解决事件的截止时间；列表按条件查询")
    void deletesAndLists() {
        when(ruleMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteByIds(List.of(7L));
        capturedRecheck();

        MesAndonRuleBo query = new MesAndonRuleBo();
        query.setRuleName("一般");
        assertThat(service.queryPageList(query, new PageParam(1, 10)).getItems()).isEmpty();
    }

}
