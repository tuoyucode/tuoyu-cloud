package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.equipment.domain.MesOeeConfig;
import org.tuoyu.mes.equipment.domain.bo.MesOeeConfigBo;
import org.tuoyu.mes.equipment.domain.bo.MesOeeQueryBo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeConfigVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeExportVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeStatVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeVo;
import org.tuoyu.mes.equipment.enums.OeeMetricStatus;
import org.tuoyu.mes.equipment.enums.OeeTargetType;
import org.tuoyu.mes.equipment.mapper.MesOeeConfigMapper;
import org.tuoyu.mes.equipment.service.IMesOeeService;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.enums.ReportStatus;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 设备综合效率（OEE）
 * <p>
 * 数据来源与口径：
 * <ul>
 *     <li>计划生产时间：OEE 配置中的每日计划生产分钟数（设备没有配置时取所属产线的配置）；</li>
 *     <li>停机时间：安灯事件中记录的停机分钟数（停线级别或填了停机原因的事件，从上报到解决），
 *     按设备统计时取该设备的事件，按产线统计时取该产线以及产线上各设备的事件，重叠的时段只算一次；</li>
 *     <li>产出与合格数：报工记录（不含已撤销的），按报工时间归入日期；按设备统计时取设备所在工位的报工，
 *     设备没有工位时取所在产线的报工；</li>
 *     <li>标准节拍：OEE 配置中的标准节拍（设备没有配置时取产线的），没有配置时取每条报工对应工序任务的单件标准工时。</li>
 * </ul>
 * 报工与安灯事件都按单据的创建部门与创建者限定在当前用户的数据权限内。日期分组在 Java 中完成，
 * 查询只用两种数据库通用的条件，MySQL 与 PostgreSQL 结果一致。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesOeeServiceImpl implements IMesOeeService {

    /**
     * 日期范围最长天数（含首尾）
     */
    static final int MAX_DAYS = 366;

    /**
     * 一次计算最多读取的报工记录数：超过时请缩小日期范围
     */
    static final long MAX_REPORTS = 200_000;

    private final MesOeeConfigMapper configMapper;
    private final MesDeviceMapper deviceMapper;
    private final MesLineMapper lineMapper;
    private final MesStationMapper stationMapper;
    private final MesProductionReportMapper reportMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesAndonEventMapper andonMapper;

    @Override
    public MesOeeVo calculate(MesOeeQueryBo bo) {
        OeeTargetType type = CodeEnum.of(OeeTargetType.class, bo.getTargetType(), "统计对象");
        LocalDate begin = bo.getBeginDate();
        LocalDate end = bo.getEndDate();
        if (begin == null || end == null) {
            throw new ServiceException("请选择日期范围");
        }
        if (end.isBefore(begin)) {
            throw new ServiceException("结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(begin, end) + 1 > MAX_DAYS) {
            throw new ServiceException("日期范围最长 " + MAX_DAYS + " 天");
        }
        MesOeeVo vo = new MesOeeVo();
        vo.setTargetType(type.code());
        vo.setTargetId(bo.getTargetId());
        vo.setBeginDate(begin);
        vo.setEndDate(end);
        Source source = type == OeeTargetType.DEVICE ? deviceSource(bo.getTargetId(), vo) : lineSource(bo.getTargetId(), vo);

        LocalDateTime from = begin.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        List<OeeCalculator.Output> outputs = source.reports() == null ? List.of()
            : outputs(source.reports(), from, to, vo.getCycleMinutes());
        List<OeeCalculator.Downtime> downtimes = downtimes(source.downtime(), from, to);
        OeeCalculator.Result result = OeeCalculator.calculate(begin, end, vo.getPlannedMinutesPerDay(),
            source.reports() != null, outputs, downtimes);
        vo.setSummary(result.summary());
        vo.setDays(result.days());
        return vo;
    }

    /**
     * 产出与停机的筛选条件
     *
     * @param reports  报工的条件，没有产出来源时为 null
     * @param downtime 安灯事件的条件
     */
    private record Source(Consumer<LambdaQueryWrapper<MesProductionReport>> reports,
                          Consumer<LambdaQueryWrapper<MesAndonEvent>> downtime) {
    }

    private Source deviceSource(Long deviceId, MesOeeVo vo) {
        MesDevice device = deviceId == null ? null : deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new ServiceException("设备不存在");
        }
        vo.setTargetName(device.getDeviceCode() + " " + device.getDeviceName());
        MesOeeConfig own = config(OeeTargetType.DEVICE, deviceId);
        MesOeeConfig line = device.getLineId() == null ? null : config(OeeTargetType.LINE, device.getLineId());
        if (own != null && own.getPlannedMinutes() != null) {
            vo.setPlannedMinutesPerDay(own.getPlannedMinutes());
            vo.setPlannedSource(OeeTargetType.DEVICE.code());
        } else if (line != null && line.getPlannedMinutes() != null) {
            vo.setPlannedMinutesPerDay(line.getPlannedMinutes());
            vo.setPlannedSource(OeeTargetType.LINE.code());
        }
        vo.setCycleMinutes(own != null && own.getCycleMinutes() != null ? own.getCycleMinutes()
            : line == null ? null : line.getCycleMinutes());

        Consumer<LambdaQueryWrapper<MesProductionReport>> reports = null;
        if (device.getStationId() != null) {
            MesStation station = stationMapper.selectById(device.getStationId());
            vo.setOutputSource("station");
            vo.setOutputSourceName(station == null ? null : station.getStationName());
            reports = w -> w.eq(MesProductionReport::getStationId, device.getStationId());
        } else if (device.getLineId() != null) {
            MesLine lineEntity = lineMapper.selectById(device.getLineId());
            vo.setOutputSource(OeeTargetType.LINE.code());
            vo.setOutputSourceName(lineEntity == null ? null : lineEntity.getLineName());
            reports = w -> w.eq(MesProductionReport::getLineId, device.getLineId());
        }
        return new Source(reports, w -> w.eq(MesAndonEvent::getDeviceId, deviceId));
    }

    private Source lineSource(Long lineId, MesOeeVo vo) {
        MesLine line = lineId == null ? null : lineMapper.selectById(lineId);
        if (line == null) {
            throw new ServiceException("产线不存在");
        }
        vo.setTargetName(line.getLineCode() + " " + line.getLineName());
        MesOeeConfig config = config(OeeTargetType.LINE, lineId);
        if (config != null) {
            vo.setPlannedMinutesPerDay(config.getPlannedMinutes());
            vo.setPlannedSource(config.getPlannedMinutes() == null ? null : OeeTargetType.LINE.code());
            vo.setCycleMinutes(config.getCycleMinutes());
        }
        vo.setOutputSource(OeeTargetType.LINE.code());
        vo.setOutputSourceName(line.getLineName());
        List<Long> devices = deviceMapper.selectList(QueryBuilder.lambda(MesDevice.class)
                .select(MesDevice::getDeviceId)
                .eq(MesDevice::getLineId, lineId)
                .build())
            .stream().map(MesDevice::getDeviceId).toList();
        return new Source(w -> w.eq(MesProductionReport::getLineId, lineId),
            w -> w.and(target -> target.eq(MesAndonEvent::getLineId, lineId)
                .or(!devices.isEmpty(), byDevice -> byDevice.in(MesAndonEvent::getDeviceId, devices))));
    }

    /**
     * 范围内的报工（数据权限内，不含已撤销的），标准节拍取配置或工序任务的单件标准工时
     */
    private List<OeeCalculator.Output> outputs(Consumer<LambdaQueryWrapper<MesProductionReport>> filter,
                                               LocalDateTime from, LocalDateTime to, BigDecimal configuredCycle) {
        LambdaQueryWrapper<MesProductionReport> count = reportScope(filter, from, to);
        long total = reportMapper.selectPageInScope(PageParam.countOnly(), count).getTotal();
        if (total > MAX_REPORTS) {
            throw new ServiceException("日期范围内的报工记录超过 " + MAX_REPORTS + " 条，请缩小日期范围");
        }
        if (total == 0) {
            return List.of();
        }
        LambdaQueryWrapper<MesProductionReport> query = reportScope(filter, from, to);
        query.select(MesProductionReport::getReportTime, MesProductionReport::getTaskId,
            MesProductionReport::getReportQty, MesProductionReport::getQualifiedQty);
        List<MesProductionReport> reports = reportMapper.selectListInScope(query);
        Map<Long, BigDecimal> standards = configuredCycle != null ? Map.of()
            : standardMinutes(MesLookups.ids(reports, MesProductionReport::getTaskId));
        List<OeeCalculator.Output> outputs = new ArrayList<>(reports.size());
        for (MesProductionReport report : reports) {
            BigDecimal cycle = configuredCycle != null ? configuredCycle : standards.get(report.getTaskId());
            outputs.add(new OeeCalculator.Output(report.getReportTime(), report.getReportQty(), report.getQualifiedQty(),
                cycle));
        }
        return outputs;
    }

    private static LambdaQueryWrapper<MesProductionReport> reportScope(
        Consumer<LambdaQueryWrapper<MesProductionReport>> filter, LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<MesProductionReport> wrapper = QueryBuilder.lambda(MesProductionReport.class)
            .ne(MesProductionReport::getReportStatus, ReportStatus.CANCELLED.code())
            .ge(MesProductionReport::getReportTime, from)
            .lt(MesProductionReport::getReportTime, to)
            .build();
        filter.accept(wrapper);
        return wrapper;
    }

    /**
     * 工序任务的单件标准工时（分钟），分批查询
     */
    private Map<Long, BigDecimal> standardMinutes(Collection<Long> taskIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (List<Long> chunk : MesLookups.chunks(taskIds)) {
            for (MesOperationTask task : taskMapper.selectList(QueryBuilder.lambda(MesOperationTask.class)
                .select(MesOperationTask::getTaskId, MesOperationTask::getStandardMinutes)
                .in(MesOperationTask::getTaskId, chunk)
                .build())) {
                result.put(task.getTaskId(), task.getStandardMinutes());
            }
        }
        return result;
    }

    /**
     * 与范围相交的停机（数据权限内）：记录了停机分钟数、在范围结束前上报、在范围开始后解决
     */
    private List<OeeCalculator.Downtime> downtimes(Consumer<LambdaQueryWrapper<MesAndonEvent>> filter,
                                                  LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<MesAndonEvent> wrapper = QueryBuilder.lambda(MesAndonEvent.class)
            .select(MesAndonEvent::getEventId, MesAndonEvent::getReportTime, MesAndonEvent::getDowntimeMinutes)
            .gt(MesAndonEvent::getDowntimeMinutes, 0)
            .lt(MesAndonEvent::getReportTime, to)
            .ge(MesAndonEvent::getResolveTime, from)
            .build();
        filter.accept(wrapper);
        List<OeeCalculator.Downtime> downtimes = new ArrayList<>();
        for (MesAndonEvent event : andonMapper.selectListInScope(wrapper)) {
            if (event.getReportTime() != null && event.getDowntimeMinutes() != null) {
                downtimes.add(new OeeCalculator.Downtime(event.getReportTime(),
                    event.getReportTime().plusMinutes(event.getDowntimeMinutes())));
            }
        }
        return downtimes;
    }

    @Override
    public List<MesOeeExportVo> exportRows(MesOeeQueryBo bo) {
        MesOeeVo oee = calculate(bo);
        List<MesOeeExportVo> rows = new ArrayList<>(oee.getDays().size() + 1);
        rows.add(exportRow("汇总（" + oee.getSummary().getActiveDays() + " 个生产日）", oee.getSummary()));
        for (MesOeeStatVo day : oee.getDays()) {
            rows.add(exportRow(day.getStatDate().toString(), day));
        }
        return rows;
    }

    private static MesOeeExportVo exportRow(String label, MesOeeStatVo stat) {
        MesOeeExportVo row = new MesOeeExportVo();
        row.setStatDate(label);
        row.setPlannedMinutes(stat.getPlannedMinutes());
        row.setDowntimeMinutes(stat.getDowntimeMinutes());
        row.setRunMinutes(stat.getRunMinutes());
        row.setOutputQty(stat.getOutputQty());
        row.setQualifiedQty(stat.getQualifiedQty());
        row.setIdealMinutes(stat.getIdealMinutes());
        row.setAvailability(metricText(stat.getAvailability(), stat.getAvailabilityStatus()));
        row.setPerformance(metricText(stat.getPerformance(), stat.getPerformanceStatus()));
        row.setQuality(metricText(stat.getQuality(), stat.getQualityStatus()));
        row.setOee(metricText(stat.getOee(), stat.getOeeStatus()));
        return row;
    }

    /**
     * 指标的文字：已计算的为百分数，其他写状态（未配置、无数据、无生产）
     */
    static String metricText(BigDecimal value, String status) {
        if (value != null && OeeMetricStatus.OK.code().equals(status)) {
            return value.stripTrailingZeros().toPlainString() + "%";
        }
        return CodeEnum.find(OeeMetricStatus.class, status).map(OeeMetricStatus::label).orElse("");
    }

    @Override
    public MesOeeConfigVo getConfig(String targetType, Long targetId) {
        OeeTargetType type = CodeEnum.of(OeeTargetType.class, targetType, "配置对象");
        requireTarget(type, targetId);
        MesOeeConfig config = config(type, targetId);
        MesOeeConfigVo vo = config == null ? new MesOeeConfigVo() : BeanMapper.map(config, MesOeeConfigVo.class);
        vo.setTargetType(type.code());
        vo.setTargetId(targetId);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(MesOeeConfigBo bo) {
        OeeTargetType type = CodeEnum.of(OeeTargetType.class, bo.getTargetType(), "配置对象");
        requireTarget(type, bo.getTargetId());
        MesOeeConfig existing = config(type, bo.getTargetId());
        String remark = StringUtils.trimToNull(bo.getRemark());
        if (existing != null) {
            // 两项都可以清空（清空后对应的指标显示“未配置”），逐项写入
            configMapper.update(new MesOeeConfig(), Wrappers.<MesOeeConfig>lambdaUpdate()
                .set(MesOeeConfig::getPlannedMinutes, bo.getPlannedMinutes())
                .set(MesOeeConfig::getCycleMinutes, bo.getCycleMinutes())
                .set(MesOeeConfig::getRemark, remark)
                .eq(MesOeeConfig::getConfigId, existing.getConfigId()));
            return;
        }
        MesOeeConfig config = new MesOeeConfig();
        config.setTargetType(type.code());
        config.setTargetId(bo.getTargetId());
        config.setPlannedMinutes(bo.getPlannedMinutes());
        config.setCycleMinutes(bo.getCycleMinutes());
        config.setRemark(remark);
        try {
            configMapper.insert(config);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("该对象的 OEE 配置刚被其他人保存，请刷新后重试");
        }
    }

    private void requireTarget(OeeTargetType type, Long targetId) {
        boolean exists = targetId != null && (type == OeeTargetType.DEVICE ? deviceMapper.selectById(targetId) != null
            : lineMapper.selectById(targetId) != null);
        if (!exists) {
            throw new ServiceException(type == OeeTargetType.DEVICE ? "设备不存在" : "产线不存在");
        }
    }

    private MesOeeConfig config(OeeTargetType type, Long targetId) {
        return configMapper.selectOne(QueryBuilder.lambda(MesOeeConfig.class)
            .eq(MesOeeConfig::getTargetType, type.code())
            .eq(MesOeeConfig::getTargetId, targetId)
            .build());
    }

}
