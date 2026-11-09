package org.tuoyu.mes.andon.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;
import org.tuoyu.mes.andon.domain.bo.MesDowntimeReasonBo;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.andon.mapper.MesDowntimeReasonMapper;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("停机原因：分类校验、编码唯一、删除前检查与下拉选项")
class MesDowntimeReasonServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesDowntimeReasonMapper reasonMapper = MesTestSupport.mapper(MesDowntimeReasonMapper.class);
    private final MesAndonEventMapper eventMapper = MesTestSupport.mapper(MesAndonEventMapper.class);

    private final MesDowntimeReasonServiceImpl service = new MesDowntimeReasonServiceImpl(reasonMapper, eventMapper);

    private static MesDowntimeReasonBo bo(String type) {
        MesDowntimeReasonBo bo = new MesDowntimeReasonBo();
        bo.setReasonCode("DT01");
        bo.setReasonName("设备故障");
        bo.setReasonType(type);
        return bo;
    }

    @Test
    @DisplayName("新增时补齐状态与排序")
    void insert() {
        service.insertByBo(bo("equipment"));

        ArgumentCaptor<MesDowntimeReason> captor = ArgumentCaptor.forClass(MesDowntimeReason.class);
        verify(reasonMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
        assertThat(captor.getValue().getSortNo()).isZero();
    }

    @Test
    @DisplayName("原因分类不正确时拒绝")
    void rejectsType() {
        assertThatThrownBy(() -> service.insertByBo(bo("weather"))).hasMessageContaining("原因分类");
    }

    @Test
    @DisplayName("编码已存在时拒绝")
    void rejectsDuplicate() {
        when(reasonMapper.exists(any(Wrapper.class))).thenReturn(true);

        assertThatThrownBy(() -> service.insertByBo(bo("equipment"))).hasMessageContaining("DT01");
    }

    @Test
    @DisplayName("修改不存在的停机原因时提示不存在")
    void updateMissing() {
        MesDowntimeReasonBo bo = bo("equipment");
        bo.setReasonId(1L);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("被安灯事件使用的停机原因不能删除")
    void rejectsDeleteInUse() {
        when(eventMapper.exists(any(Wrapper.class))).thenReturn(true);

        assertThatThrownBy(() -> service.deleteByIds(List.of(1L))).hasMessageContaining("停用");
        verify(reasonMapper, never()).deleteByIds(any());
    }

    @Test
    @DisplayName("下拉选项只含 ID、编码与名称")
    void options() {
        MesDowntimeReason reason = new MesDowntimeReason();
        reason.setReasonId(1L);
        reason.setReasonCode("DT01");
        reason.setReasonName("设备故障");
        when(reasonMapper.selectList(any(Wrapper.class))).thenReturn(List.of(reason));

        assertThat(service.options()).containsExactly(new MesOptionVo(1L, "DT01", "设备故障"));
    }

    @Test
    @DisplayName("查询不存在的停机原因时提示不存在")
    void queryMissing() {
        assertThatThrownBy(() -> service.queryById(1L)).hasMessageContaining("不存在");
    }

}
