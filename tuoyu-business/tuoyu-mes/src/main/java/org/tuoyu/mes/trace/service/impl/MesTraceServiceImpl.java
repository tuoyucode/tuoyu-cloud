package org.tuoyu.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.domain.MesStatusLog;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.mapper.MesStatusLogMapper;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
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
import org.tuoyu.mes.material.enums.MaterialRequestStatus;
import org.tuoyu.mes.material.enums.StockInStatus;
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
import org.tuoyu.mes.trace.domain.MesSn;
import org.tuoyu.mes.trace.domain.vo.MesSnVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceDefectVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceEventVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceInspectionVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceMaterialVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceReportVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceStockInVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceTaskVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceVo;
import org.tuoyu.mes.trace.domain.vo.MesTraceWorkorderVo;
import org.tuoyu.mes.trace.enums.SnStatus;
import org.tuoyu.mes.trace.mapper.MesSnMapper;
import org.tuoyu.mes.trace.service.IMesTraceService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 追溯查询（只读）：识别关键字后，按工单批量读取工序任务、报工、领退料、检验、不良、入库与状态履历，组装成追溯结果。
 * 各类记录都按工单 ID 一次查出，不逐行查询。
 * <p>
 * 追溯权限（{@code mes:trace:query}）只开放工单、工序任务与状态履历；报工、用料、检验、不良、入库各段还要求对应模块的
 * 查询权限（列表或详情任一），没有时不读取该段，段内为空、时间线中没有它的事件，并在 {@link MesTraceVo#getDeniedSections()} 中列出。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesTraceServiceImpl implements IMesTraceService {

    public static final String MATCH_SN = "sn";
    public static final String MATCH_BATCH = "batch";
    public static final String MATCH_MATERIAL_BATCH = "material_batch";
    public static final String MATCH_WORKORDER = "workorder";

    public static final String SECTION_REPORTS = "reports";
    public static final String SECTION_MATERIALS = "materials";
    public static final String SECTION_INSPECTIONS = "inspections";
    public static final String SECTION_DEFECTS = "defects";
    public static final String SECTION_STOCK_INS = "stockIns";

    /**
     * 各段 → 对应模块的权限码前缀（有 {@code :list} 或 {@code :query} 之一即可查看），按页面上的顺序
     */
    static final Map<String, String> SECTION_PERMISSIONS = sectionPermissions();

    /**
     * 一次最多返回的工单数
     */
    static final int MAX_WORKORDERS = 20;

    /**
     * 时间线最多返回的事件数
     */
    static final int MAX_EVENTS = 1000;

    private static final int MAX_KEYWORD = 128;

    private final MesSnMapper snMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesProductionReportMapper reportMapper;
    private final MesMaterialRequestMapper requestMapper;
    private final MesMaterialRequestItemMapper requestItemMapper;
    private final MesInspectionMapper inspectionMapper;
    private final MesDefectRecordMapper defectMapper;
    private final MesStockInMapper stockInMapper;
    private final MesStatusLogMapper statusLogMapper;
    private final MesMaterialMapper materialMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesLineMapper lineMapper;
    private final MesStationMapper stationMapper;
    private final MesTeamMapper teamMapper;
    private final MesWarehouseMapper warehouseMapper;
    private final MesLocationMapper locationMapper;

    private Predicate<String> permissionChecker = permission -> UserContext.current()
        .map(user -> user.getPermissions().contains(permission))
        .orElse(false);

    /**
     * 测试中替换权限判断
     */
    void setPermissionChecker(Predicate<String> permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    private static Map<String, String> sectionPermissions() {
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put(SECTION_REPORTS, "mes:productionReport");
        permissions.put(SECTION_MATERIALS, "mes:materialRequest");
        permissions.put(SECTION_INSPECTIONS, "mes:inspection");
        permissions.put(SECTION_DEFECTS, "mes:defect");
        permissions.put(SECTION_STOCK_INS, "mes:stockIn");
        return Collections.unmodifiableMap(permissions);
    }

    /**
     * 当前用户可以查看的段（超级管理员全部可以）
     */
    private Set<String> allowedSections() {
        Set<String> allowed = new HashSet<>();
        boolean superAdmin = UserContext.isSuperAdmin();
        SECTION_PERMISSIONS.forEach((section, prefix) -> {
            if (superAdmin || permissionChecker.test(prefix + ":list") || permissionChecker.test(prefix + ":query")) {
                allowed.add(section);
            }
        });
        return allowed;
    }

    @Override
    public MesTraceVo trace(String keyword) {
        String key = StringUtils.trim(keyword);
        if (StringUtils.isBlank(key)) {
            throw new ServiceException("请输入序列号、批次号或工单编号");
        }
        if (key.length() > MAX_KEYWORD) {
            throw new ServiceException("关键字不能超过 " + MAX_KEYWORD + " 个字符");
        }
        MesTraceVo result = new MesTraceVo();
        result.setKeyword(key);

        MesSn sn = snMapper.selectList(QueryBuilder.lambda(MesSn.class).eq(MesSn::getSnCode, key).build())
            .stream().findFirst().orElse(null);
        Collection<Long> workorderIds;
        if (sn != null) {
            result.setMatchType(MATCH_SN);
            workorderIds = List.of(sn.getWorkorderId());
        } else {
            workorderIds = workorderIds(workorderMapper.selectList(QueryBuilder.lambda(MesWorkorder.class)
                .select(MesWorkorder::getWorkorderId)
                .eq(MesWorkorder::getBatchCode, key)
                .build()));
            if (!workorderIds.isEmpty()) {
                result.setMatchType(MATCH_BATCH);
            } else {
                workorderIds = workordersUsingMaterialBatch(key);
                if (!workorderIds.isEmpty()) {
                    result.setMatchType(MATCH_MATERIAL_BATCH);
                } else {
                    workorderIds = workorderIds(workorderMapper.selectList(QueryBuilder.lambda(MesWorkorder.class)
                        .select(MesWorkorder::getWorkorderId)
                        .eq(MesWorkorder::getWorkorderCode, key)
                        .build()));
                    if (!workorderIds.isEmpty()) {
                        result.setMatchType(MATCH_WORKORDER);
                    }
                }
            }
        }
        if (workorderIds.isEmpty()) {
            return result;
        }
        // 只返回最近的若干个工单（ID 递增，越大越新）
        List<Long> ids = workorderIds.stream().filter(Objects::nonNull).distinct()
            .sorted(Comparator.reverseOrder()).toList();
        if (ids.size() > MAX_WORKORDERS) {
            result.setTruncated(true);
            ids = ids.subList(0, MAX_WORKORDERS);
        }
        // 只追溯当前用户数据权限内的工单：上面按序列号、批次匹配时只取 ID，展示的工单以这里的结果为准
        List<MesWorkorder> workorders = workorderMapper.selectByIdsInScope(ids).stream()
            .sorted(Comparator.comparing(MesWorkorder::getWorkorderId, Comparator.reverseOrder()))
            .toList();
        if (workorders.isEmpty()) {
            result.setMatchType(null);
            return result;
        }
        Set<String> allowed = allowedSections();
        SECTION_PERMISSIONS.keySet().stream().filter(section -> !allowed.contains(section))
            .forEach(result.getDeniedSections()::add);
        assemble(result, workorders, sn, allowed);
        return result;
    }

    /**
     * 使用了该物料批次的工单：已完成的领退料单中有该批次的明细
     */
    private Set<Long> workordersUsingMaterialBatch(String batchCode) {
        Set<Long> requestIds = requestItemMapper.selectList(QueryBuilder.lambda(MesMaterialRequestItem.class)
                .select(MesMaterialRequestItem::getRequestId)
                .eq(MesMaterialRequestItem::getBatchCode, batchCode)
                .build())
            .stream().map(MesMaterialRequestItem::getRequestId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (requestIds.isEmpty()) {
            return Set.of();
        }
        return requestMapper.selectList(QueryBuilder.lambda(MesMaterialRequest.class)
                .select(MesMaterialRequest::getWorkorderId)
                .in(MesMaterialRequest::getRequestId, requestIds)
                .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.COMPLETED.code())
                .build())
            .stream().map(MesMaterialRequest::getWorkorderId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static List<Long> workorderIds(List<MesWorkorder> workorders) {
        return workorders.stream().map(MesWorkorder::getWorkorderId).filter(Objects::nonNull).toList();
    }

    private void assemble(MesTraceVo result, List<MesWorkorder> workorders, MesSn matchedSn, Set<String> allowed) {
        List<Long> ids = workorderIds(workorders);
        Map<Long, MesWorkorder> workorderById = workorders.stream()
            .collect(Collectors.toMap(MesWorkorder::getWorkorderId, Function.identity()));

        List<MesOperationTask> tasks = taskMapper.selectList(QueryBuilder.lambda(MesOperationTask.class)
            .in(MesOperationTask::getWorkorderId, ids)
            .orderByAsc(MesOperationTask::getWorkorderId, MesOperationTask::getSeqNo)
            .build());
        // 没有权限的段不读取
        List<MesProductionReport> reports = !allowed.contains(SECTION_REPORTS) ? List.of()
            : reportMapper.selectList(QueryBuilder.lambda(MesProductionReport.class)
                .in(MesProductionReport::getWorkorderId, ids)
                .orderByAsc(MesProductionReport::getReportTime, MesProductionReport::getReportId)
                .build());
        List<MesMaterialRequest> requests = !allowed.contains(SECTION_MATERIALS) ? List.of()
            : requestMapper.selectList(QueryBuilder.lambda(MesMaterialRequest.class)
                .in(MesMaterialRequest::getWorkorderId, ids)
                .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.COMPLETED.code())
                .orderByAsc(MesMaterialRequest::getCompleteTime, MesMaterialRequest::getRequestId)
                .build());
        List<MesMaterialRequestItem> items = requests.isEmpty() ? List.of()
            : requestItemMapper.selectList(QueryBuilder.lambda(MesMaterialRequestItem.class)
                .in(MesMaterialRequestItem::getRequestId, MesLookups.ids(requests, MesMaterialRequest::getRequestId))
                .orderByAsc(MesMaterialRequestItem::getItemId)
                .build());
        List<MesInspection> inspections = !allowed.contains(SECTION_INSPECTIONS) ? List.of()
            : inspectionMapper.selectList(QueryBuilder.lambda(MesInspection.class)
                .in(MesInspection::getWorkorderId, ids)
                .orderByAsc(MesInspection::getInspectionId)
                .build());
        List<MesDefectRecord> defects = !allowed.contains(SECTION_DEFECTS) ? List.of()
            : defectMapper.selectList(QueryBuilder.lambda(MesDefectRecord.class)
                .in(MesDefectRecord::getWorkorderId, ids)
                .orderByAsc(MesDefectRecord::getDefectId)
                .build());
        List<MesStockIn> stockIns = !allowed.contains(SECTION_STOCK_INS) ? List.of()
            : stockInMapper.selectList(QueryBuilder.lambda(MesStockIn.class)
                .in(MesStockIn::getWorkorderId, ids)
                .orderByAsc(MesStockIn::getStockInId)
                .build());
        Map<Long, Long> snCounts = snCounts(ids);

        // 关联的主数据名称
        Set<Long> materialIds = new HashSet<>(MesLookups.ids(workorders, MesWorkorder::getMaterialId));
        materialIds.addAll(MesLookups.ids(items, MesMaterialRequestItem::getMaterialId));
        Map<Long, MesMaterial> materials = byIds(materialMapper, materialIds, MesMaterial::getMaterialId);
        Map<Long, MesWorkshop> workshops = byIds(workshopMapper,
            MesLookups.ids(workorders, MesWorkorder::getWorkshopId), MesWorkshop::getWorkshopId);
        Map<Long, MesLine> lines = byIds(lineMapper,
            MesLookups.ids(workorders, MesWorkorder::getLineId), MesLine::getLineId);
        Map<Long, MesStation> stations = byIds(stationMapper,
            MesLookups.ids(reports, MesProductionReport::getStationId), MesStation::getStationId);
        Map<Long, MesTeam> teams = byIds(teamMapper,
            MesLookups.ids(reports, MesProductionReport::getTeamId), MesTeam::getTeamId);
        Map<Long, MesWarehouse> warehouses = byIds(warehouseMapper,
            MesLookups.ids(stockIns, MesStockIn::getWarehouseId), MesWarehouse::getWarehouseId);
        Set<Long> locationIds = new HashSet<>(MesLookups.ids(items, MesMaterialRequestItem::getLocationId));
        locationIds.addAll(MesLookups.ids(stockIns, MesStockIn::getLocationId));
        Map<Long, MesLocation> locations = byIds(locationMapper, locationIds, MesLocation::getLocationId);
        Map<Long, MesOperationTask> taskById = tasks.stream()
            .collect(Collectors.toMap(MesOperationTask::getTaskId, Function.identity(), (a, b) -> a));
        Map<Long, MesMaterialRequest> requestById = requests.stream()
            .collect(Collectors.toMap(MesMaterialRequest::getRequestId, Function.identity(), (a, b) -> a));

        // 工单
        Map<Long, MesTraceWorkorderVo> voById = new LinkedHashMap<>();
        for (MesWorkorder workorder : workorders) {
            MesTraceWorkorderVo vo = BeanMapper.map(workorder, MesTraceWorkorderVo.class);
            MesMaterial product = materials.get(workorder.getMaterialId());
            if (product != null) {
                vo.setMaterialCode(product.getMaterialCode());
                vo.setMaterialName(product.getMaterialName());
                vo.setUnit(product.getUnit());
            }
            vo.setWorkshopName(nameOf(workshops.get(workorder.getWorkshopId()), MesWorkshop::getWorkshopName));
            vo.setLineName(nameOf(lines.get(workorder.getLineId()), MesLine::getLineName));
            vo.setSnCount(snCounts.getOrDefault(workorder.getWorkorderId(), 0L));
            if (matchedSn != null && Objects.equals(matchedSn.getWorkorderId(), workorder.getWorkorderId())) {
                vo.setCurrentSn(toSnVo(matchedSn, workorder, product));
            }
            voById.put(workorder.getWorkorderId(), vo);
        }

        // 工序任务与报工
        Map<Long, MesTraceTaskVo> taskVoById = new LinkedHashMap<>();
        for (MesOperationTask task : tasks) {
            MesTraceWorkorderVo owner = voById.get(task.getWorkorderId());
            if (owner == null) {
                continue;
            }
            MesTraceTaskVo taskVo = BeanMapper.map(task, MesTraceTaskVo.class);
            owner.getTasks().add(taskVo);
            taskVoById.put(task.getTaskId(), taskVo);
        }
        for (MesProductionReport report : reports) {
            MesTraceTaskVo taskVo = taskVoById.get(report.getTaskId());
            if (taskVo == null) {
                continue;
            }
            MesTraceReportVo reportVo = BeanMapper.map(report, MesTraceReportVo.class);
            reportVo.setStationName(nameOf(stations.get(report.getStationId()), MesStation::getStationName));
            reportVo.setTeamName(nameOf(teams.get(report.getTeamId()), MesTeam::getTeamName));
            taskVo.getReports().add(reportVo);
        }

        // 领退料明细
        for (MesMaterialRequestItem item : items) {
            MesMaterialRequest request = requestById.get(item.getRequestId());
            MesTraceWorkorderVo owner = request == null ? null : voById.get(request.getWorkorderId());
            if (owner == null) {
                continue;
            }
            MesTraceMaterialVo vo = new MesTraceMaterialVo();
            vo.setRequestId(request.getRequestId());
            vo.setRequestCode(request.getRequestCode());
            vo.setRequestType(request.getRequestType());
            vo.setMaterialId(item.getMaterialId());
            MesMaterial material = materials.get(item.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
                vo.setUnit(material.getUnit());
            }
            vo.setBatchCode(item.getBatchCode());
            vo.setActualQty(item.getActualQty());
            vo.setLocationName(nameOf(locations.get(item.getLocationId()), MesLocation::getLocationName));
            vo.setCompleteTime(request.getCompleteTime());
            owner.getMaterials().add(vo);
        }

        // 检验、不良、入库
        for (MesInspection inspection : inspections) {
            MesTraceWorkorderVo owner = voById.get(inspection.getWorkorderId());
            if (owner != null) {
                MesTraceInspectionVo vo = BeanMapper.map(inspection, MesTraceInspectionVo.class);
                vo.setOperationName(nameOf(taskById.get(inspection.getTaskId()), MesOperationTask::getOperationName));
                owner.getInspections().add(vo);
            }
        }
        for (MesDefectRecord defect : defects) {
            MesTraceWorkorderVo owner = voById.get(defect.getWorkorderId());
            if (owner != null) {
                MesTraceDefectVo vo = BeanMapper.map(defect, MesTraceDefectVo.class);
                vo.setOperationName(nameOf(taskById.get(defect.getTaskId()), MesOperationTask::getOperationName));
                owner.getDefects().add(vo);
            }
        }
        for (MesStockIn stockIn : stockIns) {
            MesTraceWorkorderVo owner = voById.get(stockIn.getWorkorderId());
            if (owner != null) {
                MesTraceStockInVo vo = BeanMapper.map(stockIn, MesTraceStockInVo.class);
                vo.setWarehouseName(nameOf(warehouses.get(stockIn.getWarehouseId()), MesWarehouse::getWarehouseName));
                vo.setLocationName(nameOf(locations.get(stockIn.getLocationId()), MesLocation::getLocationName));
                owner.getStockIns().add(vo);
            }
        }
        result.getWorkorders().addAll(voById.values());

        // 时间线
        List<MesTraceEventVo> events = new ArrayList<>();
        for (MesStatusLog log : statusLogs(ids, matchedSn)) {
            MesTraceEventVo event = event(log.getOperateTime(), log.getBizType(), log.getAction(), log.getBizCode(), null);
            if (BizType.WORKORDER.code().equals(log.getBizType())) {
                event.setWorkorderCode(log.getBizCode());
            } else if (matchedSn != null) {
                event.setWorkorderCode(codeOf(workorderById.get(matchedSn.getWorkorderId())));
            }
            event.setFromStatus(log.getFromStatus());
            event.setToStatus(log.getToStatus());
            event.setOperatorUserId(log.getOperatorUserId());
            event.setRemark(log.getReason());
            events.add(event);
        }
        for (MesProductionReport report : reports) {
            MesTraceEventVo event = event(report.getReportTime(), BizType.PRODUCTION_REPORT.code(), "report",
                report.getReportCode(), workorderById.get(report.getWorkorderId()));
            event.setToStatus(report.getReportStatus());
            event.setQty(report.getQualifiedQty());
            event.setDefectQty(report.getDefectQty());
            event.setOperatorUserId(report.getReportUserId());
            event.setRemark(nameOf(taskById.get(report.getTaskId()), MesOperationTask::getOperationName));
            events.add(event);
        }
        for (MesMaterialRequest request : requests) {
            MesTraceEventVo event = event(request.getCompleteTime(), BizType.MATERIAL_REQUEST.code(),
                request.getRequestType(), request.getRequestCode(), workorderById.get(request.getWorkorderId()));
            event.setToStatus(request.getRequestStatus());
            event.setOperatorUserId(request.getOperatorUserId());
            events.add(event);
        }
        for (MesInspection inspection : inspections) {
            if (inspection.getInspectionTime() == null) {
                continue;
            }
            MesTraceEventVo event = event(inspection.getInspectionTime(), BizType.INSPECTION.code(), "inspect",
                inspection.getInspectionCode(), workorderById.get(inspection.getWorkorderId()));
            event.setToStatus(inspection.getInspectionResult());
            event.setQty(inspection.getInspectQty());
            event.setDefectQty(inspection.getDefectQty());
            event.setOperatorUserId(inspection.getInspectorUserId());
            event.setRemark(inspection.getInspectionType());
            events.add(event);
        }
        for (MesDefectRecord defect : defects) {
            MesTraceEventVo created = event(defect.getCreateTime(), BizType.DEFECT_RECORD.code(), "create",
                defect.getDefectCode(), workorderById.get(defect.getWorkorderId()));
            created.setQty(defect.getDefectQty());
            created.setRemark(defect.getDescription());
            events.add(created);
            if (defect.getHandleTime() != null) {
                MesTraceEventVo handled = event(defect.getHandleTime(), BizType.DEFECT_RECORD.code(), "handle",
                    defect.getDefectCode(), workorderById.get(defect.getWorkorderId()));
                handled.setToStatus(defect.getHandleMethod());
                handled.setQty(defect.getDefectQty());
                handled.setOperatorUserId(defect.getHandleUserId());
                handled.setRemark(defect.getHandleRemark());
                events.add(handled);
            }
        }
        for (MesStockIn stockIn : stockIns) {
            if (!StockInStatus.COMPLETED.code().equals(stockIn.getStockInStatus())) {
                continue;
            }
            MesTraceEventVo event = event(stockIn.getCompleteTime(), BizType.STOCK_IN.code(), "complete",
                stockIn.getStockInCode(), workorderById.get(stockIn.getWorkorderId()));
            event.setToStatus(stockIn.getStockInStatus());
            event.setQty(stockIn.getQty());
            event.setOperatorUserId(stockIn.getOperatorUserId());
            events.add(event);
        }
        result.getTimeline().addAll(events.stream()
            .filter(e -> e.getTime() != null)
            .sorted(Comparator.comparing(MesTraceEventVo::getTime))
            .limit(MAX_EVENTS)
            .toList());
    }

    /**
     * 各工单未报废的序列号数量：按工单分组统计，结果借用实体的 snId 属性承载数量
     */
    private Map<Long, Long> snCounts(List<Long> workorderIds) {
        return snMapper.selectList(QueryBuilder.lambda(MesSn.class)
                .select(MesSn::getWorkorderId)
                .selectCount(MesSn::getSnId, MesSn::getSnId)
                .in(MesSn::getWorkorderId, workorderIds)
                .ne(MesSn::getSnStatus, SnStatus.SCRAPPED.code())
                .groupBy(MesSn::getWorkorderId)
                .build())
            .stream()
            .filter(row -> row.getWorkorderId() != null && row.getSnId() != null)
            .collect(Collectors.toMap(MesSn::getWorkorderId, MesSn::getSnId, Long::sum));
    }

    /**
     * 工单的状态履历；按序列号查询时另加该序列号的履历
     */
    private List<MesStatusLog> statusLogs(List<Long> workorderIds, MesSn matchedSn) {
        List<MesStatusLog> logs = new ArrayList<>(statusLogMapper.selectList(QueryBuilder.lambda(MesStatusLog.class)
            .eq(MesStatusLog::getBizType, BizType.WORKORDER.code())
            .in(MesStatusLog::getBizId, workorderIds)
            .orderByAsc(MesStatusLog::getOperateTime, MesStatusLog::getLogId)
            .build()));
        if (matchedSn != null) {
            logs.addAll(statusLogMapper.selectList(QueryBuilder.lambda(MesStatusLog.class)
                .eq(MesStatusLog::getBizType, BizType.SN.code())
                .eq(MesStatusLog::getBizId, matchedSn.getSnId())
                .orderByAsc(MesStatusLog::getOperateTime, MesStatusLog::getLogId)
                .build()));
        }
        return logs;
    }

    private static MesTraceEventVo event(LocalDateTime time, String category, String action, String bizCode,
                                         MesWorkorder workorder) {
        MesTraceEventVo event = new MesTraceEventVo();
        event.setTime(time);
        event.setCategory(category);
        event.setAction(action);
        event.setBizCode(bizCode);
        event.setWorkorderCode(codeOf(workorder));
        return event;
    }

    private static MesSnVo toSnVo(MesSn sn, MesWorkorder workorder, MesMaterial product) {
        MesSnVo vo = BeanMapper.map(sn, MesSnVo.class);
        vo.setWorkorderCode(workorder.getWorkorderCode());
        if (product != null) {
            vo.setMaterialCode(product.getMaterialCode());
            vo.setMaterialName(product.getMaterialName());
        }
        return vo;
    }

    private static String codeOf(MesWorkorder workorder) {
        return workorder == null ? null : workorder.getWorkorderCode();
    }

    private static <T> String nameOf(T entity, Function<T, String> name) {
        return entity == null ? null : name.apply(entity);
    }

    /**
     * 按 ID 批量取关联对象；返回可变的 Map，关联 ID 为空时 get(null) 返回空而不是抛出异常
     */
    private static <T> Map<Long, T> byIds(BaseMapper<T> mapper, Collection<Long> ids, Function<T, Long> idOf) {
        return new HashMap<>(MesLookups.byIds(mapper, ids, idOf));
    }

}
