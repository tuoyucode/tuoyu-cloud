package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.equipment.domain.MesOeeConfig;
import org.tuoyu.mes.equipment.domain.bo.MesOeeConfigBo;
import org.tuoyu.mes.equipment.domain.bo.MesOeeQueryBo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeConfigVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeExportVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeVo;
import org.tuoyu.mes.equipment.mapper.MesOeeConfigMapper;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OEE 服务：数据来源、配置继承、日期范围与导出")
class MesOeeServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    private final MesOeeConfigMapper configMapper = MesTestSupport.mapper(MesOeeConfigMapper.class);
    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);
    private final MesProductionReportMapper reportMapper = MesTestSupport.mapper(MesProductionReportMapper.class);
    private final MesOperationTaskMapper taskMapper = MesTestSupport.mapper(MesOperationTaskMapper.class);
    private final MesAndonEventMapper andonMapper = MesTestSupport.mapper(MesAndonEventMapper.class);

    private final MesOeeServiceImpl service = new MesOeeServiceImpl(configMapper, deviceMapper, lineMapper,
        stationMapper, reportMapper, taskMapper, andonMapper);

    private static MesOeeQueryBo query(String type, long id, LocalDate begin, LocalDate end) {
        MesOeeQueryBo bo = new MesOeeQueryBo();
        bo.setTargetType(type);
        bo.setTargetId(id);
        bo.setBeginDate(begin);
        bo.setEndDate(end);
        return bo;
    }

    private static MesDevice device(Long stationId, Long lineId) {
        MesDevice device = new MesDevice();
        device.setDeviceId(30L);
        device.setDeviceCode("D01");
        device.setDeviceName("冲压机");
        device.setStationId(stationId);
        device.setLineId(lineId);
        return device;
    }

    private static MesOeeConfig config(Integer planned, String cycle) {
        MesOeeConfig config = new MesOeeConfig();
        config.setConfigId(1L);
        config.setPlannedMinutes(planned);
        config.setCycleMinutes(cycle == null ? null : new BigDecimal(cycle));
        return config;
    }

    /**
     * 让统计与列表查询返回同一批报工（分页统计取总数，列表取记录）
     */
    private void reports(List<MesProductionReport> rows) {
        when(reportMapper.selectList(any(Page.class), any())).thenAnswer(invocation -> {
            Page<?> page = invocation.getArgument(0);
            page.setTotal(rows.size());
            return rows;
        });
        when(reportMapper.selectList(any(Wrapper.class))).thenReturn(rows);
    }

    private static MesProductionReport report(String qty, String qualified) {
        MesProductionReport report = new MesProductionReport();
        report.setTaskId(20L);
        report.setReportTime(DAY.atTime(10, 0));
        report.setReportQty(new BigDecimal(qty));
        report.setQualifiedQty(new BigDecimal(qualified));
        return report;
    }

    @Test
    @DisplayName("按设备：计划时间取所属产线的配置，产出取设备所在工位的报工，标准节拍取工序任务的单件标准工时")
    void deviceWithStation() {
        when(deviceMapper.selectById(30L)).thenReturn(device(4L, 3L));
        MesStation station = new MesStation();
        station.setStationName("冲压工位");
        when(stationMapper.selectById(4L)).thenReturn(station);
        when(configMapper.selectOne(any())).thenReturn(null, config(480, null));
        reports(List.of(report("100", "95")));
        MesOperationTask task = new MesOperationTask();
        task.setTaskId(20L);
        task.setStandardMinutes(new BigDecimal("2"));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));
        MesAndonEvent event = new MesAndonEvent();
        event.setReportTime(DAY.atTime(8, 0));
        event.setDowntimeMinutes(60);
        when(andonMapper.selectList(any(Wrapper.class))).thenReturn(List.of(event));

        MesOeeVo vo = service.calculate(query("device", 30L, DAY, DAY));

        assertThat(vo.getTargetName()).isEqualTo("D01 冲压机");
        assertThat(vo.getPlannedMinutesPerDay()).isEqualTo(480);
        assertThat(vo.getPlannedSource()).isEqualTo("line");
        assertThat(vo.getOutputSource()).isEqualTo("station");
        assertThat(vo.getOutputSourceName()).isEqualTo("冲压工位");
        assertThat(vo.getSummary().getDowntimeMinutes()).isEqualTo(60L);
        assertThat(vo.getSummary().getIdealMinutes()).isEqualByComparingTo("200");
        assertThat(vo.getSummary().getQuality()).isEqualByComparingTo("95.00");
        assertThat(vo.getSummary().getOeeStatus()).isEqualTo("ok");
    }

    @Test
    @DisplayName("按设备：设备的标准节拍覆盖工序标准工时；设备没有工位与产线时性能与质量为未配置")
    void deviceWithoutOutputSource() {
        when(deviceMapper.selectById(30L)).thenReturn(device(null, null));
        when(configMapper.selectOne(any())).thenReturn(config(480, "1.5"));
        when(andonMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        MesOeeVo vo = service.calculate(query("device", 30L, DAY, DAY.plusDays(1)));

        assertThat(vo.getCycleMinutes()).isEqualByComparingTo("1.5");
        assertThat(vo.getPlannedSource()).isEqualTo("device");
        assertThat(vo.getOutputSource()).isNull();
        assertThat(vo.getDays()).hasSize(2);
        verify(reportMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("按产线：配置取产线的；没有配置时计划时间为空")
    void line() {
        MesLine line = new MesLine();
        line.setLineId(3L);
        line.setLineCode("L1");
        line.setLineName("一线");
        when(lineMapper.selectById(3L)).thenReturn(line);
        when(deviceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(device(null, 3L)));
        reports(List.of(report("10", "10")));
        when(andonMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        MesOeeVo vo = service.calculate(query("line", 3L, DAY, DAY));

        assertThat(vo.getTargetName()).isEqualTo("L1 一线");
        assertThat(vo.getOutputSource()).isEqualTo("line");
        assertThat(vo.getPlannedMinutesPerDay()).isNull();
        assertThat(vo.getSummary().getAvailabilityStatus()).isEqualTo("unconfigured");
        assertThat(vo.getSummary().getQuality()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("日期范围：结束不能早于开始，最长 366 天；对象类型与对象必须有效")
    void validation() {
        assertThatThrownBy(() -> service.calculate(query("device", 30L, DAY, DAY.minusDays(1))))
            .hasMessageContaining("结束日期不能早于开始日期");
        assertThatThrownBy(() -> service.calculate(query("device", 30L, DAY, DAY.plusDays(366))))
            .hasMessageContaining("最长 366 天");
        assertThatThrownBy(() -> service.calculate(query("plant", 30L, DAY, DAY))).hasMessageContaining("统计对象不正确");
        assertThatThrownBy(() -> service.calculate(query("device", 31L, DAY, DAY))).hasMessageContaining("设备不存在");
        assertThatThrownBy(() -> service.calculate(query("line", 31L, DAY, DAY))).hasMessageContaining("产线不存在");
    }

    @Test
    @DisplayName("报工过多时请缩小范围")
    void tooManyReports() {
        when(deviceMapper.selectById(30L)).thenReturn(device(4L, null));
        when(reportMapper.selectList(any(Page.class), any())).thenAnswer(invocation -> {
            invocation.<Page<?>>getArgument(0).setTotal(MesOeeServiceImpl.MAX_REPORTS + 1);
            return List.of();
        });
        assertThatThrownBy(() -> service.calculate(query("device", 30L, DAY, DAY))).hasMessageContaining("缩小日期范围");
    }

    @Test
    @DisplayName("导出：第一行为汇总，指标写为百分数或原因")
    void export() {
        when(deviceMapper.selectById(30L)).thenReturn(device(null, null));
        when(andonMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<MesOeeExportVo> rows = service.exportRows(query("device", 30L, DAY, DAY));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getStatDate()).startsWith("汇总");
        assertThat(rows.get(1).getStatDate()).isEqualTo("2026-11-02");
        assertThat(rows.get(1).getOee()).isEqualTo("无生产");
        assertThat(MesOeeServiceImpl.metricText(new BigDecimal("85.50"), "ok")).isEqualTo("85.5%");
        assertThat(MesOeeServiceImpl.metricText(null, "unconfigured")).isEqualTo("未配置");
        assertThat(MesOeeServiceImpl.metricText(null, null)).isEmpty();
    }

    @Test
    @DisplayName("配置：没有时返回空配置；已有时逐项更新（可以清空）；没有时新增，并发新增冲突时提示刷新")
    void config() {
        when(deviceMapper.selectById(30L)).thenReturn(device(null, null));
        MesOeeConfigVo empty = service.getConfig("device", 30L);
        assertThat(empty.getTargetType()).isEqualTo("device");
        assertThat(empty.getPlannedMinutes()).isNull();

        MesOeeConfigBo bo = new MesOeeConfigBo();
        bo.setTargetType("device");
        bo.setTargetId(30L);
        bo.setPlannedMinutes(480);
        service.saveConfig(bo);
        ArgumentCaptor<MesOeeConfig> inserted = ArgumentCaptor.forClass(MesOeeConfig.class);
        verify(configMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getTargetType()).isEqualTo("device");
        assertThat(inserted.getValue().getPlannedMinutes()).isEqualTo(480);

        when(configMapper.insert(any(MesOeeConfig.class))).thenThrow(new DuplicateKeyException("uk"));
        assertThatThrownBy(() -> service.saveConfig(bo)).hasMessageContaining("刷新后重试");

        when(configMapper.selectOne(any())).thenReturn(config(480, null));
        service.saveConfig(bo);
        verify(configMapper).update(any(), any());
        assertThat(service.getConfig("device", 30L).getPlannedMinutes()).isEqualTo(480);

        assertThatThrownBy(() -> service.getConfig("line", 9L)).hasMessageContaining("产线不存在");
    }

}
