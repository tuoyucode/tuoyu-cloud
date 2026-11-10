package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.equipment.domain.MesCheckItem;
import org.tuoyu.mes.equipment.domain.bo.MesCheckItemBo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckItemVo;
import org.tuoyu.mes.equipment.enums.CheckItemResult;
import org.tuoyu.mes.equipment.mapper.MesCheckItemMapper;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("点检项目：适用范围、结果类型与上下限、适用项目查询")
class MesCheckItemServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesCheckItemMapper itemMapper = MesTestSupport.mapper(MesCheckItemMapper.class);
    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final MesCheckItemServiceImpl service = new MesCheckItemServiceImpl(itemMapper, deviceMapper);

    private static MesCheckItemBo bo(String resultType) {
        MesCheckItemBo bo = new MesCheckItemBo();
        bo.setItemName(" 油压 ");
        bo.setDeviceType("press");
        bo.setResultType(resultType);
        return bo;
    }

    private static MesDevice device(String type) {
        MesDevice device = new MesDevice();
        device.setDeviceId(30L);
        device.setDeviceCode("D01");
        device.setDeviceName("冲压机");
        device.setDeviceType(type);
        return device;
    }

    @Test
    @DisplayName("判定型：不保存上下限与单位，默认必检、启用")
    void insertJudge() {
        MesCheckItemBo bo = bo("judge");
        bo.setLowerLimit(BigDecimal.ONE);
        bo.setUnit("MPa");

        service.insertByBo(bo);

        ArgumentCaptor<MesCheckItem> captor = ArgumentCaptor.forClass(MesCheckItem.class);
        verify(itemMapper).insert(captor.capture());
        MesCheckItem item = captor.getValue();
        assertThat(item.getItemName()).isEqualTo("油压");
        assertThat(item.getLowerLimit()).isNull();
        assertThat(item.getUnit()).isNull();
        assertThat(item.getMandatory()).isEqualTo("Y");
        assertThat(item.getStatus()).isEqualTo("0");
        assertThat(item.getSortNo()).isZero();
    }

    @Test
    @DisplayName("数值型：至少一个限值，下限不大于上限；结果类型必须有效")
    void numericLimits() {
        assertThatThrownBy(() -> service.insertByBo(bo("numeric"))).hasMessageContaining("至少填写下限或上限");
        MesCheckItemBo reversed = bo("numeric");
        reversed.setLowerLimit(BigDecimal.TEN);
        reversed.setUpperLimit(BigDecimal.ONE);
        assertThatThrownBy(() -> service.insertByBo(reversed)).hasMessageContaining("下限不能大于上限");
        assertThatThrownBy(() -> service.insertByBo(bo("text"))).hasMessageContaining("结果类型不正确");

        MesCheckItemBo ok = bo("numeric");
        ok.setUpperLimit(BigDecimal.TEN);
        ok.setMandatory("N");
        service.insertByBo(ok);
        ArgumentCaptor<MesCheckItem> captor = ArgumentCaptor.forClass(MesCheckItem.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getUpperLimit()).isEqualByComparingTo("10");
        assertThat(captor.getValue().getMandatory()).isEqualTo("N");
    }

    @Test
    @DisplayName("适用范围：指定设备时以设备为准（清空设备类型）；都不填时拒绝；设备必须存在")
    void scope() {
        MesCheckItemBo none = bo("judge");
        none.setDeviceType(" ");
        assertThatThrownBy(() -> service.insertByBo(none)).hasMessageContaining("请选择适用设备");

        MesCheckItemBo missingDevice = bo("judge");
        missingDevice.setDeviceId(99L);
        assertThatThrownBy(() -> service.insertByBo(missingDevice)).hasMessageContaining("适用设备不存在");

        when(deviceMapper.selectById(30L)).thenReturn(device("press"));
        MesCheckItemBo byDevice = bo("judge");
        byDevice.setDeviceId(30L);
        service.insertByBo(byDevice);
        ArgumentCaptor<MesCheckItem> captor = ArgumentCaptor.forClass(MesCheckItem.class);
        verify(itemMapper).insert(captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo(30L);
        assertThat(captor.getValue().getDeviceType()).isNull();
    }

    @Test
    @DisplayName("修改：逐项写入（可以清空），不存在时拒绝")
    void update() {
        MesCheckItemBo bo = bo("judge");
        bo.setCheckItemId(7L);
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("点检项目不存在");

        when(itemMapper.selectById(7L)).thenReturn(new MesCheckItem());
        when(itemMapper.update(any(), any())).thenReturn(1);
        service.updateByBo(bo);
        verify(itemMapper).update(any(), any());
    }

    @Test
    @DisplayName("删除：全部存在才删除")
    void delete() {
        when(itemMapper.selectByIds(any())).thenReturn(List.of(new MesCheckItem()));
        assertThatThrownBy(() -> service.deleteByIds(List.of(1L, 2L))).hasMessageContaining("不存在");
        verify(itemMapper, never()).deleteByIds(any());
        service.deleteByIds(List.of(1L, 1L));
        verify(itemMapper).deleteByIds(Set.of(1L));
    }

    @Test
    @DisplayName("适用项目：设备必须存在，结果带设备名称；设备类型去重排序")
    void applicableAndTypes() {
        assertThatThrownBy(() -> service.applicable(99L)).hasMessageContaining("设备不存在");

        when(deviceMapper.selectById(30L)).thenReturn(device("press"));
        MesCheckItem item = new MesCheckItem();
        item.setCheckItemId(1L);
        item.setDeviceId(30L);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item));
        when(deviceMapper.selectByIds(any())).thenReturn(List.of(device("press")));
        List<MesCheckItemVo> items = service.applicable(30L);
        assertThat(items).extracting(MesCheckItemVo::getDeviceName).containsExactly("冲压机");

        when(deviceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(device("press"), device(" "), device("cnc"),
            device("press")));
        assertThat(service.deviceTypes()).containsExactly("cnc", "press");

        assertThat(service.queryPageList(new MesCheckItemBo(), new PageParam(1, 10)).getItems()).isEmpty();
    }

    @Test
    @DisplayName("数值判定：等于上下限为合格，超出为不合格，没有设置的限值不检查")
    void judgeBoundaries() {
        assertThat(CheckJudge.judge(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN)).isEqualTo(CheckItemResult.OK);
        assertThat(CheckJudge.judge(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN)).isEqualTo(CheckItemResult.OK);
        assertThat(CheckJudge.judge(new BigDecimal("0.99"), BigDecimal.ONE, null)).isEqualTo(CheckItemResult.NG);
        assertThat(CheckJudge.judge(new BigDecimal("10.01"), null, BigDecimal.TEN)).isEqualTo(CheckItemResult.NG);
        assertThat(CheckJudge.judge(new BigDecimal("-100"), null, BigDecimal.TEN)).isEqualTo(CheckItemResult.OK);
    }

}
