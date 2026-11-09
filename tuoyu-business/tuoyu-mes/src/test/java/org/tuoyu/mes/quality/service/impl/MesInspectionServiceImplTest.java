package org.tuoyu.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.domain.MesInspectionScheme;
import org.tuoyu.mes.quality.domain.bo.MesInspectionBo;
import org.tuoyu.mes.quality.domain.bo.MesInspectionResultBo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionSchemeVo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionVo;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionSchemeMapper;
import org.tuoyu.mes.quality.service.IMesDefectRecordService;
import org.tuoyu.mes.quality.service.IMesInspectionSchemeService;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("检验单：新增规则、检验数量与结论、不合格登记不良")
class MesInspectionServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesInspectionMapper inspectionMapper = MesTestSupport.mapper(MesInspectionMapper.class);
    private final MesInspectionSchemeMapper schemeMapper = MesTestSupport.mapper(MesInspectionSchemeMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesOperationTaskMapper taskMapper = MesTestSupport.mapper(MesOperationTaskMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final IMesInspectionSchemeService schemeService = mock(IMesInspectionSchemeService.class);
    private final IMesDefectRecordService defectService = mock(IMesDefectRecordService.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);
    private final RemoteUserService userService = mock(RemoteUserService.class);

    private final MesInspectionServiceImpl service = new MesInspectionServiceImpl(inspectionMapper, schemeMapper,
        workorderMapper, taskMapper, materialMapper, schemeService, defectService, codeService, statusLogService,
        userService);

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        previous = MesTestSupport.loginAs(8L);
        when(codeService.next(CodeRules.INSPECTION)).thenReturn("QC001");
        when(inspectionMapper.insert(any(MesInspection.class))).thenAnswer(invocation -> {
            invocation.<MesInspection>getArgument(0).setInspectionId(400L);
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

    private static MesOperationTask task(Long workorderId, String status) {
        MesOperationTask task = new MesOperationTask();
        task.setTaskId(20L);
        task.setWorkorderId(workorderId);
        task.setOperationCode("OP10");
        task.setOperationName("装配");
        task.setTaskStatus(status);
        return task;
    }

    private static MesInspectionBo bo(String type, Long taskId) {
        MesInspectionBo bo = new MesInspectionBo();
        bo.setInspectionType(type);
        bo.setWorkorderId(10L);
        bo.setTaskId(taskId);
        return bo;
    }

    private static MesInspection pending() {
        MesInspection inspection = new MesInspection();
        inspection.setInspectionId(400L);
        inspection.setInspectionCode("QC001");
        inspection.setInspectionType("first");
        inspection.setWorkorderId(10L);
        inspection.setTaskId(20L);
        inspection.setMaterialId(50L);
        inspection.setInspectionStatus("pending");
        return inspection;
    }

    private static MesInspectionResultBo result(String inspect, String qualified, String defect, String conclusion) {
        MesInspectionResultBo bo = new MesInspectionResultBo();
        bo.setInspectQty(new BigDecimal(inspect));
        bo.setQualifiedQty(new BigDecimal(qualified));
        bo.setDefectQty(new BigDecimal(defect));
        bo.setInspectionResult(conclusion);
        return bo;
    }

    @Test
    @DisplayName("首检单：未指定方案时按产品与工序自动匹配，检验数量默认取方案的抽样数")
    void insertFirstWithMatchedScheme() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("released"));
        when(taskMapper.selectById(20L)).thenReturn(task(10L, "dispatched"));
        MesInspectionSchemeVo matched = new MesInspectionSchemeVo();
        matched.setSchemeId(30L);
        matched.setSampleQty(new BigDecimal("5"));
        when(schemeService.match("first", 50L, "OP10")).thenReturn(matched);

        service.insertByBo(bo("first", 20L));

        ArgumentCaptor<MesInspection> captor = ArgumentCaptor.forClass(MesInspection.class);
        verify(inspectionMapper).insert(captor.capture());
        MesInspection saved = captor.getValue();
        assertThat(saved.getInspectionCode()).isEqualTo("QC001");
        assertThat(saved.getTaskId()).isEqualTo(20L);
        assertThat(saved.getSchemeId()).isEqualTo(30L);
        assertThat(saved.getMaterialId()).isEqualTo(50L);
        assertThat(saved.getInspectQty()).isEqualByComparingTo("5");
        assertThat(saved.getInspectionStatus()).isEqualTo("pending");
        verify(statusLogService).record(BizType.INSPECTION, 400L, "QC001", "create", null, "pending", null);
    }

    @Test
    @DisplayName("首检、巡检必须指定属于该工单的工序任务")
    void firstRequiresTask() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("in_progress"));
        assertThatThrownBy(() -> service.insertByBo(bo("patrol", null))).hasMessageContaining("必须指定工序任务");

        when(taskMapper.selectById(20L)).thenReturn(task(11L, "dispatched"));
        assertThatThrownBy(() -> service.insertByBo(bo("first", 20L))).hasMessageContaining("不属于该工单");

        when(taskMapper.selectById(20L)).thenReturn(task(10L, "completed"));
        assertThatThrownBy(() -> service.insertByBo(bo("first", 20L))).hasMessageContaining("工序任务当前为");
        verify(inspectionMapper, never()).insert(any(MesInspection.class));
    }

    @Test
    @DisplayName("已完工的工单只能做完工检；草稿工单不能检验")
    void workorderState() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("completed"));
        when(taskMapper.selectById(20L)).thenReturn(task(10L, "in_progress"));
        assertThatThrownBy(() -> service.insertByBo(bo("first", 20L))).hasMessageContaining("不能首检");

        service.insertByBo(bo("final", null));
        verify(inspectionMapper).insert(any(MesInspection.class));

        when(workorderMapper.selectById(10L)).thenReturn(workorder("draft"));
        assertThatThrownBy(() -> service.insertByBo(bo("final", null))).hasMessageContaining("不能完工检");
    }

    @Test
    @DisplayName("指定的方案必须启用、类型一致并适用于工单产品")
    void explicitScheme() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("in_progress"));
        MesInspectionScheme scheme = new MesInspectionScheme();
        scheme.setSchemeId(30L);
        scheme.setSchemeName("巡检方案");
        scheme.setInspectionType("patrol");
        scheme.setStatus("0");
        when(schemeMapper.selectById(30L)).thenReturn(scheme);
        MesInspectionBo bo = bo("final", null);
        bo.setSchemeId(30L);
        assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("不适用于完工检");

        scheme.setInspectionType("final");
        scheme.setMaterialId(51L);
        assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("不适用于该工单的产品");

        scheme.setMaterialId(null);
        scheme.setStatus("1");
        assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("已停用");

        scheme.setStatus("0");
        bo.setInspectQty(new BigDecimal("3"));
        service.insertByBo(bo);
        verify(schemeService, never()).match(anyString(), any(), any());
    }

    @Test
    @DisplayName("合格数与不良数之和必须等于检验数")
    void quantitiesMustMatch() {
        when(inspectionMapper.selectById(400L)).thenReturn(pending());

        assertThatThrownBy(() -> service.inspect(400L, result("10", "8", "1", "passed")))
            .hasMessageContaining("必须等于检验数量");
        verify(inspectionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("检验合格：写入结论、检验员与时间，不登记不良")
    void inspectPassed() {
        when(inspectionMapper.selectById(400L)).thenReturn(pending());
        when(inspectionMapper.update(any(), any())).thenReturn(1);

        service.inspect(400L, result("10", "10", "0", "passed"));

        ArgumentCaptor<MesInspection> change = ArgumentCaptor.forClass(MesInspection.class);
        verify(inspectionMapper).update(change.capture(), any());
        assertThat(change.getValue().getInspectionStatus()).isEqualTo("completed");
        assertThat(change.getValue().getInspectionResult()).isEqualTo("passed");
        assertThat(change.getValue().getInspectorUserId()).isEqualTo(8L);
        assertThat(change.getValue().getInspectionTime()).isNotNull();
        verify(statusLogService).record(BizType.INSPECTION, 400L, "QC001", "inspect", "pending", "completed", "合格");
        verify(defectService, never()).createFromInspection(any(), anyString());
    }

    @Test
    @DisplayName("检验不合格且有不良数时登记不良记录（来源为检验）")
    void inspectRejectedCreatesDefect() {
        when(inspectionMapper.selectById(400L)).thenReturn(pending());
        when(inspectionMapper.update(any(), any())).thenReturn(1);
        MesInspectionResultBo bo = result("10", "7", "3", "rejected");
        bo.setResultRemark("尺寸超差");

        service.inspect(400L, bo);

        ArgumentCaptor<MesInspection> captor = ArgumentCaptor.forClass(MesInspection.class);
        verify(defectService).createFromInspection(captor.capture(), contains("尺寸超差"));
        assertThat(captor.getValue().getDefectQty()).isEqualByComparingTo("3");
        assertThat(captor.getValue().getInspectionId()).isEqualTo(400L);
    }

    @Test
    @DisplayName("检验不合格但不良数为 0 时不登记不良；已完成的检验单不能再次检验")
    void rejectedWithoutDefect() {
        when(inspectionMapper.selectById(400L)).thenReturn(pending());
        when(inspectionMapper.update(any(), any())).thenReturn(1);

        service.inspect(400L, result("10", "10", "0", "rejected"));
        verify(defectService, never()).createFromInspection(any(), anyString());

        MesInspection completed = pending();
        completed.setInspectionStatus("completed");
        when(inspectionMapper.selectById(400L)).thenReturn(completed);
        assertThatThrownBy(() -> service.inspect(400L, result("1", "1", "0", "passed"))).hasMessageContaining("不能检验");

        assertThatThrownBy(() -> service.inspect(400L, result("1", "1", "0", "maybe"))).hasMessageContaining("检验结论不正确");
    }

    @Test
    @DisplayName("状态已变化时提示刷新")
    void concurrentInspect() {
        when(inspectionMapper.selectById(400L)).thenReturn(pending());
        when(inspectionMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.inspect(400L, result("1", "1", "0", "passed"))).hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("只能删除待检验的检验单")
    void deletePendingOnly() {
        MesInspection completed = pending();
        completed.setInspectionStatus("completed");
        when(inspectionMapper.selectByIds(any())).thenReturn(List.of(completed));
        assertThatThrownBy(() -> service.deleteByIds(List.of(400L))).hasMessageContaining("不能删除");

        when(inspectionMapper.selectByIds(any())).thenReturn(List.of(pending()));
        when(inspectionMapper.delete(any(Wrapper.class))).thenReturn(1);
        service.deleteByIds(List.of(400L));
    }

    @Test
    @DisplayName("详情带方案的检验项目；导出时填写检验员昵称")
    void queries() {
        MesInspection inspection = pending();
        inspection.setSchemeId(30L);
        inspection.setInspectorUserId(8L);
        when(inspectionMapper.selectById(400L)).thenReturn(inspection);
        MesInspectionScheme scheme = new MesInspectionScheme();
        scheme.setSchemeId(30L);
        scheme.setSchemeName("首检方案");
        scheme.setCheckItems("外观\n尺寸");
        when(schemeMapper.selectById(30L)).thenReturn(scheme);
        when(schemeMapper.selectByIds(any())).thenReturn(List.of(scheme));
        when(taskMapper.selectByIds(any())).thenReturn(List.of(task(10L, "in_progress")));
        when(workorderMapper.selectByIds(any())).thenReturn(List.of(workorder("in_progress")));

        MesInspectionVo vo = service.queryById(400L);
        assertThat(vo.getCheckItems()).contains("尺寸");
        assertThat(vo.getSchemeName()).isEqualTo("首检方案");
        assertThat(vo.getOperationName()).isEqualTo("装配");
        assertThat(vo.getWorkorderCode()).isEqualTo("WO001");

        when(inspectionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(inspection));
        when(userService.selectUserNicksByIds(any())).thenReturn(Map.of(8L, "检验员"));
        assertThat(service.queryList(new MesInspectionBo())).singleElement()
            .satisfies(item -> assertThat(item.getInspectorName()).isEqualTo("检验员"));
        verify(statusLogService, never()).list(eq(BizType.INSPECTION), eq(1L));
    }

}
