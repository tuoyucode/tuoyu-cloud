package org.tuoyu.mes.andon.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.rpc.fallback.RemoteFallback;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;
import org.tuoyu.mes.andon.domain.bo.MesAndonEventBo;
import org.tuoyu.mes.andon.domain.vo.MesAndonEventVo;
import org.tuoyu.mes.andon.enums.AndonLevel;
import org.tuoyu.mes.andon.enums.AndonStatus;
import org.tuoyu.mes.andon.enums.AndonType;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.andon.mapper.MesDowntimeReasonMapper;
import org.tuoyu.mes.andon.service.IMesAndonEventService;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.StateGuard;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.resource.api.RemoteMessageService;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 安灯事件
 * <p>
 * 状态流转：已上报 →（派单）已派单 →（开始处理）处理中 →（解决）已解决 →（关闭）已关闭；已派单可以改派，已上报的误报可以直接关闭。
 * 每次变化都用带原状态条件的更新，并发操作时只有一个成功。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesAndonEventServiceImpl implements IMesAndonEventService {

    /**
     * 派单权限：有它的人（班组长、主管）可以代替处理人开始处理、解决，或代替上报人关闭
     */
    static final String ASSIGN_PERMISSION = "mes:andon:assign";

    static final String PUSH_FALLBACK = "resource.message.andon";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MesAndonEventMapper eventMapper;
    private final MesDowntimeReasonMapper reasonMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesLineMapper lineMapper;
    private final MesStationMapper stationMapper;
    private final MesDeviceMapper deviceMapper;
    private final MesWorkorderMapper workorderMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final RemoteMessageService remoteMessageService;

    private Clock clock = Clock.systemDefaultZone();

    private Predicate<String> permissionChecker = StpUtil::hasPermission;

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * 测试中替换权限判断
     */
    void setPermissionChecker(Predicate<String> permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Override
    public MesAndonEventVo queryById(Long eventId) {
        MesAndonEventVo vo = BeanMapper.map(requireEvent(eventId), MesAndonEventVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesAndonEventVo> queryPageList(MesAndonEventBo bo, PageParam pageParam) {
        PageResult<MesAndonEventVo> result = PageResults.of(eventMapper.selectPage(pageParam.toPage(MesAndonEvent.class),
            buildQueryWrapper(bo, !pageParam.hasSort())), MesAndonEventVo.class);
        fill(result.getItems());
        return result;
    }

    @Override
    public List<MesAndonEventVo> queryList(MesAndonEventBo bo) {
        List<MesAndonEventVo> list = BeanMapper.mapList(eventMapper.selectList(buildQueryWrapper(bo, true)),
            MesAndonEventVo.class);
        fill(list);
        return list;
    }

    @Override
    public long countList(MesAndonEventBo bo) {
        return eventMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    private LambdaQueryWrapper<MesAndonEvent> buildQueryWrapper(MesAndonEventBo bo, boolean defaultOrder) {
        Map<String, Object> params = bo.getParams();
        LocalDateTime begin = params == null ? null : parseTime(params.get("beginTime"), false);
        LocalDateTime end = params == null ? null : parseTime(params.get("endTime"), true);
        Long handler = Boolean.TRUE.equals(bo.getMine()) ? UserContext.userId() : bo.getHandlerUserId();
        return QueryBuilder.lambda(MesAndonEvent.class)
            .likeIfText(MesAndonEvent::getEventCode, bo.getEventCode())
            .eqIfText(MesAndonEvent::getEventType, bo.getEventType())
            .eqIfText(MesAndonEvent::getEventLevel, bo.getEventLevel())
            .eqIfText(MesAndonEvent::getEventStatus, bo.getEventStatus())
            .eqIfPresent(MesAndonEvent::getWorkshopId, bo.getWorkshopId())
            .eqIfPresent(MesAndonEvent::getHandlerUserId, handler)
            .geIfPresent(MesAndonEvent::getReportTime, begin)
            .leIfPresent(MesAndonEvent::getReportTime, end)
            .orderByDesc(defaultOrder, MesAndonEvent::getReportTime)
            .orderByDesc(defaultOrder, MesAndonEvent::getEventId)
            .build();
    }

    /**
     * 解析时间范围参数：接受“yyyy-MM-dd HH:mm:ss”或“yyyy-MM-dd”（只有日期时，结束时间取当天最后一秒）
     */
    static LocalDateTime parseTime(Object value, boolean endOfDay) {
        if (value == null || StringUtils.isBlank(value.toString())) {
            return null;
        }
        String text = value.toString().trim();
        try {
            if (text.length() <= 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(text, DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new ServiceException("时间格式不正确：" + text + "，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /**
     * 批量填充关联名称与持续时长
     */
    private void fill(List<MesAndonEventVo> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, MesWorkshop> workshops = byIds(workshopMapper,
            MesLookups.ids(list, MesAndonEventVo::getWorkshopId), MesWorkshop::getWorkshopId);
        Map<Long, MesLine> lines = byIds(lineMapper,
            MesLookups.ids(list, MesAndonEventVo::getLineId), MesLine::getLineId);
        Map<Long, MesStation> stations = byIds(stationMapper,
            MesLookups.ids(list, MesAndonEventVo::getStationId), MesStation::getStationId);
        Map<Long, MesDevice> devices = byIds(deviceMapper,
            MesLookups.ids(list, MesAndonEventVo::getDeviceId), MesDevice::getDeviceId);
        Map<Long, MesDowntimeReason> reasons = byIds(reasonMapper,
            MesLookups.ids(list, MesAndonEventVo::getDowntimeReasonId), MesDowntimeReason::getReasonId);
        Map<Long, MesWorkorder> workorders = byIds(workorderMapper,
            MesLookups.ids(list, MesAndonEventVo::getWorkorderId), MesWorkorder::getWorkorderId);
        LocalDateTime now = LocalDateTime.now(clock);
        for (MesAndonEventVo vo : list) {
            MesWorkshop workshop = workshops.get(vo.getWorkshopId());
            vo.setWorkshopName(workshop == null ? null : workshop.getWorkshopName());
            MesLine line = lines.get(vo.getLineId());
            vo.setLineName(line == null ? null : line.getLineName());
            MesStation station = stations.get(vo.getStationId());
            vo.setStationName(station == null ? null : station.getStationName());
            MesDevice device = devices.get(vo.getDeviceId());
            vo.setDeviceName(device == null ? null : device.getDeviceName());
            MesDowntimeReason reason = reasons.get(vo.getDowntimeReasonId());
            vo.setDowntimeReasonName(reason == null ? null : reason.getReasonName());
            MesWorkorder workorder = workorders.get(vo.getWorkorderId());
            vo.setWorkorderCode(workorder == null ? null : workorder.getWorkorderCode());
            vo.setDurationMinutes(durationMinutes(vo.getReportTime(), vo.getResolveTime(), now));
        }
    }

    /**
     * 已持续分钟数：已解决的按解决时间计算，未解决的按当前时间计算
     */
    static Long durationMinutes(LocalDateTime reportTime, LocalDateTime resolveTime, LocalDateTime now) {
        if (reportTime == null) {
            return null;
        }
        LocalDateTime until = resolveTime == null ? now : resolveTime;
        return Math.max(0, Duration.between(reportTime, until).toMinutes());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void report(MesAndonEventBo bo) {
        CodeEnum.of(AndonType.class, bo.getEventType(), "异常类型");
        CodeEnum.of(AndonLevel.class, bo.getEventLevel(), "紧急程度");
        checkLocation(bo);
        if (bo.getWorkorderId() != null && workorderMapper.selectById(bo.getWorkorderId()) == null) {
            throw new ServiceException("关联的工单不存在");
        }
        if (bo.getDowntimeReasonId() != null) {
            requireEnabledReason(bo.getDowntimeReasonId());
        }
        MesAndonEvent event = BeanMapper.map(bo, MesAndonEvent.class);
        event.setEventId(null);
        event.setEventCode(codeService.next(CodeRules.ANDON));
        event.setEventStatus(AndonStatus.REPORTED.code());
        event.setReportUserId(UserContext.userId());
        event.setReportTime(LocalDateTime.now(clock));
        event.setHandlerUserId(null);
        event.setDowntimeMinutes(null);
        eventMapper.insert(event);
        statusLogService.record(BizType.ANDON_EVENT, event.getEventId(), event.getEventCode(), "create", null,
            AndonStatus.REPORTED.code(), null);
    }

    /**
     * 产线、工位、设备必须属于所选车间，工位与设备还必须属于所选产线
     */
    private void checkLocation(MesAndonEventBo bo) {
        Long workshopId = bo.getWorkshopId();
        if (workshopId == null || workshopMapper.selectById(workshopId) == null) {
            throw new ServiceException("车间不存在");
        }
        if (bo.getLineId() != null) {
            MesLine line = lineMapper.selectById(bo.getLineId());
            if (line == null) {
                throw new ServiceException("产线不存在");
            }
            if (!Objects.equals(line.getWorkshopId(), workshopId)) {
                throw new ServiceException("产线“" + line.getLineName() + "”不属于所选车间");
            }
        }
        if (bo.getStationId() != null) {
            MesStation station = stationMapper.selectById(bo.getStationId());
            if (station == null) {
                throw new ServiceException("工位不存在");
            }
            if (!Objects.equals(station.getWorkshopId(), workshopId)) {
                throw new ServiceException("工位“" + station.getStationName() + "”不属于所选车间");
            }
            if (bo.getLineId() != null && !Objects.equals(station.getLineId(), bo.getLineId())) {
                throw new ServiceException("工位“" + station.getStationName() + "”不属于所选产线");
            }
        }
        if (bo.getDeviceId() != null) {
            MesDevice device = deviceMapper.selectById(bo.getDeviceId());
            if (device == null) {
                throw new ServiceException("设备不存在");
            }
            if (!Objects.equals(device.getWorkshopId(), workshopId)) {
                throw new ServiceException("设备“" + device.getDeviceName() + "”不属于所选车间");
            }
            if (bo.getLineId() != null && device.getLineId() != null && !Objects.equals(device.getLineId(), bo.getLineId())) {
                throw new ServiceException("设备“" + device.getDeviceName() + "”不属于所选产线");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long eventId, Long handlerUserId) {
        if (handlerUserId == null) {
            throw new ServiceException("请选择处理人");
        }
        MesAndonEvent event = requireEvent(eventId);
        AndonStatus from = StateGuard.require("安灯事件", AndonStatus.class, event.getEventStatus(), "派单",
            AndonStatus.REPORTED, AndonStatus.ASSIGNED);
        changeStatus(event, from, AndonStatus.ASSIGNED, Wrappers.<MesAndonEvent>lambdaUpdate()
            .set(MesAndonEvent::getHandlerUserId, handlerUserId)
            .set(MesAndonEvent::getAssignTime, LocalDateTime.now(clock)));
        String reason = from == AndonStatus.ASSIGNED && !Objects.equals(event.getHandlerUserId(), handlerUserId)
            ? "改派" : null;
        statusLogService.record(BizType.ANDON_EVENT, eventId, event.getEventCode(), "assign", from.code(),
            AndonStatus.ASSIGNED.code(), reason);
        String message = "安灯事件 " + event.getEventCode() + " 已派给你：" + event.getTitle();
        afterCommit(() -> RemoteFallback.run(PUSH_FALLBACK,
            () -> remoteMessageService.publishMessage(List.of(handlerUserId), message)));
    }

    /**
     * 在事务提交后执行（推送消息不应早于数据生效）；没有事务时立即执行
     */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(Long eventId) {
        MesAndonEvent event = requireEvent(eventId);
        StateGuard.require("安灯事件", AndonStatus.class, event.getEventStatus(), "开始处理", AndonStatus.ASSIGNED);
        if (!isUser(event.getHandlerUserId()) && !canAssign()) {
            throw new ServiceException("只有处理人本人或有派单权限的人可以开始处理该安灯事件");
        }
        changeStatus(event, AndonStatus.ASSIGNED, AndonStatus.PROCESSING, Wrappers.<MesAndonEvent>lambdaUpdate()
            .set(MesAndonEvent::getResponseTime, LocalDateTime.now(clock)));
        statusLogService.record(BizType.ANDON_EVENT, eventId, event.getEventCode(), "process",
            AndonStatus.ASSIGNED.code(), AndonStatus.PROCESSING.code(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long eventId, String resolveRemark, Long downtimeReasonId) {
        if (StringUtils.isBlank(resolveRemark)) {
            throw new ServiceException("请填写处理说明");
        }
        MesAndonEvent event = requireEvent(eventId);
        StateGuard.require("安灯事件", AndonStatus.class, event.getEventStatus(), "解决", AndonStatus.PROCESSING);
        if (!isUser(event.getHandlerUserId()) && !canAssign()) {
            throw new ServiceException("只有处理人本人或有派单权限的人可以解决该安灯事件");
        }
        if (downtimeReasonId != null) {
            requireEnabledReason(downtimeReasonId);
        }
        Long reasonId = downtimeReasonId != null ? downtimeReasonId : event.getDowntimeReasonId();
        LocalDateTime now = LocalDateTime.now(clock);
        Integer downtime = downtimeMinutes(event, reasonId, now);
        changeStatus(event, AndonStatus.PROCESSING, AndonStatus.RESOLVED, Wrappers.<MesAndonEvent>lambdaUpdate()
            .set(MesAndonEvent::getResolveTime, now)
            .set(MesAndonEvent::getResolveRemark, resolveRemark)
            .set(MesAndonEvent::getDowntimeReasonId, reasonId)
            .set(MesAndonEvent::getDowntimeMinutes, downtime));
        statusLogService.record(BizType.ANDON_EVENT, eventId, event.getEventCode(), "resolve",
            AndonStatus.PROCESSING.code(), AndonStatus.RESOLVED.code(), resolveRemark);
    }

    /**
     * 停机分钟数：停线级别或有停机原因时才记录（上报到解决），否则为空
     */
    static Integer downtimeMinutes(MesAndonEvent event, Long reasonId, LocalDateTime resolveTime) {
        boolean stopped = AndonLevel.CRITICAL.code().equals(event.getEventLevel()) || reasonId != null;
        if (!stopped || event.getReportTime() == null) {
            return null;
        }
        long minutes = Math.max(0, Duration.between(event.getReportTime(), resolveTime).toMinutes());
        return (int) Math.min(Integer.MAX_VALUE, minutes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long eventId, String reason) {
        MesAndonEvent event = requireEvent(eventId);
        AndonStatus from = StateGuard.require("安灯事件", AndonStatus.class, event.getEventStatus(), "关闭",
            AndonStatus.RESOLVED, AndonStatus.REPORTED);
        if (from == AndonStatus.REPORTED && StringUtils.isBlank(reason)) {
            throw new ServiceException("未处理的安灯事件直接关闭时请填写原因（例如误报）");
        }
        if (from == AndonStatus.RESOLVED && !isUser(event.getReportUserId()) && !canAssign()) {
            throw new ServiceException("已解决的安灯事件由上报人确认关闭");
        }
        changeStatus(event, from, AndonStatus.CLOSED, Wrappers.<MesAndonEvent>lambdaUpdate()
            .set(MesAndonEvent::getCloseTime, LocalDateTime.now(clock)));
        statusLogService.record(BizType.ANDON_EVENT, eventId, event.getEventCode(), "close", from.code(),
            AndonStatus.CLOSED.code(), reason);
    }

    @Override
    public List<MesStatusLogVo> logs(Long eventId) {
        requireEvent(eventId);
        return statusLogService.list(BizType.ANDON_EVENT, eventId);
    }

    /**
     * 带原状态条件更新状态，影响 0 行说明状态已被其他操作改变
     */
    private void changeStatus(MesAndonEvent event, AndonStatus from, AndonStatus to,
                              LambdaUpdateWrapper<MesAndonEvent> update) {
        int rows = eventMapper.update(null, update
            .set(MesAndonEvent::getEventStatus, to.code())
            .eq(MesAndonEvent::getEventId, event.getEventId())
            .eq(MesAndonEvent::getEventStatus, from.code()));
        if (rows == 0) {
            throw new ServiceException("安灯事件的状态已变化，请刷新后重试");
        }
    }

    private boolean isUser(Long userId) {
        return userId != null && Objects.equals(userId, UserContext.userId());
    }

    private boolean canAssign() {
        return UserContext.isSuperAdmin() || permissionChecker.test(ASSIGN_PERMISSION);
    }

    private void requireEnabledReason(Long reasonId) {
        MesDowntimeReason reason = reasonMapper.selectById(reasonId);
        if (reason == null) {
            throw new ServiceException("停机原因不存在");
        }
        if (!SystemConstants.NORMAL.equals(reason.getStatus())) {
            throw new ServiceException("停机原因“" + reason.getReasonName() + "”已停用");
        }
    }

    private MesAndonEvent requireEvent(Long eventId) {
        MesAndonEvent event = eventId == null ? null : eventMapper.selectById(eventId);
        if (event == null) {
            throw new ServiceException("安灯事件不存在");
        }
        return event;
    }

    /**
     * 按 ID 批量取关联对象；返回可变的 Map，关联 ID 为空时 get(null) 返回空而不是抛出异常
     */
    private static <T> Map<Long, T> byIds(BaseMapper<T> mapper, Collection<Long> ids, Function<T, Long> idOf) {
        return new HashMap<>(MesLookups.byIds(mapper, ids, idOf));
    }

}
