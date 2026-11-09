package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.bo.MesLineBo;
import org.tuoyu.mes.masterdata.domain.bo.MesStationBo;
import org.tuoyu.mes.masterdata.domain.vo.MesLineVo;
import org.tuoyu.mes.masterdata.domain.vo.MesStationVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("产线与工位：上级由下级带出、换上级限制与删除前校验")
class MesLineStationServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesFactoryMapper factoryMapper = MesTestSupport.mapper(MesFactoryMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);
    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);

    private final MesLineServiceImpl lineService = new MesLineServiceImpl(lineMapper, workshopMapper, factoryMapper,
        stationMapper, deviceMapper, workorderMapper);
    private final MesStationServiceImpl stationService = new MesStationServiceImpl(stationMapper, lineMapper,
        workshopMapper, factoryMapper, deviceMapper);

    static MesLine line(long id, long workshopId, long factoryId) {
        MesLine line = new MesLine();
        line.setLineId(id);
        line.setWorkshopId(workshopId);
        line.setFactoryId(factoryId);
        line.setLineCode("L" + id);
        line.setLineName("产线" + id);
        return line;
    }

    private static MesLineBo lineBo(Long workshopId) {
        MesLineBo bo = new MesLineBo();
        bo.setWorkshopId(workshopId);
        bo.setLineCode("L01");
        bo.setLineName("一线");
        return bo;
    }

    private static MesStationBo stationBo(Long lineId) {
        MesStationBo bo = new MesStationBo();
        bo.setLineId(lineId);
        bo.setStationCode("S01");
        bo.setStationName("一号工位");
        return bo;
    }

    @Test
    @DisplayName("产线的工厂由车间带出，不采信前端提交的工厂")
    void lineFactoryFromWorkshop() {
        when(workshopMapper.selectById(5L)).thenReturn(MesWorkshopServiceImplTest.workshop(5L, 1L));
        MesLineBo bo = lineBo(5L);
        bo.setFactoryId(99L);

        lineService.insertByBo(bo);

        ArgumentCaptor<MesLine> captor = ArgumentCaptor.forClass(MesLine.class);
        verify(lineMapper).insert(captor.capture());
        assertThat(captor.getValue().getFactoryId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
    }

    @Test
    @DisplayName("所属车间不存在时拒绝新增产线")
    void lineRejectsMissingWorkshop() {
        assertThatThrownBy(() -> lineService.insertByBo(lineBo(5L))).hasMessageContaining("车间不存在");
    }

    @Test
    @DisplayName("产线下已有工位时不能更换车间")
    void lineRejectsWorkshopChange() {
        when(lineMapper.selectById(7L)).thenReturn(line(7L, 5L, 1L));
        when(workshopMapper.selectById(6L)).thenReturn(MesWorkshopServiceImplTest.workshop(6L, 1L));
        when(stationMapper.exists(any(Wrapper.class))).thenReturn(true);
        MesLineBo bo = lineBo(6L);
        bo.setLineId(7L);

        assertThatThrownBy(() -> lineService.updateByBo(bo)).hasMessageContaining("不能更换所属车间");
    }

    @Test
    @DisplayName("修改产线时同步工厂")
    void lineUpdateSyncsFactory() {
        when(lineMapper.selectById(7L)).thenReturn(line(7L, 5L, 1L));
        when(workshopMapper.selectById(6L)).thenReturn(MesWorkshopServiceImplTest.workshop(6L, 2L));
        when(lineMapper.updateById(any(MesLine.class))).thenReturn(1);
        MesLineBo bo = lineBo(6L);
        bo.setLineId(7L);

        lineService.updateByBo(bo);

        ArgumentCaptor<MesLine> captor = ArgumentCaptor.forClass(MesLine.class);
        verify(lineMapper).updateById(captor.capture());
        assertThat(captor.getValue().getFactoryId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("产线下还有工位或已有工单时不能删除")
    void lineDeleteChecks() {
        when(stationMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> lineService.deleteByIds(List.of(7L))).hasMessageContaining("工位");

        when(stationMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> lineService.deleteByIds(List.of(7L))).hasMessageContaining("工单");
        verify(lineMapper, never()).deleteByIds(anyCollection());

        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(lineMapper.deleteByIds(anyCollection())).thenReturn(1);
        lineService.deleteByIds(List.of(7L));
    }

    @Test
    @DisplayName("产线列表带工厂与车间名称；下拉选项的上级为车间")
    void lineListAndOptions() {
        when(lineMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(line(7L, 5L, 1L)));
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line(7L, 5L, 1L)));
        when(factoryMapper.selectByIds(anyCollection()))
            .thenReturn(List.of(MesWorkshopServiceImplTest.factory(1L, "一厂")));
        when(workshopMapper.selectByIds(anyCollection()))
            .thenReturn(List.of(MesWorkshopServiceImplTest.workshop(5L, 1L)));

        MesLineVo vo = lineService.queryPageList(new MesLineBo(), new PageParam()).getItems().getFirst();
        assertThat(vo.getFactoryName()).isEqualTo("一厂");
        assertThat(vo.getWorkshopName()).isEqualTo("车间5");
        assertThat(lineService.queryList(new MesLineBo())).hasSize(1);
        assertThat(lineService.options(5L)).singleElement().extracting("parentId").isEqualTo(5L);
    }

    @Test
    @DisplayName("工位的车间与工厂由产线带出")
    void stationHierarchyFromLine() {
        when(lineMapper.selectById(7L)).thenReturn(line(7L, 5L, 1L));
        MesStationBo bo = stationBo(7L);
        bo.setWorkshopId(99L);
        bo.setFactoryId(98L);

        stationService.insertByBo(bo);

        ArgumentCaptor<MesStation> captor = ArgumentCaptor.forClass(MesStation.class);
        verify(stationMapper).insert(captor.capture());
        assertThat(captor.getValue().getWorkshopId()).isEqualTo(5L);
        assertThat(captor.getValue().getFactoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("工位上已有设备时不能更换产线，也不能删除")
    void stationChecks() {
        MesStation current = new MesStation();
        current.setStationId(9L);
        current.setLineId(7L);
        when(stationMapper.selectById(9L)).thenReturn(current);
        when(lineMapper.selectById(8L)).thenReturn(line(8L, 5L, 1L));
        when(deviceMapper.exists(any(Wrapper.class))).thenReturn(true);
        MesStationBo bo = stationBo(8L);
        bo.setStationId(9L);

        assertThatThrownBy(() -> stationService.updateByBo(bo)).hasMessageContaining("不能更换所属产线");
        assertThatThrownBy(() -> stationService.deleteByIds(List.of(9L))).hasMessageContaining("设备");
    }

    @Test
    @DisplayName("修改工位：同一产线时直接保存；工位不存在时提示")
    void stationUpdate() {
        MesStationBo bo = stationBo(7L);
        bo.setStationId(9L);
        assertThatThrownBy(() -> stationService.updateByBo(bo)).hasMessageContaining("不存在");

        MesStation current = new MesStation();
        current.setStationId(9L);
        current.setLineId(7L);
        when(stationMapper.selectById(9L)).thenReturn(current);
        when(lineMapper.selectById(7L)).thenReturn(line(7L, 5L, 1L));
        when(stationMapper.updateById(any(MesStation.class))).thenReturn(1);
        stationService.updateByBo(bo);
        verify(stationMapper).updateById(any(MesStation.class));
    }

    @Test
    @DisplayName("工位详情带三级名称；下拉选项的上级为产线")
    void stationQueries() {
        MesStation station = new MesStation();
        station.setStationId(9L);
        station.setLineId(7L);
        station.setWorkshopId(5L);
        station.setFactoryId(1L);
        station.setStationCode("S9");
        when(stationMapper.selectById(9L)).thenReturn(station);
        when(stationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(station));
        when(lineMapper.selectByIds(anyCollection())).thenReturn(List.of(line(7L, 5L, 1L)));

        MesStationVo vo = stationService.queryById(9L);
        assertThat(vo.getLineName()).isEqualTo("产线7");
        assertThat(stationService.options(7L)).singleElement().extracting("parentId").isEqualTo(7L);
        assertThat(stationService.queryList(new MesStationBo())).hasSize(1);
        assertThat(stationService.queryPageList(new MesStationBo(), new PageParam()).getItems()).isEmpty();
        assertThat(stationService.countList(new MesStationBo())).isZero();
    }

}
