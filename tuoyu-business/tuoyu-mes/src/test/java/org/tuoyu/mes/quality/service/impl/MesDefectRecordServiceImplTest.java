package org.tuoyu.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.domain.bo.MesDefectHandleBo;
import org.tuoyu.mes.quality.domain.bo.MesDefectRecordBo;
import org.tuoyu.mes.quality.domain.vo.MesDefectRecordVo;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("不良记录：手工登记、检验联动与处置")
class MesDefectRecordServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesDefectRecordMapper defectMapper = MesTestSupport.mapper(MesDefectRecordMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesOperationTaskMapper taskMapper = MesTestSupport.mapper(MesOperationTaskMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesInspectionMapper inspectionMapper = MesTestSupport.mapper(MesInspectionMapper.class);
    private final MesProductionReportMapper reportMapper = MesTestSupport.mapper(MesProductionReportMapper.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);
    private final RemoteUserService userService = mock(RemoteUserService.class);

    private final MesDefectRecordServiceImpl service = new MesDefectRecordServiceImpl(defectMapper, workorderMapper,
        taskMapper, materialMapper, inspectionMapper, reportMapper, codeService, statusLogService, userService);

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        previous = MesTestSupport.loginAs(9L);
        when(codeService.next(CodeRules.DEFECT)).thenReturn("NG001");
        when(defectMapper.insert(any(MesDefectRecord.class))).thenAnswer(invocation -> {
            invocation.<MesDefectRecord>getArgument(0).setDefectId(500L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.restore(previous);
    }

    private static MesWorkorder workorder(String status) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(10L);
        workorder.setWorkorderCode("WO001");
        workorder.setMaterialId(50L);
        workorder.setWorkorderStatus(status);
        return workorder;
    }

    private static MesDefectRecordBo bo(String level) {
        MesDefectRecordBo bo = new MesDefectRecordBo();
        bo.setWorkorderId(10L);
        bo.setDefectQty(new BigDecimal("2"));
        bo.setDefectLevel(level);
        bo.setDescription("划伤");
        return bo;
    }

    private static MesDefectRecord record(String source, Long sourceId, String status) {
        MesDefectRecord record = new MesDefectRecord();
        record.setDefectId(500L);
        record.setDefectCode("NG001");
        record.setSourceType(source);
        record.setSourceId(sourceId);
        record.setWorkorderId(10L);
        record.setHandleStatus(status);
        return record;
    }

    @Test
    @DisplayName("手工登记：来源为手工，产品取自工单，待处置")
    void insertManual() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("in_progress"));

        service.insertByBo(bo("major"));

        ArgumentCaptor<MesDefectRecord> captor = ArgumentCaptor.forClass(MesDefectRecord.class);
        verify(defectMapper).insert(captor.capture());
        MesDefectRecord saved = captor.getValue();
        assertThat(saved.getDefectCode()).isEqualTo("NG001");
        assertThat(saved.getSourceType()).isEqualTo("manual");
        assertThat(saved.getMaterialId()).isEqualTo(50L);
        assertThat(saved.getDefectLevel()).isEqualTo("major");
        assertThat(saved.getHandleStatus()).isEqualTo("pending");
        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "create", null, "pending", null);
    }

    @Test
    @DisplayName("等级无效、工单状态不允许或工序任务不属于工单时拒绝登记")
    void insertRejects() {
        assertThatThrownBy(() -> service.insertByBo(bo("unknown"))).hasMessageContaining("不良等级不正确");

        when(workorderMapper.selectById(10L)).thenReturn(workorder("draft"));
        assertThatThrownBy(() -> service.insertByBo(bo("minor"))).hasMessageContaining("不能登记不良");

        when(workorderMapper.selectById(10L)).thenReturn(workorder("released"));
        MesOperationTask task = new MesOperationTask();
        task.setTaskId(20L);
        task.setWorkorderId(11L);
        when(taskMapper.selectById(20L)).thenReturn(task);
        MesDefectRecordBo bo = bo("minor");
        bo.setTaskId(20L);
        assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("不属于该工单");
        verify(defectMapper, never()).insert(any(MesDefectRecord.class));
    }

    @Test
    @DisplayName("检验不合格登记的不良：来源为检验单，数量取检验的不良数")
    void createFromInspection() {
        MesInspection inspection = new MesInspection();
        inspection.setInspectionId(400L);
        inspection.setWorkorderId(10L);
        inspection.setTaskId(20L);
        inspection.setMaterialId(50L);
        inspection.setDefectQty(new BigDecimal("3"));

        service.createFromInspection(inspection, "检验单 QC001 检验不合格");

        ArgumentCaptor<MesDefectRecord> captor = ArgumentCaptor.forClass(MesDefectRecord.class);
        verify(defectMapper).insert(captor.capture());
        MesDefectRecord saved = captor.getValue();
        assertThat(saved.getSourceType()).isEqualTo("inspection");
        assertThat(saved.getSourceId()).isEqualTo(400L);
        assertThat(saved.getTaskId()).isEqualTo(20L);
        assertThat(saved.getDefectQty()).isEqualByComparingTo("3");
        assertThat(saved.getHandleStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("处置：待处置 → 已处置，写入处置人与时间")
    void handle() {
        when(defectMapper.selectById(500L)).thenReturn(record("manual", null, "pending"));
        when(defectMapper.update(any(), any())).thenReturn(1);
        MesDefectHandleBo bo = new MesDefectHandleBo();
        bo.setHandleMethod("rework");
        bo.setHandleRemark("返工后复检");

        service.handle(500L, bo);

        ArgumentCaptor<MesDefectRecord> change = ArgumentCaptor.forClass(MesDefectRecord.class);
        verify(defectMapper).update(change.capture(), any());
        assertThat(change.getValue().getHandleStatus()).isEqualTo("handled");
        assertThat(change.getValue().getHandleMethod()).isEqualTo("rework");
        assertThat(change.getValue().getHandleUserId()).isEqualTo(9L);
        assertThat(change.getValue().getHandleTime()).isNotNull();
        verify(statusLogService).record(BizType.DEFECT_RECORD, 500L, "NG001", "handle", "pending", "handled", "返工");
    }

    @Test
    @DisplayName("已处置的不良不能再次处置；处置方式无效时拒绝；状态已变化时提示刷新")
    void handleRejects() {
        MesDefectHandleBo bo = new MesDefectHandleBo();
        bo.setHandleMethod("scrap");
        when(defectMapper.selectById(500L)).thenReturn(record("manual", null, "handled"));
        assertThatThrownBy(() -> service.handle(500L, bo)).hasMessageContaining("不能处置");

        when(defectMapper.selectById(500L)).thenReturn(record("manual", null, "pending"));
        when(defectMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.handle(500L, bo)).hasMessageContaining("状态已变化");

        bo.setHandleMethod("burn");
        assertThatThrownBy(() -> service.handle(500L, bo)).hasMessageContaining("处置方式不正确");
    }

    @Test
    @DisplayName("列表带来源单据编号、工单编号与工序名称")
    void list() {
        when(defectMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(
            record("inspection", 400L, "pending"), record("report", 600L, "pending")));
        when(workorderMapper.selectByIds(any())).thenReturn(List.of(workorder("in_progress")));
        MesInspection inspection = new MesInspection();
        inspection.setInspectionId(400L);
        inspection.setInspectionCode("QC001");
        when(inspectionMapper.selectByIds(any())).thenReturn(List.of(inspection));
        MesProductionReport report = new MesProductionReport();
        report.setReportId(600L);
        report.setReportCode("RP001");
        when(reportMapper.selectByIds(any())).thenReturn(List.of(report));

        PageResult<MesDefectRecordVo> page = service.queryPageList(new MesDefectRecordBo(), new PageParam(1, 10));

        assertThat(page.getItems()).extracting(MesDefectRecordVo::getSourceCode).containsExactly("QC001", "RP001");
        assertThat(page.getItems()).allSatisfy(vo -> assertThat(vo.getWorkorderCode()).isEqualTo("WO001"));
        when(defectMapper.selectById(500L)).thenReturn(record("manual", null, "pending"));
        assertThat(service.queryById(500L).getDefectCode()).isEqualTo("NG001");
        service.logs(500L);
        verify(statusLogService).list(BizType.DEFECT_RECORD, 500L);
    }

}
