package org.tuoyu.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.StateGuard;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesRoute;
import org.tuoyu.mes.masterdata.domain.MesRouteOperation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.enums.MaterialType;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteOperationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.domain.bo.MesWorkorderBo;
import org.tuoyu.mes.plan.domain.vo.MesWorkorderVo;
import org.tuoyu.mes.plan.enums.TaskStatus;
import org.tuoyu.mes.plan.enums.WorkorderSource;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.plan.service.IMesWorkorderService;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.enums.ReportStatus;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 生产工单
 * <p>
 * 列表、导出、下拉与按 ID 的查看、修改、删除、状态动作受数据权限约束（按创建部门与创建者），不在范围内的按不存在处理；
 * 读取工艺路线、工序任务、报工等单据之间的内部读取不加数据权限。状态动作先锁定工单行，
 * 与同一工单的派工、报工、撤销报工依次执行，状态变化再带原状态条件。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesWorkorderServiceImpl implements IMesWorkorderService {

    private static final String OBJECT = "工单";
    private static final int OPTION_LIMIT = 50;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String YES = "Y";

    private final MesWorkorderMapper workorderMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesProductionReportMapper reportMapper;
    private final MesMaterialMapper materialMapper;
    private final MesBomMapper bomMapper;
    private final MesRouteMapper routeMapper;
    private final MesRouteOperationMapper routeOperationMapper;
    private final MesFactoryMapper factoryMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesLineMapper lineMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final MesOperationTaskAssembler taskAssembler;

    // ---------------------------------------------------------------- 查询

    @Override
    public MesWorkorderVo queryById(Long workorderId) {
        MesWorkorder workorder = requireInScope(workorderId);
        MesWorkorderVo vo = BeanMapper.map(workorder, MesWorkorderVo.class);
        fillNames(List.of(vo));
        // 工单在数据权限内即可看到它的全部工序任务（任务由下达的人创建，创建部门可能不同）
        vo.setTasks(taskAssembler.toVoList(tasksOf(workorderId)));
        return vo;
    }

    @Override
    public PageResult<MesWorkorderVo> queryPageList(MesWorkorderBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesWorkorder> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesWorkorderVo> result = PageResults.of(
            workorderMapper.selectPageInScope(pageParam.toPage(MesWorkorder.class), lqw), MesWorkorderVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesWorkorderVo> queryList(MesWorkorderBo bo) {
        List<MesWorkorderVo> vos = BeanMapper.mapList(workorderMapper.selectListInScope(buildQueryWrapper(bo, true)),
            MesWorkorderVo.class);
        fillNames(vos);
        return vos;
    }

    @Override
    public long countList(MesWorkorderBo bo) {
        return workorderMapper.selectPageInScope(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(String keyword, String statuses) {
        String text = StringUtils.trimToNull(keyword);
        List<MesWorkorder> workorders = workorderMapper.selectListInScope(QueryBuilder.lambda(MesWorkorder.class)
            .select(MesWorkorder::getWorkorderId, MesWorkorder::getWorkorderCode, MesWorkorder::getMaterialId)
            .inIfNotEmpty(MesWorkorder::getWorkorderStatus, splitCodes(statuses))
            .and(text != null, w -> w.like(MesWorkorder::getWorkorderCode, text)
                .or().like(MesWorkorder::getBatchCode, text))
            .orderByDesc(MesWorkorder::getCreateTime, MesWorkorder::getWorkorderId)
            .last("limit " + OPTION_LIMIT)
            .build());
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(workorders, MesWorkorder::getMaterialId), MesMaterial::getMaterialId);
        return workorders.stream().map(w -> {
            MesMaterial material = PlanLookups.get(materials, w.getMaterialId());
            return new MesOptionVo(w.getWorkorderId(), w.getWorkorderCode(),
                material == null ? null : material.getMaterialName(), w.getMaterialId());
        }).toList();
    }

    @Override
    public List<MesStatusLogVo> logs(Long workorderId) {
        requireInScope(workorderId);
        return statusLogService.list(BizType.WORKORDER, workorderId);
    }

    private LambdaQueryWrapper<MesWorkorder> buildQueryWrapper(MesWorkorderBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesWorkorder.class)
            .likeIfText(MesWorkorder::getWorkorderCode, bo.getWorkorderCode())
            .eqIfPresent(MesWorkorder::getMaterialId, bo.getMaterialId())
            .eqIfPresent(MesWorkorder::getWorkshopId, bo.getWorkshopId())
            .eqIfPresent(MesWorkorder::getLineId, bo.getLineId())
            .inIfNotEmpty(MesWorkorder::getWorkorderStatus, splitCodes(bo.getWorkorderStatus()))
            .geIfPresent(MesWorkorder::getPlanStartTime, bo.getBeginPlanStartTime())
            .leIfPresent(MesWorkorder::getPlanStartTime, bo.getEndPlanStartTime())
            .orderByDesc(defaultOrder, MesWorkorder::getCreateTime)
            .orderByDesc(defaultOrder, MesWorkorder::getWorkorderId)
            .build();
    }

    /**
     * 列表中批量填充产品、工厂、车间、产线、物料清单、工艺路线的名称与完成率
     */
    private void fillNames(List<MesWorkorderVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(vos, MesWorkorderVo::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesFactory> factories = MesLookups.byIds(factoryMapper,
            MesLookups.ids(vos, MesWorkorderVo::getFactoryId), MesFactory::getFactoryId);
        Map<Long, MesWorkshop> workshops = MesLookups.byIds(workshopMapper,
            MesLookups.ids(vos, MesWorkorderVo::getWorkshopId), MesWorkshop::getWorkshopId);
        Map<Long, MesLine> lines = MesLookups.byIds(lineMapper,
            MesLookups.ids(vos, MesWorkorderVo::getLineId), MesLine::getLineId);
        Map<Long, MesBom> boms = MesLookups.byIds(bomMapper,
            MesLookups.ids(vos, MesWorkorderVo::getBomId), MesBom::getBomId);
        Map<Long, MesRoute> routes = MesLookups.byIds(routeMapper,
            MesLookups.ids(vos, MesWorkorderVo::getRouteId), MesRoute::getRouteId);
        for (MesWorkorderVo vo : vos) {
            MesMaterial material = PlanLookups.get(materials, vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
                vo.setSpecification(material.getSpecification());
                vo.setUnit(material.getUnit());
            }
            MesFactory factory = PlanLookups.get(factories, vo.getFactoryId());
            vo.setFactoryName(factory == null ? null : factory.getFactoryName());
            MesWorkshop workshop = PlanLookups.get(workshops, vo.getWorkshopId());
            vo.setWorkshopName(workshop == null ? null : workshop.getWorkshopName());
            MesLine line = PlanLookups.get(lines, vo.getLineId());
            vo.setLineName(line == null ? null : line.getLineName());
            MesBom bom = PlanLookups.get(boms, vo.getBomId());
            vo.setBomName(bom == null ? null : bom.getBomCode() + " " + bom.getBomVersion());
            MesRoute route = PlanLookups.get(routes, vo.getRouteId());
            vo.setRouteName(route == null ? null : route.getRouteName() + " " + route.getRouteVersion());
            vo.setCompletionRate(completionRate(vo.getCompletedQty(), vo.getPlanQty()));
        }
    }

    /**
     * 完成率：完工数量 ÷ 计划数量，百分数保留两位小数
     */
    static BigDecimal completionRate(BigDecimal completedQty, BigDecimal planQty) {
        if (planQty == null || planQty.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return TaskSequence.orZero(completedQty).multiply(HUNDRED).divide(planQty, 2, RoundingMode.HALF_UP);
    }

    private static List<String> splitCodes(String codes) {
        if (StringUtils.isBlank(codes)) {
            return List.of();
        }
        return Arrays.stream(codes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    // ---------------------------------------------------------------- 草稿维护

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesWorkorderBo bo) {
        MesWorkorder workorder = BeanMapper.map(bo, MesWorkorder.class);
        validateAndFill(workorder);
        workorder.setWorkorderId(null);
        workorder.setWorkorderCode(codeService.next(CodeRules.WORKORDER));
        workorder.setWorkorderStatus(WorkorderStatus.DRAFT.code());
        workorder.setCompletedQty(BigDecimal.ZERO);
        workorder.setDefectQty(BigDecimal.ZERO);
        workorder.setBatchCode(null);
        workorder.setActualStartTime(null);
        workorder.setActualEndTime(null);
        if (workorder.getPriority() == null) {
            workorder.setPriority(0);
        }
        workorderMapper.insert(workorder);
        bo.setWorkorderId(workorder.getWorkorderId());
        bo.setWorkorderCode(workorder.getWorkorderCode());
        statusLogService.record(BizType.WORKORDER, workorder.getWorkorderId(), workorder.getWorkorderCode(), "create",
            null, WorkorderStatus.DRAFT.code(), null);
    }

    @Override
    public void updateByBo(MesWorkorderBo bo) {
        MesWorkorder current = requireInScope(bo.getWorkorderId());
        StateGuard.require(OBJECT, WorkorderStatus.class, current.getWorkorderStatus(), "修改", WorkorderStatus.DRAFT);
        MesWorkorder workorder = BeanMapper.map(bo, MesWorkorder.class);
        validateAndFill(workorder);
        // 可以清空的字段用条件显式设置，其余字段按实体更新（为空的字段不更新）
        LambdaUpdateWrapper<MesWorkorder> wrapper = Wrappers.lambdaUpdate(MesWorkorder.class)
            .set(MesWorkorder::getBomId, workorder.getBomId())
            .set(MesWorkorder::getLineId, workorder.getLineId())
            .set(MesWorkorder::getSourceCode, workorder.getSourceCode())
            .set(MesWorkorder::getPlanStartTime, workorder.getPlanStartTime())
            .set(MesWorkorder::getPlanEndTime, workorder.getPlanEndTime())
            .set(MesWorkorder::getRemark, workorder.getRemark())
            .eq(MesWorkorder::getWorkorderId, bo.getWorkorderId())
            .eq(MesWorkorder::getWorkorderStatus, WorkorderStatus.DRAFT.code());
        MesWorkorder changes = new MesWorkorder();
        changes.setSourceType(workorder.getSourceType());
        changes.setMaterialId(workorder.getMaterialId());
        changes.setRouteId(workorder.getRouteId());
        changes.setFactoryId(workorder.getFactoryId());
        changes.setWorkshopId(workorder.getWorkshopId());
        changes.setPlanQty(workorder.getPlanQty());
        changes.setPriority(workorder.getPriority() == null ? 0 : workorder.getPriority());
        if (workorderMapper.update(changes, wrapper) == 0) {
            throw new ServiceException("工单状态已变化，请刷新后重试");
        }
    }

    /**
     * 校验产品、工艺路线、物料清单、车间、产线并补齐工厂与默认物料清单
     */
    private void validateAndFill(MesWorkorder workorder) {
        if (workorder.getPlanQty() == null || workorder.getPlanQty().signum() <= 0) {
            throw new ServiceException("计划数量必须大于 0");
        }
        if (StringUtils.isBlank(workorder.getSourceType())) {
            workorder.setSourceType(WorkorderSource.MANUAL.code());
        } else {
            CodeEnum.of(WorkorderSource.class, workorder.getSourceType(), "工单来源");
        }
        if (workorder.getPlanStartTime() != null && workorder.getPlanEndTime() != null
            && workorder.getPlanEndTime().isBefore(workorder.getPlanStartTime())) {
            throw new ServiceException("计划完成时间不能早于计划开始时间");
        }
        MesMaterial material = materialMapper.selectById(workorder.getMaterialId());
        if (material == null) {
            throw new ServiceException("产品不存在");
        }
        if (!CodeEnum.in(material.getMaterialType(), MaterialType.PRODUCT, MaterialType.SEMI)) {
            throw new ServiceException("物料“" + material.getMaterialName() + "”不是成品或半成品，不能创建工单");
        }
        if (!SystemConstants.NORMAL.equals(material.getStatus())) {
            throw new ServiceException("产品“" + material.getMaterialName() + "”已停用");
        }
        MesRoute route = routeMapper.selectById(workorder.getRouteId());
        if (route == null || !Objects.equals(route.getMaterialId(), material.getMaterialId())) {
            throw new ServiceException("工艺路线不存在或不属于该产品");
        }
        if (!SystemConstants.NORMAL.equals(route.getStatus())) {
            throw new ServiceException("工艺路线“" + route.getRouteName() + "”已停用");
        }
        if (workorder.getBomId() == null) {
            MesBom bom = bomMapper.selectOne(QueryBuilder.lambda(MesBom.class)
                .eq(MesBom::getMaterialId, material.getMaterialId())
                .eq(MesBom::getIsDefault, YES)
                .eq(MesBom::getStatus, SystemConstants.NORMAL)
                .orderByDesc(MesBom::getBomId)
                .last("limit 1")
                .build());
            workorder.setBomId(bom == null ? null : bom.getBomId());
        } else {
            MesBom bom = bomMapper.selectById(workorder.getBomId());
            if (bom == null || !Objects.equals(bom.getMaterialId(), material.getMaterialId())) {
                throw new ServiceException("物料清单不存在或不属于该产品");
            }
        }
        MesWorkshop workshop = workshopMapper.selectById(workorder.getWorkshopId());
        if (workshop == null) {
            throw new ServiceException("车间不存在");
        }
        workorder.setFactoryId(workshop.getFactoryId());
        if (workorder.getLineId() != null) {
            MesLine line = lineMapper.selectById(workorder.getLineId());
            if (line == null || !Objects.equals(line.getWorkshopId(), workshop.getWorkshopId())) {
                throw new ServiceException("产线不存在或不属于所选车间");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        List<MesWorkorder> workorders = workorderMapper.selectByIdsInScope(distinct);
        // 任何一个不存在或不在数据权限内都整批拒绝
        if (workorders.isEmpty() || workorders.size() != distinct.size()) {
            throw new ServiceException("删除工单失败，工单不存在");
        }
        for (MesWorkorder workorder : workorders) {
            StateGuard.require(OBJECT + "“" + workorder.getWorkorderCode() + "”", WorkorderStatus.class,
                workorder.getWorkorderStatus(), "删除", WorkorderStatus.DRAFT);
        }
        int deleted = workorderMapper.delete(QueryBuilder.lambda(MesWorkorder.class)
            .in(MesWorkorder::getWorkorderId, distinct)
            .eq(MesWorkorder::getWorkorderStatus, WorkorderStatus.DRAFT.code())
            .build());
        if (deleted != workorders.size()) {
            throw new ServiceException("工单状态已变化，请刷新后重试");
        }
    }

    // ---------------------------------------------------------------- 状态动作

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long workorderId) {
        MesWorkorder workorder = lockInScope(workorderId);
        StateGuard.require(OBJECT, WorkorderStatus.class, workorder.getWorkorderStatus(), "下达", WorkorderStatus.DRAFT);
        MesRoute route = routeMapper.selectById(workorder.getRouteId());
        if (route == null) {
            throw new ServiceException("工单的工艺路线不存在");
        }
        List<MesRouteOperation> operations = routeOperationMapper.selectList(QueryBuilder.lambda(MesRouteOperation.class)
            .eq(MesRouteOperation::getRouteId, route.getRouteId())
            .orderByAsc(MesRouteOperation::getSeqNo, MesRouteOperation::getOperationId)
            .build());
        if (operations.isEmpty()) {
            throw new ServiceException("工艺路线“" + route.getRouteName() + "”没有工序，不能下达");
        }
        String batchCode = codeService.next(CodeRules.BATCH);
        changeStatus(workorder, WorkorderStatus.RELEASED, w -> w.set(MesWorkorder::getBatchCode, batchCode));
        List<String> taskCodes = codeService.next(CodeRules.TASK, operations.size());
        List<MesOperationTask> tasks = new ArrayList<>(operations.size());
        for (int i = 0; i < operations.size(); i++) {
            MesRouteOperation operation = operations.get(i);
            MesOperationTask task = new MesOperationTask();
            task.setTaskCode(taskCodes.get(i));
            task.setWorkorderId(workorder.getWorkorderId());
            task.setOperationId(operation.getOperationId());
            task.setSeqNo(operation.getSeqNo());
            task.setOperationCode(operation.getOperationCode());
            task.setOperationName(operation.getOperationName());
            task.setMaterialId(workorder.getMaterialId());
            task.setWorkshopId(workorder.getWorkshopId());
            task.setLineId(workorder.getLineId());
            task.setPlanQty(workorder.getPlanQty());
            task.setQualifiedQty(BigDecimal.ZERO);
            task.setDefectQty(BigDecimal.ZERO);
            task.setStandardMinutes(TaskSequence.orZero(operation.getStandardMinutes()));
            task.setFirstInspection(YES.equals(operation.getFirstInspection()) ? YES : "N");
            task.setTaskStatus(TaskStatus.PENDING.code());
            tasks.add(task);
        }
        taskMapper.insert(tasks);
        statusLogService.record(BizType.WORKORDER, workorder.getWorkorderId(), workorder.getWorkorderCode(), "release",
            WorkorderStatus.DRAFT.code(), WorkorderStatus.RELEASED.code(), "批次号 " + batchCode);
        for (MesOperationTask task : tasks) {
            statusLogService.record(BizType.OPERATION_TASK, task.getTaskId(), task.getTaskCode(), "create", null,
                TaskStatus.PENDING.code(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pause(Long workorderId, String reason) {
        requireReason(reason, "暂停");
        MesWorkorder workorder = lockInScope(workorderId);
        WorkorderStatus from = StateGuard.require(OBJECT, WorkorderStatus.class, workorder.getWorkorderStatus(), "暂停",
            WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS);
        changeStatus(workorder, WorkorderStatus.PAUSED, null);
        statusLogService.record(BizType.WORKORDER, workorderId, workorder.getWorkorderCode(), "pause", from.code(),
            WorkorderStatus.PAUSED.code(), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long workorderId) {
        MesWorkorder workorder = lockInScope(workorderId);
        StateGuard.require(OBJECT, WorkorderStatus.class, workorder.getWorkorderStatus(), "恢复", WorkorderStatus.PAUSED);
        WorkorderStatus target = statusBeforePause(workorderId);
        changeStatus(workorder, target, null);
        statusLogService.record(BizType.WORKORDER, workorderId, workorder.getWorkorderCode(), "resume",
            WorkorderStatus.PAUSED.code(), target.code(), null);
    }

    /**
     * 暂停前的状态：取最近一次暂停的原状态；取不到时有报工为生产中，否则为已下达
     */
    private WorkorderStatus statusBeforePause(Long workorderId) {
        List<MesStatusLogVo> logs = statusLogService.list(BizType.WORKORDER, workorderId);
        for (int i = logs.size() - 1; i >= 0; i--) {
            MesStatusLogVo log = logs.get(i);
            if ("pause".equals(log.getAction())) {
                if (CodeEnum.in(log.getFromStatus(), WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS)) {
                    return CodeEnum.of(WorkorderStatus.class, log.getFromStatus(), "工单状态");
                }
                break;
            }
        }
        // 内部判断，统计工单的全部报工，不加数据权限
        boolean reported = reportMapper.exists(QueryBuilder.lambda(MesProductionReport.class)
            .eq(MesProductionReport::getWorkorderId, workorderId)
            .ne(MesProductionReport::getReportStatus, ReportStatus.CANCELLED.code())
            .build());
        return reported ? WorkorderStatus.IN_PROGRESS : WorkorderStatus.RELEASED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long workorderId) {
        MesWorkorder workorder = lockInScope(workorderId);
        StateGuard.require(OBJECT, WorkorderStatus.class, workorder.getWorkorderStatus(), "完工",
            WorkorderStatus.IN_PROGRESS);
        finish(workorder, "complete");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeIfFinished(Long workorderId) {
        // 报工、完成工序任务之后的内部调用，调用方已锁定工单行，不加数据权限
        MesWorkorder workorder = workorderMapper.selectByIdForUpdate(workorderId);
        if (workorder == null) {
            return false;
        }
        // 判断与修改在同一条条件更新中完成：完工数量未达到计划数量或状态已变化时影响 0 行，工单保持原状
        LambdaUpdateWrapper<MesWorkorder> wrapper = Wrappers.lambdaUpdate(MesWorkorder.class)
            .set(MesWorkorder::getWorkorderStatus, WorkorderStatus.COMPLETED.code())
            .set(MesWorkorder::getActualEndTime, LocalDateTime.now())
            .eq(MesWorkorder::getWorkorderId, workorderId)
            .eq(MesWorkorder::getWorkorderStatus, WorkorderStatus.IN_PROGRESS.code())
            .apply("completed_qty >= plan_qty");
        if (workorderMapper.update(null, wrapper) == 0) {
            return false;
        }
        afterFinished(workorder, "auto_complete");
        return true;
    }

    /**
     * 生产中 → 已完工：写实际完成时间，未完成的工序任务一并完成
     */
    private void finish(MesWorkorder workorder, String action) {
        LocalDateTime now = LocalDateTime.now();
        changeStatus(workorder, WorkorderStatus.COMPLETED, w -> w.set(MesWorkorder::getActualEndTime, now));
        afterFinished(workorder, action);
    }

    /**
     * 工单状态变为已完工之后：记录履历，未完成的工序任务一并完成
     */
    private void afterFinished(MesWorkorder workorder, String action) {
        statusLogService.record(BizType.WORKORDER, workorder.getWorkorderId(), workorder.getWorkorderCode(), action,
            WorkorderStatus.IN_PROGRESS.code(), WorkorderStatus.COMPLETED.code(), null);
        changeTasks(workorder, TaskStatus.COMPLETED, action, "工单完工",
            TaskStatus.PENDING, TaskStatus.DISPATCHED, TaskStatus.IN_PROGRESS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long workorderId, String reason) {
        MesWorkorder workorder = lockInScope(workorderId);
        StateGuard.require(OBJECT, WorkorderStatus.class, workorder.getWorkorderStatus(), "关闭", WorkorderStatus.COMPLETED);
        changeStatus(workorder, WorkorderStatus.CLOSED, null);
        statusLogService.record(BizType.WORKORDER, workorderId, workorder.getWorkorderCode(), "close",
            WorkorderStatus.COMPLETED.code(), WorkorderStatus.CLOSED.code(), reason);
        changeTasks(workorder, TaskStatus.CLOSED, "close", "工单关闭",
            TaskStatus.PENDING, TaskStatus.DISPATCHED, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long workorderId, String reason) {
        requireReason(reason, "取消");
        MesWorkorder workorder = lockInScope(workorderId);
        WorkorderStatus from = StateGuard.require(OBJECT, WorkorderStatus.class, workorder.getWorkorderStatus(), "取消",
            WorkorderStatus.DRAFT, WorkorderStatus.RELEASED);
        // 内部判断，统计工单的全部报工（包括其他部门的人报的），不加数据权限
        boolean reported = reportMapper.exists(QueryBuilder.lambda(MesProductionReport.class)
            .eq(MesProductionReport::getWorkorderId, workorderId)
            .ne(MesProductionReport::getReportStatus, ReportStatus.CANCELLED.code())
            .build());
        if (reported) {
            throw new ServiceException("工单已有报工，不能取消");
        }
        changeStatus(workorder, WorkorderStatus.CANCELLED, null);
        statusLogService.record(BizType.WORKORDER, workorderId, workorder.getWorkorderCode(), "cancel", from.code(),
            WorkorderStatus.CANCELLED.code(), reason);
        changeTasks(workorder, TaskStatus.CLOSED, "close", "工单取消",
            TaskStatus.PENDING, TaskStatus.DISPATCHED, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 读取工单并确认在当前用户的数据权限内，否则按不存在处理（不能越过部门按 ID 查看或修改）
     */
    private MesWorkorder requireInScope(Long workorderId) {
        MesWorkorder workorder = workorderMapper.selectByIdInScope(workorderId);
        if (workorder == null) {
            throw new ServiceException("工单不存在");
        }
        return workorder;
    }

    /**
     * 状态动作的入口：先锁定工单行（同一工单的动作、派工与报工依次执行，读到的是最新数据），
     * 再确认在当前用户的数据权限内，否则按不存在处理
     */
    private MesWorkorder lockInScope(Long workorderId) {
        MesWorkorder workorder = workorderMapper.selectByIdForUpdate(workorderId);
        if (workorder == null || workorderMapper.selectByIdInScope(workorderId) == null) {
            throw new ServiceException("工单不存在");
        }
        return workorder;
    }

    private List<MesOperationTask> tasksOf(Long workorderId) {
        return taskMapper.selectList(QueryBuilder.lambda(MesOperationTask.class)
            .eq(MesOperationTask::getWorkorderId, workorderId)
            .orderByAsc(MesOperationTask::getSeqNo, MesOperationTask::getTaskId)
            .build());
    }

    private static void requireReason(String reason, String action) {
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("请填写" + action + "原因");
        }
    }

    /**
     * 带原状态条件改变工单状态，状态已被其他操作改变时拒绝
     *
     * @param extra 同时更新的其他列，可为空
     */
    private void changeStatus(MesWorkorder workorder, WorkorderStatus to,
                              Consumer<LambdaUpdateWrapper<MesWorkorder>> extra) {
        LambdaUpdateWrapper<MesWorkorder> wrapper = Wrappers.lambdaUpdate(MesWorkorder.class)
            .set(MesWorkorder::getWorkorderStatus, to.code())
            .eq(MesWorkorder::getWorkorderId, workorder.getWorkorderId())
            .eq(MesWorkorder::getWorkorderStatus, workorder.getWorkorderStatus());
        if (extra != null) {
            extra.accept(wrapper);
        }
        if (workorderMapper.update(null, wrapper) == 0) {
            throw new ServiceException("工单状态已变化，请刷新后重试");
        }
    }

    /**
     * 把工单下处于给定状态的工序任务改为目标状态，并逐个记录履历
     */
    private void changeTasks(MesWorkorder workorder, TaskStatus to, String action, String reason, TaskStatus... from) {
        List<String> fromCodes = Arrays.stream(from).map(TaskStatus::code).toList();
        // 加锁读取：履历中的原状态取自最新数据
        List<MesOperationTask> tasks = taskMapper.selectByWorkorderForUpdate(workorder.getWorkorderId()).stream()
            .filter(task -> fromCodes.contains(task.getTaskStatus()))
            .toList();
        if (tasks.isEmpty()) {
            return;
        }
        LambdaUpdateWrapper<MesOperationTask> wrapper = Wrappers.lambdaUpdate(MesOperationTask.class)
            .set(MesOperationTask::getTaskStatus, to.code())
            .in(MesOperationTask::getTaskId, MesLookups.ids(tasks, MesOperationTask::getTaskId))
            .in(MesOperationTask::getTaskStatus, fromCodes);
        if (to == TaskStatus.COMPLETED) {
            wrapper.set(MesOperationTask::getActualEndTime, LocalDateTime.now());
        }
        taskMapper.update(null, wrapper);
        for (MesOperationTask task : tasks) {
            statusLogService.record(BizType.OPERATION_TASK, task.getTaskId(), task.getTaskCode(), action,
                task.getTaskStatus(), to.code(), reason);
        }
    }

}
