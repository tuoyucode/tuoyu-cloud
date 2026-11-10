package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.equipment.mapper.MesCheckRecordMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenanceOrderMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceImportVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.support.ExcelFiles;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("设备导入：所属位置按编码引用并按最低一级带出上级")
class MesDeviceImportTest {

    static {
        MesTestSupport.init();
    }

    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final MesFactoryMapper factoryMapper = MesTestSupport.mapper(MesFactoryMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);

    private final MesDeviceServiceImpl service = new MesDeviceServiceImpl(deviceMapper, factoryMapper, workshopMapper,
        lineMapper, stationMapper, MesTestSupport.mapper(MesAndonEventMapper.class),
        MesTestSupport.mapper(MesCheckRecordMapper.class), MesTestSupport.mapper(MesMaintenancePlanMapper.class),
        MesTestSupport.mapper(MesMaintenanceOrderMapper.class));

    {
        MesFactory factory = new MesFactory();
        factory.setFactoryId(1L);
        factory.setFactoryCode("F1");
        MesFactory otherFactory = new MesFactory();
        otherFactory.setFactoryId(2L);
        otherFactory.setFactoryCode("F2");
        when(factoryMapper.selectList(any(Wrapper.class))).thenReturn(List.of(factory, otherFactory));
        MesWorkshop workshop = new MesWorkshop();
        workshop.setWorkshopId(10L);
        workshop.setFactoryId(1L);
        workshop.setWorkshopCode("W1");
        when(workshopMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workshop));
        MesLine line = new MesLine();
        line.setLineId(20L);
        line.setWorkshopId(10L);
        line.setFactoryId(1L);
        line.setLineCode("L1");
        MesLine otherLine = new MesLine();
        otherLine.setLineId(21L);
        otherLine.setWorkshopId(10L);
        otherLine.setFactoryId(1L);
        otherLine.setLineCode("L2");
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line, otherLine));
        MesStation station = new MesStation();
        station.setStationId(30L);
        station.setLineId(20L);
        station.setWorkshopId(10L);
        station.setFactoryId(1L);
        station.setStationCode("S1");
        when(stationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(station));
    }

    static MesDeviceImportVo row(String code, String factory, String workshop, String line, String station) {
        MesDeviceImportVo row = new MesDeviceImportVo();
        row.setDeviceCode(code);
        row.setDeviceName("设备" + code);
        row.setFactoryCode(factory);
        row.setWorkshopCode(workshop);
        row.setLineCode(line);
        row.setStationCode(station);
        return row;
    }

    @Test
    @DisplayName("合格时写入：只填工位时带出产线、车间与工厂；只填车间时带出工厂")
    @SuppressWarnings("unchecked")
    void importsValidRows() {
        int count = service.importData(ExcelFiles.xlsx(MesDeviceImportVo.class,
            List.of(row("D1", null, null, null, "S1"), row("D2", "F1", "W1", null, null))));

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<Collection<MesDevice>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(deviceMapper).insert(captor.capture());
        List<MesDevice> devices = new ArrayList<>(captor.getValue());
        assertThat(devices.get(0)).satisfies(d -> {
            assertThat(d.getStationId()).isEqualTo(30L);
            assertThat(d.getLineId()).isEqualTo(20L);
            assertThat(d.getWorkshopId()).isEqualTo(10L);
            assertThat(d.getFactoryId()).isEqualTo(1L);
            assertThat(d.getStatus()).isEqualTo("0");
        });
        assertThat(devices.get(1)).satisfies(d -> {
            assertThat(d.getStationId()).isNull();
            assertThat(d.getLineId()).isNull();
            assertThat(d.getWorkshopId()).isEqualTo(10L);
            assertThat(d.getFactoryId()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("位置编码不存在、层级不一致、没有填写位置、设备编码重复时逐行报出")
    void rejectsBadRows() {
        MesDevice existing = new MesDevice();
        existing.setDeviceCode("D9");
        when(deviceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        List<MesDeviceImportVo> rows = List.of(row("D1", null, null, null, "S9"), row("D2", null, null, "L2", "S1"),
            row("D3", "F2", "W1", null, null), row("D4", null, null, null, null), row("D9", "F1", null, null, null),
            row("d1", "F1", null, null, null));

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesDeviceImportVo.class, rows)))
            .hasMessageContaining("第 2 行：所属工位编码 S9 不存在")
            .hasMessageContaining("第 3 行：所属工位 S1 不属于产线 L2")
            .hasMessageContaining("第 4 行：所属车间 W1 不属于工厂 F2")
            .hasMessageContaining("第 5 行：请填写所属工厂编码")
            .hasMessageContaining("第 6 行：设备编码 D9 已存在")
            .hasMessageContaining("第 7 行：设备编码 d1 与第 2 行重复");
        verify(deviceMapper, never()).insert(anyCollection());
    }

}
