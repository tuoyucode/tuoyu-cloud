package org.tuoyu.mes.andon.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;
import org.tuoyu.mes.andon.domain.bo.MesAndonEventBo;
import org.tuoyu.mes.andon.domain.vo.MesAndonEventVo;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.andon.mapper.MesDowntimeReasonMapper;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.resource.api.RemoteMessageService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("安灯事件：上报位置校验、状态流转、处理人校验与停机时长")
class MesAndonEventServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 10, 0);
    private static final long ME = 7L;

    private final MesAndonEventMapper eventMapper = MesTestSupport.mapper(MesAndonEventMapper.class);
    private final MesDowntimeReasonMapper reasonMapper = MesTestSupport.mapper(MesDowntimeReasonMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);
    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);
    private final RemoteMessageService messageService = mock(RemoteMessageService.class);

    private final MesAndonEventServiceImpl service = new MesAndonEventServiceImpl(eventMapper, reasonMapper,
        workshopMapper, lineMapper, stationMapper, deviceMapper, workorderMapper, codeService, statusLogService,
        messageService);

    private final UserContextProvider previous = MesTestSupport.loginAs(ME);

    {
        service.setClock(Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        service.setPermissionChecker(permission -> false);
    }

    @AfterEach
    void restore() {
        MesTestSupport.restore(previous);
    }

    private static MesAndonEvent event(String status) {
        MesAndonEvent event = new MesAndonEvent();
        event.setEventId(1L);
        event.setEventCode("AD001");
        event.setTitle("设备停机");
        event.setEventLevel("normal");
        event.setEventStatus(status);
        event.setReportUserId(3L);
        event.setReportTime(NOW.minusMinutes(90));
        return event;
    }

    private static MesAndonEventBo reportBo() {
        MesAndonEventBo bo = new MesAndonEventBo();
        bo.setEventType("equipment");
        bo.setEventLevel("urgent");
        bo.setTitle("主轴异响");
        bo.setWorkshopId(1L);
        return bo;
    }

    private static MesWorkshop workshop() {
        MesWorkshop workshop = new MesWorkshop();
        workshop.setWorkshopId(1L);
        workshop.setWorkshopName("一车间");
        return workshop;
    }

    private static MesLine line(long workshopId) {
        MesLine line = new MesLine();
        line.setLineId(2L);
        line.setLineName("一线");
        line.setWorkshopId(workshopId);
        return line;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<MesAndonEvent> capturedUpdate() {
        ArgumentCaptor<Wrapper<MesAndonEvent>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(eventMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<MesAndonEvent> update = (LambdaUpdateWrapper<MesAndonEvent>) captor.getValue();
        // 条件参数在生成 SQL 片段时才登记
        update.getSqlSegment();
        return update;
    }

    @Test
    @DisplayName("上报：编号来自安灯规则，上报人为当前用户，状态为已上报并记录履历")
    void reports() {
        when(workshopMapper.selectById(1L)).thenReturn(workshop());
        when(lineMapper.selectById(2L)).thenReturn(line(1L));
        when(codeService.next(CodeRules.ANDON)).thenReturn("AD001");
        when(eventMapper.insert(any(MesAndonEvent.class))).thenAnswer(invocation -> {
            invocation.<MesAndonEvent>getArgument(0).setEventId(9L);
            return 1;
        });
        MesAndonEventBo bo = reportBo();
        bo.setLineId(2L);

        service.report(bo);

        ArgumentCaptor<MesAndonEvent> captor = ArgumentCaptor.forClass(MesAndonEvent.class);
        verify(eventMapper).insert(captor.capture());
        MesAndonEvent saved = captor.getValue();
        assertThat(saved.getEventCode()).isEqualTo("AD001");
        assertThat(saved.getEventStatus()).isEqualTo("reported");
        assertThat(saved.getReportUserId()).isEqualTo(ME);
        assertThat(saved.getReportTime()).isEqualTo(NOW);
        verify(statusLogService).record(BizType.ANDON_EVENT, 9L, "AD001", "create", null, "reported", null);
    }

    @Test
    @DisplayName("产线不属于所选车间时拒绝上报")
    void rejectsLineOfOtherWorkshop() {
        when(workshopMapper.selectById(1L)).thenReturn(workshop());
        when(lineMapper.selectById(2L)).thenReturn(line(99L));
        MesAndonEventBo bo = reportBo();
        bo.setLineId(2L);

        assertThatThrownBy(() -> service.report(bo)).hasMessageContaining("不属于所选车间");
        verify(eventMapper, never()).insert(any(MesAndonEvent.class));
    }

    @Test
    @DisplayName("工位不属于所选产线时拒绝上报")
    void rejectsStationOfOtherLine() {
        when(workshopMapper.selectById(1L)).thenReturn(workshop());
        when(lineMapper.selectById(2L)).thenReturn(line(1L));
        MesStation station = new MesStation();
        station.setStationId(3L);
        station.setStationName("工位三");
        station.setWorkshopId(1L);
        station.setLineId(88L);
        when(stationMapper.selectById(3L)).thenReturn(station);
        MesAndonEventBo bo = reportBo();
        bo.setLineId(2L);
        bo.setStationId(3L);

        assertThatThrownBy(() -> service.report(bo)).hasMessageContaining("不属于所选产线");
    }

    @Test
    @DisplayName("设备不属于所选车间时拒绝上报")
    void rejectsDeviceOfOtherWorkshop() {
        when(workshopMapper.selectById(1L)).thenReturn(workshop());
        MesDevice device = new MesDevice();
        device.setDeviceId(4L);
        device.setDeviceName("CNC-01");
        device.setWorkshopId(2L);
        when(deviceMapper.selectById(4L)).thenReturn(device);
        MesAndonEventBo bo = reportBo();
        bo.setDeviceId(4L);

        assertThatThrownBy(() -> service.report(bo)).hasMessageContaining("CNC-01");
    }

    @Test
    @DisplayName("异常类型、车间不正确时拒绝上报")
    void rejectsInvalidReport() {
        MesAndonEventBo bo = reportBo();
        bo.setEventType("fire");
        assertThatThrownBy(() -> service.report(bo)).hasMessageContaining("异常类型");

        assertThatThrownBy(() -> service.report(reportBo())).hasMessageContaining("车间不存在");
    }

    @Test
    @DisplayName("派单：已上报 → 已派单，写处理人并推送消息")
    void assigns() {
        when(eventMapper.selectById(1L)).thenReturn(event("reported"));
        when(eventMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.assign(1L, 20L);

        LambdaUpdateWrapper<MesAndonEvent> update = capturedUpdate();
        assertThat(update.getSqlSet()).contains("handler_user_id").contains("event_status");
        assertThat(update.getParamNameValuePairs().values()).contains(20L, "assigned", "reported");
        verify(statusLogService).record(BizType.ANDON_EVENT, 1L, "AD001", "assign", "reported", "assigned", null);
        verify(messageService).publishMessage(List.of(20L), "安灯事件 AD001 已派给你：设备停机");
    }

    @Test
    @DisplayName("推送失败不影响派单；改派时履历注明改派")
    void reassignIgnoresPushFailure() {
        MesAndonEvent event = event("assigned");
        event.setHandlerUserId(20L);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(eventMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        doThrow(new IllegalStateException("推送服务不可用")).when(messageService).publishMessage(any(), any());

        assertThatCode(() -> service.assign(1L, 21L)).doesNotThrowAnyException();
        verify(statusLogService).record(BizType.ANDON_EVENT, 1L, "AD001", "assign", "assigned", "assigned", "改派");
    }

    @Test
    @DisplayName("已解决的事件不能派单")
    void rejectsAssignResolved() {
        when(eventMapper.selectById(1L)).thenReturn(event("resolved"));

        assertThatThrownBy(() -> service.assign(1L, 20L)).hasMessageContaining("已解决").hasMessageContaining("派单");
    }

    @Test
    @DisplayName("并发派单时只有一个成功，另一个提示刷新")
    void assignConcurrent() {
        when(eventMapper.selectById(1L)).thenReturn(event("reported"));

        assertThatThrownBy(() -> service.assign(1L, 20L)).hasMessageContaining("状态已变化");
        verify(messageService, never()).publishMessage(any(), any());
    }

    @Test
    @DisplayName("开始处理：处理人本人可以")
    void handlerProcesses() {
        MesAndonEvent event = event("assigned");
        event.setHandlerUserId(ME);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(eventMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.process(1L);

        assertThat(capturedUpdate().getParamNameValuePairs().values()).contains("processing", NOW);
        verify(statusLogService).record(BizType.ANDON_EVENT, 1L, "AD001", "process", "assigned", "processing", null);
    }

    @Test
    @DisplayName("开始处理：不是处理人、也没有派单权限时拒绝")
    void othersCannotProcess() {
        MesAndonEvent event = event("assigned");
        event.setHandlerUserId(20L);
        when(eventMapper.selectById(1L)).thenReturn(event);

        assertThatThrownBy(() -> service.process(1L)).hasMessageContaining("处理人本人");
    }

    @Test
    @DisplayName("开始处理：有派单权限的人可以代为开始")
    void assignerProcesses() {
        MesAndonEvent event = event("assigned");
        event.setHandlerUserId(20L);
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(eventMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.setPermissionChecker("mes:andon:assign"::equals);

        assertThatCode(() -> service.process(1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("解决：停线级别记录从上报到解决的分钟数")
    void resolvesCritical() {
        MesAndonEvent event = event("processing");
        event.setHandlerUserId(ME);
        event.setEventLevel("critical");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(eventMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.resolve(1L, "更换轴承", null);

        assertThat(capturedUpdate().getParamNameValuePairs().values()).contains(90, "resolved", "更换轴承");
        verify(statusLogService).record(BizType.ANDON_EVENT, 1L, "AD001", "resolve", "processing", "resolved", "更换轴承");
    }

    @Test
    @DisplayName("停机分钟数：一般级别且没有停机原因时不记录，填了停机原因时记录")
    void downtimeRule() {
        MesAndonEvent event = event("processing");
        assertThat(MesAndonEventServiceImpl.downtimeMinutes(event, null, NOW)).isNull();
        assertThat(MesAndonEventServiceImpl.downtimeMinutes(event, 5L, NOW)).isEqualTo(90);
        event.setEventLevel("critical");
        assertThat(MesAndonEventServiceImpl.downtimeMinutes(event, null, NOW)).isEqualTo(90);
    }

    @Test
    @DisplayName("解决时选择的停机原因必须启用")
    void resolveChecksReason() {
        MesAndonEvent event = event("processing");
        event.setHandlerUserId(ME);
        when(eventMapper.selectById(1L)).thenReturn(event);
        MesDowntimeReason reason = new MesDowntimeReason();
        reason.setReasonId(5L);
        reason.setReasonName("缺料");
        reason.setStatus("1");
        when(reasonMapper.selectById(5L)).thenReturn(reason);

        assertThatThrownBy(() -> service.resolve(1L, "补料", 5L)).hasMessageContaining("已停用");
    }

    @Test
    @DisplayName("只有处理中的事件可以解决，处理说明必填")
    void resolveRules() {
        when(eventMapper.selectById(1L)).thenReturn(event("assigned"));

        assertThatThrownBy(() -> service.resolve(1L, "完成", null)).hasMessageContaining("已派单");
        assertThatThrownBy(() -> service.resolve(1L, " ", null)).hasMessageContaining("处理说明");
    }

    @Test
    @DisplayName("关闭：已上报的误报必须填写原因")
    void closeReportedNeedsReason() {
        when(eventMapper.selectById(1L)).thenReturn(event("reported"));

        assertThatThrownBy(() -> service.close(1L, null)).hasMessageContaining("原因");
    }

    @Test
    @DisplayName("关闭：已上报的误报填写原因后可以直接关闭")
    void closeFalseAlarm() {
        when(eventMapper.selectById(1L)).thenReturn(event("reported"));
        when(eventMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.close(1L, "误报");

        verify(statusLogService).record(BizType.ANDON_EVENT, 1L, "AD001", "close", "reported", "closed", "误报");
    }

    @Test
    @DisplayName("关闭：已解决的事件由上报人确认，其他人没有派单权限时拒绝")
    void closeResolvedByReporter() {
        MesAndonEvent event = event("resolved");
        when(eventMapper.selectById(1L)).thenReturn(event);
        when(eventMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assertThatThrownBy(() -> service.close(1L, null)).hasMessageContaining("上报人");

        event.setReportUserId(ME);
        assertThatCode(() -> service.close(1L, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("处理中的事件不能关闭")
    void rejectsCloseProcessing() {
        when(eventMapper.selectById(1L)).thenReturn(event("processing"));

        assertThatThrownBy(() -> service.close(1L, "x")).hasMessageContaining("处理中");
    }

    @Test
    @DisplayName("时间范围参数：只有日期时开始取 0 点、结束取当天最后一秒；格式不对时拒绝")
    void parsesTimeRange() {
        assertThat(MesAndonEventServiceImpl.parseTime("2026-11-08", false)).isEqualTo(LocalDateTime.of(2026, 11, 8, 0, 0));
        assertThat(MesAndonEventServiceImpl.parseTime("2026-11-08", true)).isEqualTo(LocalDateTime.of(2026, 11, 8, 23, 59, 59));
        assertThat(MesAndonEventServiceImpl.parseTime("2026-11-08 12:30:00", true)).isEqualTo(LocalDateTime.of(2026, 11, 8, 12, 30));
        assertThat(MesAndonEventServiceImpl.parseTime(" ", true)).isNull();
        assertThatThrownBy(() -> MesAndonEventServiceImpl.parseTime("08/11/2026", true)).hasMessageContaining("时间格式");
    }

    @Test
    @DisplayName("列表：只看我的按当前用户筛选，并填充名称与持续分钟数")
    @SuppressWarnings("unchecked")
    void listMine() {
        MesAndonEvent row = event("processing");
        row.setWorkshopId(1L);
        when(eventMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(row));
        when(workshopMapper.selectByIds(anyCollection())).thenReturn(List.of(workshop()));
        MesAndonEventBo bo = new MesAndonEventBo();
        bo.setMine(true);
        bo.setParams(Map.of("beginTime", "2026-11-01", "endTime", "2026-11-08"));

        List<MesAndonEventVo> items = service.queryPageList(bo, new PageParam(1, 10)).getItems();

        ArgumentCaptor<Wrapper<MesAndonEvent>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(eventMapper).selectList(any(IPage.class), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("handler_user_id").contains("report_time");
        assertThat(items).singleElement().satisfies(vo -> {
            assertThat(vo.getWorkshopName()).isEqualTo("一车间");
            assertThat(vo.getDurationMinutes()).isEqualTo(90L);
        });
    }

    @Test
    @DisplayName("持续分钟数：已解决的按解决时间计算")
    void durationUsesResolveTime() {
        assertThat(MesAndonEventServiceImpl.durationMinutes(NOW.minusMinutes(30), NOW.minusMinutes(10), NOW)).isEqualTo(20L);
        assertThat(MesAndonEventServiceImpl.durationMinutes(null, null, NOW)).isNull();
    }

}
