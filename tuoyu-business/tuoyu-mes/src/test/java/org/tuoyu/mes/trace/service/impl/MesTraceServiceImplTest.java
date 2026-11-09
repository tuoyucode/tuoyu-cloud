package org.tuoyu.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.mes.common.domain.MesStatusLog;
import org.tuoyu.mes.common.mapper.MesStatusLogMapper;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.material.domain.MesMaterialRequest;
import org.tuoyu.mes.material.domain.MesMaterialRequestItem;
import org.tuoyu.mes.material.domain.MesStockIn;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestMapper;
import org.tuoyu.mes.material.mapper.MesStockInMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.mes.trace.domain.MesSn;
import org.tuoyu.mes.trace.domain.vo.MesTraceEventVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceWorkorderVo;
import org.tuoyu.mes.trace.mapper.MesSnMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("追溯查询：识别顺序、返回上限与记录组装")
class MesTraceServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 8, 8, 0);

    private final MesSnMapper snMapper = MesTestSupport.mapper(MesSnMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesOperationTaskMapper taskMapper = MesTestSupport.mapper(MesOperationTaskMapper.class);
    private final MesProductionReportMapper reportMapper = MesTestSupport.mapper(MesProductionReportMapper.class);
    private final MesMaterialRequestMapper requestMapper = MesTestSupport.mapper(MesMaterialRequestMapper.class);
    private final MesMaterialRequestItemMapper itemMapper = MesTestSupport.mapper(MesMaterialRequestItemMapper.class);
    private final MesInspectionMapper inspectionMapper = MesTestSupport.mapper(MesInspectionMapper.class);
    private final MesDefectRecordMapper defectMapper = MesTestSupport.mapper(MesDefectRecordMapper.class);
    private final MesStockInMapper stockInMapper = MesTestSupport.mapper(MesStockInMapper.class);
    private final MesStatusLogMapper statusLogMapper = MesTestSupport.mapper(MesStatusLogMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);
    private final MesTeamMapper teamMapper = MesTestSupport.mapper(MesTeamMapper.class);
    private final MesWarehouseMapper warehouseMapper = MesTestSupport.mapper(MesWarehouseMapper.class);
    private final MesLocationMapper locationMapper = MesTestSupport.mapper(MesLocationMapper.class);

    private final MesTraceServiceImpl service = new MesTraceServiceImpl(snMapper, workorderMapper, taskMapper,
        reportMapper, requestMapper, itemMapper, inspectionMapper, defectMapper, stockInMapper, statusLogMapper,
        materialMapper, workshopMapper, lineMapper, stationMapper, teamMapper, warehouseMapper, locationMapper);

    private static MesWorkorder workorder(long id) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(id);
        workorder.setWorkorderCode("WO" + id);
        workorder.setMaterialId(10L);
        workorder.setBatchCode("B" + id);
        workorder.setPlanQty(new BigDecimal("100"));
        workorder.setWorkorderStatus("in_progress");
        return workorder;
    }

    private static MesWorkorder idOnly(long id) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(id);
        return workorder;
    }

    private static MesSn sn() {
        MesSn sn = new MesSn();
        sn.setSnId(5L);
        sn.setSnCode("SN001");
        sn.setWorkorderId(1L);
        sn.setMaterialId(10L);
        sn.setSnStatus("completed");
        return sn;
    }

    private static MesSn snCount(long workorderId, long count) {
        MesSn row = new MesSn();
        row.setWorkorderId(workorderId);
        row.setSnId(count);
        return row;
    }

    @Test
    @DisplayName("关键字为空时提示输入")
    void rejectsBlank() {
        assertThatThrownBy(() -> service.trace("  ")).hasMessageContaining("请输入");
    }

    @Test
    @DisplayName("先按序列号精确匹配：带出当前序列号与序列号数量，不再按批次或工单编号查找")
    @SuppressWarnings("unchecked")
    void matchesSnFirst() {
        when(snMapper.selectList(any(Wrapper.class))).thenReturn(List.of(sn()), List.of(snCount(1L, 3L)));
        when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder(1L)));
        MesMaterial product = new MesMaterial();
        product.setMaterialId(10L);
        product.setMaterialName("控制器");
        product.setUnit("台");
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(product));

        MesTraceVo result = service.trace(" SN001 ");

        assertThat(result.getMatchType()).isEqualTo("sn");
        assertThat(result.getKeyword()).isEqualTo("SN001");
        MesTraceWorkorderVo workorder = result.getWorkorders().get(0);
        assertThat(workorder.getWorkorderCode()).isEqualTo("WO1");
        assertThat(workorder.getMaterialName()).isEqualTo("控制器");
        assertThat(workorder.getSnCount()).isEqualTo(3L);
        assertThat(workorder.getCurrentSn().getSnCode()).isEqualTo("SN001");
        assertThat(workorder.getCurrentSn().getWorkorderCode()).isEqualTo("WO1");
        verify(workorderMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("没有序列号时按生产批次号匹配")
    @SuppressWarnings("unchecked")
    void matchesBatch() {
        when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(idOnly(1L)));
        when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder(1L)));

        MesTraceVo result = service.trace("B1");

        assertThat(result.getMatchType()).isEqualTo("batch");
        assertThat(result.getWorkorders()).extracting(MesTraceWorkorderVo::getWorkorderCode).containsExactly("WO1");
        verify(itemMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("再按物料批次号匹配：找到已完成的领退料单所属的工单")
    @SuppressWarnings("unchecked")
    void matchesMaterialBatch() {
        MesMaterialRequestItem item = new MesMaterialRequestItem();
        item.setRequestId(50L);
        MesMaterialRequest request = new MesMaterialRequest();
        request.setRequestId(50L);
        request.setWorkorderId(2L);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item), List.of());
        when(requestMapper.selectList(any(Wrapper.class))).thenReturn(List.of(request), List.of());
        when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder(2L)));

        MesTraceVo result = service.trace("RAW-LOT-01");

        assertThat(result.getMatchType()).isEqualTo("material_batch");
        assertThat(result.getWorkorders()).extracting(MesTraceWorkorderVo::getWorkorderId).containsExactly(2L);
    }

    @Test
    @DisplayName("最后按工单编号匹配")
    @SuppressWarnings("unchecked")
    void matchesWorkorderCode() {
        when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of(idOnly(3L)));
        when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder(3L)));

        MesTraceVo result = service.trace("WO3");

        assertThat(result.getMatchType()).isEqualTo("workorder");
        assertThat(result.getWorkorders()).hasSize(1);
    }

    @Test
    @DisplayName("都没有匹配时返回空结果")
    void noMatch() {
        MesTraceVo result = service.trace("UNKNOWN");

        assertThat(result.getMatchType()).isNull();
        assertThat(result.getWorkorders()).isEmpty();
        assertThat(result.getTimeline()).isEmpty();
    }

    @Test
    @DisplayName("匹配到的工单超过 20 个时只返回最近的 20 个")
    @SuppressWarnings("unchecked")
    void truncates() {
        MesMaterialRequestItem item = new MesMaterialRequestItem();
        item.setRequestId(50L);
        List<MesMaterialRequest> requests = LongStream.rangeClosed(1, 25).mapToObj(id -> {
            MesMaterialRequest request = new MesMaterialRequest();
            request.setWorkorderId(id);
            return request;
        }).toList();
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item), List.of());
        when(requestMapper.selectList(any(Wrapper.class))).thenReturn(requests, List.of());
        when(workorderMapper.selectByIds(anyCollection())).thenAnswer(invocation -> invocation
            .<Collection<Long>>getArgument(0).stream().map(MesTraceServiceImplTest::workorder).toList());

        MesTraceVo result = service.trace("RAW-LOT-01");

        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getWorkorders()).hasSize(20);
        assertThat(result.getWorkorders().get(0).getWorkorderId()).isEqualTo(25L);
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(workorderMapper).selectByIds(ids.capture());
        assertThat(ids.getValue()).hasSize(20).doesNotContain(1L, 5L);
    }

    @Test
    @DisplayName("组装工序与报工、检验、不良、入库，时间线按时间先后排列")
    @SuppressWarnings("unchecked")
    void assemblesRecords() {
        when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(idOnly(1L)));
        when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder(1L)));

        MesOperationTask task = new MesOperationTask();
        task.setTaskId(100L);
        task.setWorkorderId(1L);
        task.setSeqNo(10);
        task.setOperationName("装配");
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task));

        MesProductionReport report = new MesProductionReport();
        report.setReportId(200L);
        report.setReportCode("RP1");
        report.setWorkorderId(1L);
        report.setTaskId(100L);
        report.setStationId(300L);
        report.setReportUserId(7L);
        report.setQualifiedQty(new BigDecimal("9"));
        report.setDefectQty(BigDecimal.ONE);
        report.setReportTime(T0.plusHours(2));
        report.setReportStatus("reported");
        when(reportMapper.selectList(any(Wrapper.class))).thenReturn(List.of(report));
        MesStation station = new MesStation();
        station.setStationId(300L);
        station.setStationName("工位一");
        when(stationMapper.selectByIds(anyCollection())).thenReturn(List.of(station));

        MesInspection inspection = new MesInspection();
        inspection.setInspectionId(400L);
        inspection.setInspectionCode("QC1");
        inspection.setWorkorderId(1L);
        inspection.setTaskId(100L);
        inspection.setInspectionResult("passed");
        inspection.setInspectionTime(T0.plusHours(1));
        when(inspectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(inspection));

        MesDefectRecord defect = new MesDefectRecord();
        defect.setDefectId(500L);
        defect.setDefectCode("NG1");
        defect.setWorkorderId(1L);
        defect.setTaskId(100L);
        defect.setCreateTime(T0.plusHours(3));
        defect.setHandleTime(T0.plusHours(4));
        defect.setHandleMethod("rework");
        when(defectMapper.selectList(any(Wrapper.class))).thenReturn(List.of(defect));

        MesStockIn stockIn = new MesStockIn();
        stockIn.setStockInId(600L);
        stockIn.setStockInCode("RK1");
        stockIn.setWorkorderId(1L);
        stockIn.setStockInStatus("completed");
        stockIn.setCompleteTime(T0.plusHours(5));
        MesStockIn cancelled = new MesStockIn();
        cancelled.setStockInId(601L);
        cancelled.setWorkorderId(1L);
        cancelled.setStockInStatus("cancelled");
        when(stockInMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stockIn, cancelled));

        MesStatusLog release = new MesStatusLog();
        release.setBizType("workorder");
        release.setBizCode("WO1");
        release.setAction("release");
        release.setToStatus("released");
        release.setOperateTime(T0);
        when(statusLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of(release));

        MesTraceVo result = service.trace("B1");

        MesTraceWorkorderVo workorder = result.getWorkorders().get(0);
        assertThat(workorder.getTasks()).singleElement().satisfies(t -> {
            assertThat(t.getOperationName()).isEqualTo("装配");
            assertThat(t.getReports()).singleElement().satisfies(r -> assertThat(r.getStationName()).isEqualTo("工位一"));
        });
        assertThat(workorder.getInspections()).singleElement()
            .satisfies(i -> assertThat(i.getOperationName()).isEqualTo("装配"));
        assertThat(workorder.getDefects()).hasSize(1);
        assertThat(workorder.getStockIns()).hasSize(2);
        assertThat(result.getTimeline()).extracting(MesTraceEventVo::getAction)
            .containsExactly("release", "inspect", "report", "create", "handle", "complete");
        assertThat(result.getTimeline()).allSatisfy(e -> assertThat(e.getWorkorderCode()).isEqualTo("WO1"));
        assertThat(result.getTimeline().get(2).getQty()).isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("关键字过长时拒绝")
    void rejectsLongKeyword() {
        assertThatThrownBy(() -> service.trace("x".repeat(129))).hasMessageContaining("128");
    }

}
