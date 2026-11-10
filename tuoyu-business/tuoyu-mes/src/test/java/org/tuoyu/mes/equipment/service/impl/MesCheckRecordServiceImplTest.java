package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.andon.domain.bo.MesAndonEventBo;
import org.tuoyu.mes.andon.service.IMesAndonEventService;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.equipment.domain.MesCheckItem;
import org.tuoyu.mes.equipment.domain.MesCheckRecord;
import org.tuoyu.mes.equipment.domain.MesCheckRecordDetail;
import org.tuoyu.mes.equipment.domain.bo.MesCheckAndonBo;
import org.tuoyu.mes.equipment.domain.bo.MesCheckRecordBo;
import org.tuoyu.mes.equipment.domain.bo.MesCheckRecordDetailBo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckRecordVo;
import org.tuoyu.mes.equipment.mapper.MesCheckRecordDetailMapper;
import org.tuoyu.mes.equipment.mapper.MesCheckRecordMapper;
import org.tuoyu.mes.equipment.service.IMesCheckItemService;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("点检记录：逐项判定、异常结论、作废与发起安灯")
class MesCheckRecordServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 9, 30);

    private final MesCheckRecordMapper recordMapper = MesTestSupport.mapper(MesCheckRecordMapper.class);
    private final MesCheckRecordDetailMapper detailMapper = MesTestSupport.mapper(MesCheckRecordDetailMapper.class);
    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final IMesCheckItemService checkItemService = mock(IMesCheckItemService.class);
    private final IMesAndonEventService andonService = mock(IMesAndonEventService.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);
    private final RemoteUserService userService = mock(RemoteUserService.class);

    private final MesCheckRecordServiceImpl service = new MesCheckRecordServiceImpl(recordMapper, detailMapper,
        deviceMapper, checkItemService, andonService, codeService, statusLogService, userService);

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        previous = MesTestSupport.loginAs(8L);
        service.setClock(Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        when(codeService.next(CodeRules.CHECK_RECORD)).thenReturn("DJ001");
        when(recordMapper.insert(any(MesCheckRecord.class))).thenAnswer(invocation -> {
            invocation.<MesCheckRecord>getArgument(0).setCheckRecordId(500L);
            return 1;
        });
        when(deviceMapper.selectById(30L)).thenReturn(device());
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.restore(previous);
    }

    private static MesDevice device() {
        MesDevice device = new MesDevice();
        device.setDeviceId(30L);
        device.setDeviceCode("D01");
        device.setDeviceName("冲压机");
        device.setDeviceType("press");
        device.setWorkshopId(2L);
        device.setLineId(3L);
        device.setStationId(4L);
        device.setStatus("0");
        return device;
    }

    private static MesCheckItem judgeItem(long id, String mandatory) {
        MesCheckItem item = new MesCheckItem();
        item.setCheckItemId(id);
        item.setItemName("外观" + id);
        item.setResultType("judge");
        item.setMandatory(mandatory);
        item.setSortNo((int) id);
        return item;
    }

    private static MesCheckItem numericItem(long id, String lower, String upper) {
        MesCheckItem item = new MesCheckItem();
        item.setCheckItemId(id);
        item.setItemName("油压" + id);
        item.setResultType("numeric");
        item.setLowerLimit(lower == null ? null : new BigDecimal(lower));
        item.setUpperLimit(upper == null ? null : new BigDecimal(upper));
        item.setUnit("MPa");
        item.setMandatory("Y");
        item.setSortNo((int) id);
        return item;
    }

    private static MesCheckRecordDetailBo judged(long itemId, String result) {
        MesCheckRecordDetailBo input = new MesCheckRecordDetailBo();
        input.setCheckItemId(itemId);
        input.setItemResult(result);
        return input;
    }

    private static MesCheckRecordDetailBo measured(long itemId, String value) {
        MesCheckRecordDetailBo input = new MesCheckRecordDetailBo();
        input.setCheckItemId(itemId);
        input.setMeasuredValue(new BigDecimal(value));
        // 数值型项目不采用客户端的结论
        input.setItemResult("ok");
        return input;
    }

    private static MesCheckRecordBo bo(MesCheckRecordDetailBo... details) {
        MesCheckRecordBo bo = new MesCheckRecordBo();
        bo.setDeviceId(30L);
        bo.setDetails(List.of(details));
        return bo;
    }

    private static MesCheckRecord completed(String result, String andonRaised) {
        MesCheckRecord record = new MesCheckRecord();
        record.setCheckRecordId(500L);
        record.setRecordCode("DJ001");
        record.setDeviceId(30L);
        record.setCheckResult(result);
        record.setAbnormalCount("abnormal".equals(result) ? 1 : 0);
        record.setRecordStatus("completed");
        record.setAndonRaised(andonRaised);
        return record;
    }

    @SuppressWarnings("unchecked")
    private List<MesCheckRecordDetail> insertedDetails() {
        ArgumentCaptor<Collection<MesCheckRecordDetail>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(detailMapper).insert(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    @Test
    @DisplayName("全部合格：结论为正常，明细保存项目快照，点检人为当前用户")
    void insertNormal() {
        when(checkItemService.applicableItems(any())).thenReturn(List.of(judgeItem(1, "Y"), numericItem(2, "1", "5")));

        service.insertByBo(bo(judged(1, "ok"), measured(2, "5")));

        ArgumentCaptor<MesCheckRecord> record = ArgumentCaptor.forClass(MesCheckRecord.class);
        verify(recordMapper).insert(record.capture());
        assertThat(record.getValue().getCheckResult()).isEqualTo("normal");
        assertThat(record.getValue().getAbnormalCount()).isZero();
        assertThat(record.getValue().getCheckerUserId()).isEqualTo(8L);
        assertThat(record.getValue().getCheckTime()).isEqualTo(NOW);
        assertThat(record.getValue().getAndonRaised()).isEqualTo("N");
        List<MesCheckRecordDetail> details = insertedDetails();
        assertThat(details).extracting(MesCheckRecordDetail::getItemResult).containsExactly("ok", "ok");
        assertThat(details).allSatisfy(d -> assertThat(d.getCheckRecordId()).isEqualTo(500L));
        assertThat(details.get(1).getUnit()).isEqualTo("MPa");
        verify(statusLogService).record(eq(BizType.CHECK_RECORD), eq(500L), eq("DJ001"), eq("create"), isNull(),
            eq("completed"), isNull());
    }

    @Test
    @DisplayName("数值超出上下限自动判为不合格（不采用客户端结论），有不合格项时结论为异常；非必检项不填记为未检")
    void insertAbnormal() {
        when(checkItemService.applicableItems(any())).thenReturn(List.of(numericItem(1, "1", "5"),
            numericItem(2, null, "10"), judgeItem(3, "N")));

        service.insertByBo(bo(measured(1, "5.1"), measured(2, "-3")));

        ArgumentCaptor<MesCheckRecord> record = ArgumentCaptor.forClass(MesCheckRecord.class);
        verify(recordMapper).insert(record.capture());
        assertThat(record.getValue().getCheckResult()).isEqualTo("abnormal");
        assertThat(record.getValue().getAbnormalCount()).isEqualTo(1);
        assertThat(insertedDetails()).extracting(MesCheckRecordDetail::getItemResult)
            .containsExactly("ng", "ok", null);
    }

    @Test
    @DisplayName("必检项未填写、填写了不适用的项目、重复填写、结果值不正确时拒绝")
    void insertRejectsInvalidInput() {
        when(checkItemService.applicableItems(any())).thenReturn(List.of(judgeItem(1, "Y"), judgeItem(2, "N")));

        assertThatThrownBy(() -> service.insertByBo(bo(judged(2, "ok")))).hasMessageContaining("必检项目“外观1”");
        assertThatThrownBy(() -> service.insertByBo(bo(judged(1, "ok"), judged(9, "ok")))).hasMessageContaining("不适用");
        assertThatThrownBy(() -> service.insertByBo(bo(judged(1, "ok"), judged(1, "ng")))).hasMessageContaining("重复");
        assertThatThrownBy(() -> service.insertByBo(bo(judged(1, "good")))).hasMessageContaining("结果不正确");
        verify(recordMapper, never()).insert(any(MesCheckRecord.class));
    }

    @Test
    @DisplayName("设备不存在、已停用或没有点检项目时拒绝")
    void insertRequiresUsableDevice() {
        MesCheckRecordBo missing = bo(judged(1, "ok"));
        missing.setDeviceId(99L);
        assertThatThrownBy(() -> service.insertByBo(missing)).hasMessageContaining("设备不存在");

        when(checkItemService.applicableItems(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.insertByBo(bo(judged(1, "ok")))).hasMessageContaining("没有可用的点检项目");

        MesDevice disabled = device();
        disabled.setStatus("1");
        when(deviceMapper.selectById(30L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.insertByBo(bo(judged(1, "ok")))).hasMessageContaining("已停用");
    }

    @Test
    @DisplayName("作废：已提交 → 已作废，以原状态为条件；已作废的不能再作废；原因必填")
    void voidRecord() {
        when(recordMapper.selectById(500L)).thenReturn(completed("normal", "N"));
        when(recordMapper.update(any(), any())).thenReturn(1);

        service.voidRecord(500L, " 填错设备 ");

        ArgumentCaptor<MesCheckRecord> change = ArgumentCaptor.forClass(MesCheckRecord.class);
        verify(recordMapper).update(change.capture(), any());
        assertThat(change.getValue().getRecordStatus()).isEqualTo("voided");
        assertThat(change.getValue().getVoidReason()).isEqualTo("填错设备");
        verify(statusLogService).record(BizType.CHECK_RECORD, 500L, "DJ001", "void", "completed", "voided", "填错设备");

        assertThatThrownBy(() -> service.voidRecord(500L, " ")).hasMessageContaining("作废原因");
        MesCheckRecord voided = completed("normal", "N");
        voided.setRecordStatus("voided");
        when(recordMapper.selectById(500L)).thenReturn(voided);
        assertThatThrownBy(() -> service.voidRecord(500L, "再次")).hasMessageContaining("不能作废");
    }

    @Test
    @DisplayName("并发作废：条件更新影响 0 行时提示状态已变化")
    void voidConcurrently() {
        when(recordMapper.selectById(500L)).thenReturn(completed("normal", "N"));
        when(recordMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.voidRecord(500L, "填错")).hasMessageContaining("状态已变化");
        verify(statusLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("发起安灯：设备异常类、默认紧急，位置取设备的车间、产线与工位，描述列出不合格项；先占用再上报")
    void raiseAndon() {
        when(recordMapper.selectById(500L)).thenReturn(completed("abnormal", "N"));
        when(recordMapper.update(any(), any())).thenReturn(1);
        MesCheckRecordDetail failed = new MesCheckRecordDetail();
        failed.setItemName("油压");
        failed.setItemResult("ng");
        when(detailMapper.selectList(any(Wrapper.class))).thenReturn(List.of(failed));

        MesCheckAndonBo bo = new MesCheckAndonBo();
        bo.setContent("漏油");
        service.raiseAndon(500L, bo);

        ArgumentCaptor<MesCheckRecord> change = ArgumentCaptor.forClass(MesCheckRecord.class);
        verify(recordMapper).update(change.capture(), any());
        assertThat(change.getValue().getAndonRaised()).isEqualTo("Y");
        assertThat(change.getValue().getAndonTime()).isEqualTo(NOW);
        ArgumentCaptor<MesAndonEventBo> event = ArgumentCaptor.forClass(MesAndonEventBo.class);
        verify(andonService).report(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("equipment");
        assertThat(event.getValue().getEventLevel()).isEqualTo("urgent");
        assertThat(event.getValue().getTitle()).isEqualTo("设备点检异常：冲压机");
        assertThat(event.getValue().getContent()).isEqualTo("点检单 DJ001 有 1 项不合格：油压。漏油");
        assertThat(event.getValue().getWorkshopId()).isEqualTo(2L);
        assertThat(event.getValue().getLineId()).isEqualTo(3L);
        assertThat(event.getValue().getStationId()).isEqualTo(4L);
        assertThat(event.getValue().getDeviceId()).isEqualTo(30L);
        verify(statusLogService).record(BizType.CHECK_RECORD, 500L, "DJ001", "andon", "completed", "completed",
            "发起安灯（紧急）");
    }

    @Test
    @DisplayName("发起安灯：正常的、已发起过的、设备没有车间的、并发占用失败的都不上报")
    void raiseAndonRejected() {
        MesCheckAndonBo bo = new MesCheckAndonBo();
        when(recordMapper.selectById(500L)).thenReturn(completed("normal", "N"));
        assertThatThrownBy(() -> service.raiseAndon(500L, bo)).hasMessageContaining("不需要发起安灯");

        when(recordMapper.selectById(500L)).thenReturn(completed("abnormal", "Y"));
        assertThatThrownBy(() -> service.raiseAndon(500L, bo)).hasMessageContaining("已经发起过");

        when(recordMapper.selectById(500L)).thenReturn(completed("abnormal", "N"));
        MesDevice noWorkshop = device();
        noWorkshop.setWorkshopId(null);
        when(deviceMapper.selectById(30L)).thenReturn(noWorkshop);
        assertThatThrownBy(() -> service.raiseAndon(500L, bo)).hasMessageContaining("没有设置所属车间");

        when(deviceMapper.selectById(30L)).thenReturn(device());
        when(recordMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.raiseAndon(500L, bo)).hasMessageContaining("已经发起过安灯或状态已变化");

        bo.setEventLevel("wrong");
        assertThatThrownBy(() -> service.raiseAndon(500L, bo)).hasMessageContaining("紧急程度不正确");
        verify(andonService, never()).report(any());
    }

    @Test
    @DisplayName("详情带逐项结果；不在数据权限内或不存在时按不存在处理")
    void queryById() {
        when(recordMapper.selectById(500L)).thenReturn(completed("normal", "N"));
        MesCheckRecordDetail detail = new MesCheckRecordDetail();
        detail.setItemName("外观");
        detail.setItemResult("ok");
        when(detailMapper.selectList(any(Wrapper.class))).thenReturn(List.of(detail));
        when(deviceMapper.selectByIds(any())).thenReturn(List.of(device()));

        MesCheckRecordVo vo = service.queryById(500L);
        assertThat(vo.getDeviceName()).isEqualTo("冲压机");
        assertThat(vo.getDetails()).hasSize(1);

        assertThatThrownBy(() -> service.queryById(501L)).hasMessageContaining("点检记录不存在");
        assertThatThrownBy(() -> service.logs(501L)).hasMessageContaining("点检记录不存在");
        service.logs(500L);
        verify(statusLogService).list(BizType.CHECK_RECORD, 500L);
    }

    @Test
    @DisplayName("列表、导出与导出前统计")
    void listAndExport() {
        MesCheckRecordBo query = new MesCheckRecordBo();
        query.setRecordCode("DJ");
        query.setBeginCheckTime(NOW.minusDays(1));
        assertThat(service.queryPageList(query, new PageParam(1, 10)).getItems()).isEmpty();
        when(recordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(completed("normal", "N")));
        assertThat(service.queryList(query)).hasSize(1);
        assertThat(service.countList(query)).isZero();
    }

}
