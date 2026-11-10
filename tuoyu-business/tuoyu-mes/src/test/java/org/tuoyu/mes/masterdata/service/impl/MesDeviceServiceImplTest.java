package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.equipment.mapper.MesCheckRecordMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenanceOrderMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.bo.MesDeviceBo;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("设备：工厂、车间、产线、工位同属一条链并补齐上级")
class MesDeviceServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final MesFactoryMapper factoryMapper = MesTestSupport.mapper(MesFactoryMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);
    private final MesAndonEventMapper andonEventMapper = MesTestSupport.mapper(MesAndonEventMapper.class);
    private final MesCheckRecordMapper checkRecordMapper = MesTestSupport.mapper(MesCheckRecordMapper.class);
    private final MesMaintenancePlanMapper maintenancePlanMapper = MesTestSupport.mapper(MesMaintenancePlanMapper.class);

    private final MesDeviceServiceImpl service = new MesDeviceServiceImpl(deviceMapper, factoryMapper, workshopMapper,
        lineMapper, stationMapper, andonEventMapper, checkRecordMapper, maintenancePlanMapper,
        MesTestSupport.mapper(MesMaintenanceOrderMapper.class));

    private static MesDevice device() {
        MesDevice device = new MesDevice();
        device.setDeviceId(40L);
        device.setDeviceCode("D01");
        device.setDeviceName("注塑机");
        return device;
    }

    private static MesStation station() {
        MesStation station = new MesStation();
        station.setStationId(9L);
        station.setLineId(7L);
        station.setWorkshopId(5L);
        station.setFactoryId(1L);
        station.setStationName("一号工位");
        return station;
    }

    private void factoryExists() {
        when(factoryMapper.selectById(1L)).thenReturn(MesWorkshopServiceImplTest.factory(1L, "一厂"));
    }

    @Test
    @DisplayName("只填工位时补齐产线、车间与工厂")
    void fillsChainFromStation() {
        factoryExists();
        when(stationMapper.selectById(9L)).thenReturn(station());
        MesDevice device = device();
        device.setStationId(9L);

        service.resolveLocation(device);

        assertThat(device.getLineId()).isEqualTo(7L);
        assertThat(device.getWorkshopId()).isEqualTo(5L);
        assertThat(device.getFactoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("工位不属于所选产线时拒绝")
    void rejectsMismatchedChain() {
        when(stationMapper.selectById(9L)).thenReturn(station());
        MesDevice device = device();
        device.setStationId(9L);
        device.setLineId(8L);

        assertThatThrownBy(() -> service.resolveLocation(device)).hasMessageContaining("不属于所选产线");
    }

    @Test
    @DisplayName("只填产线或车间时补齐上级；车间不属于所选工厂时拒绝")
    void fillsChainFromLineOrWorkshop() {
        factoryExists();
        when(lineMapper.selectById(7L)).thenReturn(MesLineStationServiceImplTest.line(7L, 5L, 1L));
        MesDevice byLine = device();
        byLine.setLineId(7L);
        service.resolveLocation(byLine);
        assertThat(byLine.getWorkshopId()).isEqualTo(5L);
        assertThat(byLine.getFactoryId()).isEqualTo(1L);

        when(workshopMapper.selectById(5L)).thenReturn(MesWorkshopServiceImplTest.workshop(5L, 1L));
        MesDevice byWorkshop = device();
        byWorkshop.setWorkshopId(5L);
        byWorkshop.setFactoryId(2L);
        assertThatThrownBy(() -> service.resolveLocation(byWorkshop)).hasMessageContaining("不属于所选工厂");
    }

    @Test
    @DisplayName("一级都不填时要求选择工厂；工厂不存在时拒绝")
    void requiresFactory() {
        assertThatThrownBy(() -> service.resolveLocation(device())).hasMessageContaining("请选择");

        MesDevice device = device();
        device.setFactoryId(3L);
        assertThatThrownBy(() -> service.resolveLocation(device)).hasMessageContaining("工厂不存在");
    }

    @Test
    @DisplayName("新增与修改：修改时显式写入下级位置，便于清空")
    void insertAndUpdate() {
        factoryExists();
        MesDeviceBo bo = new MesDeviceBo();
        bo.setDeviceCode("D01");
        bo.setDeviceName("注塑机");
        bo.setFactoryId(1L);
        service.insertByBo(bo);
        verify(deviceMapper).insert(any(MesDevice.class));

        bo.setDeviceId(40L);
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不存在");
        when(deviceMapper.updateById(any(MesDevice.class))).thenReturn(1);
        service.updateByBo(bo);
        verify(deviceMapper).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("已有安灯记录、点检记录或保养计划的设备不能删除")
    void deleteChecks() {
        when(andonEventMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(40L))).hasMessageContaining("安灯");
        verify(deviceMapper, never()).deleteByIds(anyCollection());

        when(andonEventMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(checkRecordMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(40L))).hasMessageContaining("点检记录");

        when(checkRecordMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(maintenancePlanMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(40L))).hasMessageContaining("保养计划");
        verify(deviceMapper, never()).deleteByIds(anyCollection());

        when(maintenancePlanMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(deviceMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteByIds(List.of(40L));
    }

    @Test
    @DisplayName("列表带四级位置名称；下拉选项的上级为车间")
    void queries() {
        MesDevice device = device();
        device.setFactoryId(1L);
        device.setWorkshopId(5L);
        device.setStationId(9L);
        when(deviceMapper.selectById(40L)).thenReturn(device);
        when(deviceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(device));
        when(deviceMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(device));
        when(stationMapper.selectByIds(anyCollection())).thenReturn(List.of(station()));

        assertThat(service.queryById(40L).getStationName()).isEqualTo("一号工位");
        assertThat(service.queryPageList(new MesDeviceBo(), new PageParam()).getItems())
            .singleElement().extracting(MesDeviceVo::getStationName).isEqualTo("一号工位");
        assertThat(service.queryList(new MesDeviceBo())).hasSize(1);
        assertThat(service.countList(new MesDeviceBo())).isZero();
        assertThat(service.options(5L)).singleElement().extracting("parentId").isEqualTo(5L);
    }

}
