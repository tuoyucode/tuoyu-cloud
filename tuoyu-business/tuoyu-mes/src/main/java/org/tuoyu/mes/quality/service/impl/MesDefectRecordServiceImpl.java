package org.tuoyu.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.context.UserContext;
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
import org.tuoyu.mes.common.approval.ApprovalCallback;
import org.tuoyu.mes.common.approval.ApprovalOutcome;
import org.tuoyu.mes.common.approval.ApprovalBusinessIds;
import org.tuoyu.mes.common.approval.MesApprovalClient;
import org.tuoyu.mes.common.approval.MesApprovalProperties;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.material.support.SafeLookup;
import org.tuoyu.mes.material.support.UserNicknames;
import org.tuoyu.mes.material.support.WorkorderFilters;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.quality.domain.MesDefectRecord;
import org.tuoyu.mes.quality.domain.MesInspection;
import org.tuoyu.mes.quality.domain.bo.MesDefectHandleBo;
import org.tuoyu.mes.quality.domain.bo.MesDefectRecordBo;
import org.tuoyu.mes.quality.domain.vo.MesDefectRecordVo;
import org.tuoyu.mes.quality.enums.DefectHandleMethod;
import org.tuoyu.mes.quality.enums.DefectHandleStatus;
import org.tuoyu.mes.quality.enums.DefectLevel;
import org.tuoyu.mes.quality.enums.DefectSource;
import org.tuoyu.mes.quality.mapper.MesDefectRecordMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.quality.service.IMesDefectRecordService;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 不良记录
 * <p>
 * 列表、导出与按 ID 的读取、处置都限定在当前用户的数据权限内（按创建部门与创建者），不在其内的记录按不存在处理；
 * 检验不合格时自动生成的不良记录、读取工单与来源单据是单据之间的内部操作，不加数据权限。
 * <p>
 * 开启不良处置评审（{@code tuoyu.mes.approval.defect-disposition}）时，让步接收、报废先提交审批（待处置 → 审批中），
 * 审批通过后才执行处置（审批中 → 已处置）；退回、撤销、终止、作废后回到待处置，可以重新处置。
 * 审批结果按 {@link ApprovalCallback} 接收，不加数据权限。
 *
 * @author tuoyu
 */
@Slf4j
@Service
public class MesDefectRecordServiceImpl implements IMesDefectRecordService, ApprovalCallback {

    private static final String OBJECT = "不良记录";

    private final MesDefectRecordMapper defectMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesMaterialMapper materialMapper;
    private final MesInspectionMapper inspectionMapper;
    private final MesProductionReportMapper reportMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final RemoteUserService userService;
    private final MesApprovalProperties approvalProperties;
    private final MesApprovalClient approvalClient;
    private final TransactionTemplate transaction;

    public MesDefectRecordServiceImpl(MesDefectRecordMapper defectMapper, MesWorkorderMapper workorderMapper,
                                      MesOperationTaskMapper taskMapper, MesMaterialMapper materialMapper,
                                      MesInspectionMapper inspectionMapper, MesProductionReportMapper reportMapper,
                                      IMesCodeService codeService, IMesStatusLogService statusLogService,
                                      RemoteUserService userService, MesApprovalProperties approvalProperties,
                                      MesApprovalClient approvalClient, PlatformTransactionManager transactionManager) {
        this.defectMapper = defectMapper;
        this.workorderMapper = workorderMapper;
        this.taskMapper = taskMapper;
        this.materialMapper = materialMapper;
        this.inspectionMapper = inspectionMapper;
        this.reportMapper = reportMapper;
        this.codeService = codeService;
        this.statusLogService = statusLogService;
        this.userService = userService;
        this.approvalProperties = approvalProperties;
        this.approvalClient = approvalClient;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public MesDefectRecordVo queryById(Long defectId) {
        MesDefectRecordVo vo = BeanMapper.map(requireDefect(defectId), MesDefectRecordVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public MesDefectRecordVo queryForApproval(String businessId) {
        Long defectId = ApprovalBusinessIds.parseId(businessId);
        if (defectId == null) {
            throw new ServiceException("不良记录不存在", HttpStatus.NOT_FOUND);
        }
        approvalClient.checkReadAccess(businessId);
        // 流程参与人可以查看，不按数据权限过滤（租户隔离仍然生效）；只开放提交过审批的记录
        MesDefectRecord record = defectMapper.selectById(defectId);
        if (record == null || StringUtils.isBlank(record.getApprovalBusinessId())) {
            throw new ServiceException("不良记录不存在", HttpStatus.NOT_FOUND);
        }
        MesDefectRecordVo vo = BeanMapper.map(record, MesDefectRecordVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesDefectRecordVo> queryPageList(MesDefectRecordBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesDefectRecord> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesDefectRecordVo> result = PageResults.of(
            defectMapper.selectPageInScope(pageParam.toPage(MesDefectRecord.class), lqw), MesDefectRecordVo.class);
        fill(result.getItems());
        return result;
    }

    @Override
    public List<MesDefectRecordVo> queryList(MesDefectRecordBo bo) {
        List<MesDefectRecordVo> vos = BeanMapper.mapList(defectMapper.selectListInScope(buildQueryWrapper(bo, true)), MesDefectRecordVo.class);
        fill(vos);
        Map<Long, String> names = UserNicknames.of(userService, MesLookups.ids(vos, MesDefectRecordVo::getHandleUserId));
        vos.forEach(vo -> vo.setHandleUserName(SafeLookup.get(names, vo.getHandleUserId())));
        return vos;
    }

    @Override
    public long countList(MesDefectRecordBo bo) {
        return defectMapper.countInScope(buildQueryWrapper(bo, false));
    }

    private LambdaQueryWrapper<MesDefectRecord> buildQueryWrapper(MesDefectRecordBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesDefectRecord.class)
            .likeIfText(MesDefectRecord::getDefectCode, bo.getDefectCode())
            .eqIfText(MesDefectRecord::getSourceType, bo.getSourceType())
            .eqIfText(MesDefectRecord::getDefectLevel, bo.getDefectLevel())
            .eqIfText(MesDefectRecord::getHandleStatus, bo.getHandleStatus())
            .eqIfPresent(MesDefectRecord::getWorkorderId, bo.getWorkorderId())
            .eqIfPresent(MesDefectRecord::getTaskId, bo.getTaskId())
            .inIfNotEmpty(MesDefectRecord::getWorkorderId, WorkorderFilters.idsByCode(workorderMapper, bo.getWorkorderCode()))
            .orderByDesc(defaultOrder, MesDefectRecord::getDefectId)
            .build();
    }

    /**
     * 工单编号、工序名称、产品与来源单据编号
     */
    private void fill(List<MesDefectRecordVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesWorkorder> workorders = MesLookups.byIds(workorderMapper,
            MesLookups.ids(vos, MesDefectRecordVo::getWorkorderId), MesWorkorder::getWorkorderId);
        Map<Long, MesOperationTask> tasks = MesLookups.byIds(taskMapper,
            MesLookups.ids(vos, MesDefectRecordVo::getTaskId), MesOperationTask::getTaskId);
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(vos, MesDefectRecordVo::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesInspection> inspections = MesLookups.byIds(inspectionMapper,
            MesLookups.ids(vos.stream().filter(v -> DefectSource.INSPECTION.code().equals(v.getSourceType())).toList(),
                MesDefectRecordVo::getSourceId), MesInspection::getInspectionId);
        Map<Long, MesProductionReport> reports = MesLookups.byIds(reportMapper,
            MesLookups.ids(vos.stream().filter(v -> DefectSource.REPORT.code().equals(v.getSourceType())).toList(),
                MesDefectRecordVo::getSourceId), MesProductionReport::getReportId);
        for (MesDefectRecordVo vo : vos) {
            MesWorkorder workorder = SafeLookup.get(workorders, vo.getWorkorderId());
            vo.setWorkorderCode(workorder == null ? null : workorder.getWorkorderCode());
            MesOperationTask task = SafeLookup.get(tasks, vo.getTaskId());
            vo.setOperationName(task == null ? null : task.getOperationName());
            MesMaterial material = SafeLookup.get(materials, vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
            }
            if (DefectSource.INSPECTION.code().equals(vo.getSourceType())) {
                MesInspection inspection = SafeLookup.get(inspections, vo.getSourceId());
                vo.setSourceCode(inspection == null ? null : inspection.getInspectionCode());
            } else if (DefectSource.REPORT.code().equals(vo.getSourceType())) {
                MesProductionReport report = SafeLookup.get(reports, vo.getSourceId());
                vo.setSourceCode(report == null ? null : report.getReportCode());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesDefectRecordBo bo) {
        DefectLevel level = CodeEnum.of(DefectLevel.class, bo.getDefectLevel(), "不良等级");
        MesWorkorder workorder = WorkorderFilters.require(workorderMapper, bo.getWorkorderId());
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), "登记不良",
            WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED, WorkorderStatus.COMPLETED);
        if (bo.getTaskId() != null) {
            MesOperationTask task = taskMapper.selectById(bo.getTaskId());
            if (task == null || !Objects.equals(task.getWorkorderId(), workorder.getWorkorderId())) {
                throw new ServiceException("工序任务不属于该工单");
            }
        }
        MesDefectRecord record = new MesDefectRecord();
        record.setSourceType(DefectSource.MANUAL.code());
        record.setWorkorderId(workorder.getWorkorderId());
        record.setTaskId(bo.getTaskId());
        record.setMaterialId(workorder.getMaterialId());
        record.setDefectQty(bo.getDefectQty());
        record.setDefectLevel(level.code());
        record.setDescription(bo.getDescription());
        record.setRemark(bo.getRemark());
        insertPending(record);
    }

    @Override
    public void createFromInspection(MesInspection inspection, String description) {
        MesDefectRecord record = new MesDefectRecord();
        record.setSourceType(DefectSource.INSPECTION.code());
        record.setSourceId(inspection.getInspectionId());
        record.setWorkorderId(inspection.getWorkorderId());
        record.setTaskId(inspection.getTaskId());
        record.setMaterialId(inspection.getMaterialId());
        record.setDefectQty(inspection.getDefectQty());
        // 检验判定不合格的批量不良按严重处理，处置时再决定返工、报废或让步接收
        record.setDefectLevel(DefectLevel.MAJOR.code());
        record.setDescription(StringUtils.substring(description, 0, 500));
        insertPending(record);
    }

    private void insertPending(MesDefectRecord record) {
        record.setDefectCode(codeService.next(CodeRules.DEFECT));
        record.setHandleStatus(DefectHandleStatus.PENDING.code());
        defectMapper.insert(record);
        statusLogService.record(BizType.DEFECT_RECORD, record.getDefectId(), record.getDefectCode(), "create",
            null, DefectHandleStatus.PENDING.code(), null);
    }

    /**
     * 处置。需要评审的处置分三步，各自独立提交：本地改为审批中 → 发起审批（跨模块调用，不参与本地事务）
     * → 发起失败时改回待处置。审批结果由 {@link #completed} 处理
     */
    @Override
    public String handle(Long defectId, MesDefectHandleBo bo) {
        DefectHandleMethod method = CodeEnum.of(DefectHandleMethod.class, bo.getHandleMethod(), "处置方式");
        MesDefectRecord record = requireDefect(defectId);
        StateGuard.require(OBJECT, DefectHandleStatus.class, record.getHandleStatus(), "处置", DefectHandleStatus.PENDING);
        if (!needsApproval(method)) {
            transaction.executeWithoutResult(status -> {
                if (!applyHandle(record, DefectHandleStatus.PENDING, null, method, bo.getHandleRemark(),
                    UserContext.userId(), "handle", method.label())) {
                    throw new ServiceException("不良记录状态已变化，请刷新后重试");
                }
            });
            // 上一轮被退回、撤销的审批已无用，清理留在发起人待办中的流程实例
            approvalClient.discard(record.getApprovalBusinessId());
            return DefectHandleStatus.HANDLED.code();
        }
        submitForApproval(record, method, bo.getHandleRemark());
        return DefectHandleStatus.APPROVING.code();
    }

    /**
     * 开启评审时，让步接收与报废需要审批；返工照常直接处置
     */
    private boolean needsApproval(DefectHandleMethod method) {
        return approvalProperties.getDefectDisposition().isEnabled()
            && (method == DefectHandleMethod.SCRAP || method == DefectHandleMethod.CONCESSION);
    }

    private void submitForApproval(MesDefectRecord record, DefectHandleMethod method, String remark) {
        String flowCode = approvalProperties.getDefectDisposition().getFlowCode();
        String businessId = approvalClient.prepare(record.getDefectId(), record.getApprovalBusinessId());
        String title = approvalTitle(record, method);
        transaction.executeWithoutResult(status -> {
            // 处置方式、说明与提交人先保存，审批通过后按它们执行处置
            int rows = defectMapper.update(new MesDefectRecord(), Wrappers.<MesDefectRecord>lambdaUpdate()
                .set(MesDefectRecord::getHandleStatus, DefectHandleStatus.APPROVING.code())
                .set(MesDefectRecord::getHandleMethod, method.code())
                .set(MesDefectRecord::getHandleRemark, remark)
                .set(MesDefectRecord::getHandleUserId, UserContext.userId())
                .set(MesDefectRecord::getApprovalBusinessId, businessId)
                .eq(MesDefectRecord::getDefectId, record.getDefectId())
                .eq(MesDefectRecord::getHandleStatus, DefectHandleStatus.PENDING.code()));
            if (rows == 0) {
                throw new ServiceException("不良记录状态已变化，请刷新后重试");
            }
            statusLogService.record(BizType.DEFECT_RECORD, record.getDefectId(), record.getDefectCode(), "submit_approval",
                DefectHandleStatus.PENDING.code(), DefectHandleStatus.APPROVING.code(), method.label());
        });
        try {
            approvalClient.start(flowCode, businessId, record.getDefectCode(), title);
        } catch (RuntimeException e) {
            revertFailedStart(record, businessId, e);
            throw e;
        }
    }

    /**
     * 发起审批失败：改回待处置，业务 ID 恢复为上一轮的值。改回也失败时记录日志，向用户报告发起失败的原因
     */
    private void revertFailedStart(MesDefectRecord record, String businessId, RuntimeException cause) {
        try {
            transaction.executeWithoutResult(status -> {
                int rows = defectMapper.update(new MesDefectRecord(), Wrappers.<MesDefectRecord>lambdaUpdate()
                    .set(MesDefectRecord::getHandleStatus, DefectHandleStatus.PENDING.code())
                    .set(MesDefectRecord::getApprovalBusinessId, record.getApprovalBusinessId())
                    .eq(MesDefectRecord::getDefectId, record.getDefectId())
                    .eq(MesDefectRecord::getHandleStatus, DefectHandleStatus.APPROVING.code())
                    .eq(MesDefectRecord::getApprovalBusinessId, businessId));
                if (rows > 0) {
                    statusLogService.record(BizType.DEFECT_RECORD, record.getDefectId(), record.getDefectCode(),
                        "approval_failed", DefectHandleStatus.APPROVING.code(), DefectHandleStatus.PENDING.code(),
                        cause.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.error("发起审批失败后改回待处置也失败，需要人工处理，不良记录: {}", record.getDefectCode(), e);
            cause.addSuppressed(e);
        }
    }

    /**
     * 审批人在待办中看到的标题，例如“不良处置：工单 WO001 产品 M001 数量 5 报废”
     */
    private String approvalTitle(MesDefectRecord record, DefectHandleMethod method) {
        MesWorkorder workorder = record.getWorkorderId() == null ? null : workorderMapper.selectById(record.getWorkorderId());
        MesMaterial material = record.getMaterialId() == null ? null : materialMapper.selectById(record.getMaterialId());
        StringBuilder title = new StringBuilder("不良处置：");
        if (workorder != null) {
            title.append("工单 ").append(workorder.getWorkorderCode()).append(' ');
        }
        if (material != null) {
            title.append("产品 ").append(material.getMaterialCode()).append(' ');
        }
        BigDecimal qty = record.getDefectQty();
        if (qty != null) {
            title.append("数量 ").append(qty.stripTrailingZeros().toPlainString()).append(' ');
        }
        return title.append(method.label()).toString();
    }

    /**
     * 执行处置：带原状态条件（审批结果还要求业务 ID 一致），重复或并发处置同一条记录时只有一次成功。
     * 直接处置与审批通过都经过这里
     *
     * @param businessId 审批的业务 ID，直接处置时为空
     * @return 是否成功；原状态已变化时为 false
     */
    private boolean applyHandle(MesDefectRecord record, DefectHandleStatus from, String businessId, DefectHandleMethod method,
                                String remark, Long handleUserId, String action, String reason) {
        MesDefectRecord change = new MesDefectRecord();
        change.setHandleStatus(DefectHandleStatus.HANDLED.code());
        change.setHandleMethod(method.code());
        change.setHandleRemark(remark);
        change.setHandleUserId(handleUserId);
        change.setHandleTime(LocalDateTime.now());
        LambdaUpdateWrapper<MesDefectRecord> wrapper = Wrappers.<MesDefectRecord>lambdaUpdate()
            .eq(MesDefectRecord::getDefectId, record.getDefectId())
            .eq(MesDefectRecord::getHandleStatus, from.code())
            .eq(businessId != null, MesDefectRecord::getApprovalBusinessId, businessId);
        if (defectMapper.update(change, wrapper) == 0) {
            return false;
        }
        statusLogService.record(BizType.DEFECT_RECORD, record.getDefectId(), record.getDefectCode(), action,
            from.code(), DefectHandleStatus.HANDLED.code(), reason);
        return true;
    }

    @Override
    public String flowCode() {
        return approvalProperties.getDefectDisposition().getFlowCode();
    }

    /**
     * 调用方已忽略租户隔离：按主键读出单据，只取所属租户
     */
    @Override
    public Optional<String> tenantOf(Long bizId) {
        return Optional.ofNullable(defectMapper.selectById(bizId)).map(MesDefectRecord::getTenantId);
    }

    @Override
    public void resubmitted(Long bizId, String businessId) {
        MesDefectRecord record = currentRound(bizId, businessId);
        if (record == null) {
            return;
        }
        int rows = defectMapper.update(new MesDefectRecord(), Wrappers.<MesDefectRecord>lambdaUpdate()
            .set(MesDefectRecord::getHandleStatus, DefectHandleStatus.APPROVING.code())
            .eq(MesDefectRecord::getDefectId, bizId)
            .eq(MesDefectRecord::getHandleStatus, DefectHandleStatus.PENDING.code())
            .eq(MesDefectRecord::getApprovalBusinessId, businessId)
            .in(MesDefectRecord::getHandleMethod, DefectHandleMethod.SCRAP.code(), DefectHandleMethod.CONCESSION.code()));
        if (rows == 0) {
            // 本模块发起或重新发起时单据已是审批中，这里只处理在待办中重新提交的情况
            log.debug("不良记录 {} 不在待处置，忽略提交事件，业务ID: {}", record.getDefectCode(), businessId);
            return;
        }
        statusLogService.record(BizType.DEFECT_RECORD, bizId, record.getDefectCode(), "submit_approval",
            DefectHandleStatus.PENDING.code(), DefectHandleStatus.APPROVING.code(), "在待办中重新提交");
    }

    @Override
    public void completed(Long bizId, String businessId, ApprovalOutcome outcome, String comment) {
        MesDefectRecord record = currentRound(bizId, businessId);
        if (record == null) {
            return;
        }
        boolean applied;
        if (outcome == ApprovalOutcome.APPROVED) {
            Optional<DefectHandleMethod> method = CodeEnum.find(DefectHandleMethod.class, record.getHandleMethod());
            if (method.isEmpty()) {
                log.warn("不良记录 {} 的处置方式不正确：{}，忽略审批结果", record.getDefectCode(), record.getHandleMethod());
                return;
            }
            applied = applyHandle(record, DefectHandleStatus.APPROVING, businessId, method.get(), record.getHandleRemark(),
                record.getHandleUserId(), outcome.action(), method.get().label() + "（" + outcome.reason(comment) + "）");
        } else {
            applied = defectMapper.update(new MesDefectRecord(), Wrappers.<MesDefectRecord>lambdaUpdate()
                .set(MesDefectRecord::getHandleStatus, DefectHandleStatus.PENDING.code())
                .eq(MesDefectRecord::getDefectId, bizId)
                .eq(MesDefectRecord::getHandleStatus, DefectHandleStatus.APPROVING.code())
                .eq(MesDefectRecord::getApprovalBusinessId, businessId)) > 0;
            if (applied) {
                statusLogService.record(BizType.DEFECT_RECORD, bizId, record.getDefectCode(), outcome.action(),
                    DefectHandleStatus.APPROVING.code(), DefectHandleStatus.PENDING.code(), outcome.reason(comment));
            }
        }
        if (!applied) {
            log.info("不良记录 {} 已不在审批中（事件重复或状态已变化），忽略审批结果：{}，业务ID: {}",
                record.getDefectCode(), outcome.label(), businessId);
        }
    }

    /**
     * 审批结果对应的不良记录：不存在或已开始新一轮审批（业务 ID 不同）时为 null
     */
    private MesDefectRecord currentRound(Long bizId, String businessId) {
        MesDefectRecord record = defectMapper.selectById(bizId);
        if (record == null || !Objects.equals(record.getApprovalBusinessId(), businessId)) {
            log.info("审批结果不属于不良记录 {} 当前的审批，忽略，业务ID: {}", bizId, businessId);
            return null;
        }
        return record;
    }

    @Override
    public List<MesStatusLogVo> logs(Long defectId) {
        requireDefect(defectId);
        return statusLogService.list(BizType.DEFECT_RECORD, defectId);
    }

    /**
     * 按 ID 取当前用户数据权限内的不良记录，不存在或不在数据权限内时抛出业务异常
     */
    private MesDefectRecord requireDefect(Long defectId) {
        MesDefectRecord record = defectId == null ? null : defectMapper.selectByIdInScope(defectId);
        if (record == null) {
            throw new ServiceException("不良记录不存在");
        }
        return record;
    }

}
