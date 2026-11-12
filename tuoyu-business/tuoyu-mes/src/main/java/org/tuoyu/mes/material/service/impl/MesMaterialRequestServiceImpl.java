package org.tuoyu.mes.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.constant.SystemConstants;
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
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesBomItem;
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.enums.TraceMode;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.material.domain.MesMaterialRequest;
import org.tuoyu.mes.material.domain.MesMaterialRequestItem;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestCompleteBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestGenerateBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestItemBo;
import org.tuoyu.mes.material.domain.vo.MesMaterialRequestItemVo;
import org.tuoyu.mes.material.domain.vo.MesMaterialRequestVo;
import org.tuoyu.mes.material.enums.MaterialRequestStatus;
import org.tuoyu.mes.material.enums.MaterialRequestType;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestMapper;
import org.tuoyu.mes.material.service.IMesMaterialRequestService;
import org.tuoyu.mes.material.support.Quantities;
import org.tuoyu.mes.material.support.SafeLookup;
import org.tuoyu.mes.material.support.UserNicknames;
import org.tuoyu.mes.material.support.WarehouseChecks;
import org.tuoyu.mes.material.support.WorkorderFilters;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.system.api.RemoteUserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 领退料单
 * <p>
 * 列表、导出与按 ID 的读取、状态动作都限定在当前用户的数据权限内（按创建部门与创建者），不在其内的单据按不存在处理；
 * 读取工单、汇总同一工单已完成的领退料是单据之间的内部校验，不加数据权限。
 * <p>
 * 开启补料审批（{@code tuoyu.mes.approval.supplement}）时，补料单提交后先进入审批中，审批通过后才变为已提交、仓库可以执行；
 * 退回、撤销、终止、作废后回到草稿，可以修改后重新提交。领料、退料照常直接提交。
 * 审批结果按 {@link ApprovalCallback} 接收，不加数据权限。
 *
 * @author tuoyu
 */
@Slf4j
@Service
public class MesMaterialRequestServiceImpl implements IMesMaterialRequestService, ApprovalCallback {

    private static final String OBJECT = "领退料单";

    /**
     * 数量保留的小数位数（与表字段 decimal(18,6) 一致）
     */
    private static final int QTY_SCALE = 6;

    private final MesMaterialRequestMapper requestMapper;
    private final MesMaterialRequestItemMapper itemMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesWarehouseMapper warehouseMapper;
    private final MesLocationMapper locationMapper;
    private final MesMaterialMapper materialMapper;
    private final MesBomMapper bomMapper;
    private final MesBomItemMapper bomItemMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final RemoteUserService userService;
    private final MesApprovalProperties approvalProperties;
    private final MesApprovalClient approvalClient;
    private final TransactionTemplate transaction;

    public MesMaterialRequestServiceImpl(MesMaterialRequestMapper requestMapper, MesMaterialRequestItemMapper itemMapper,
                                         MesWorkorderMapper workorderMapper, MesWarehouseMapper warehouseMapper,
                                         MesLocationMapper locationMapper, MesMaterialMapper materialMapper,
                                         MesBomMapper bomMapper, MesBomItemMapper bomItemMapper, IMesCodeService codeService,
                                         IMesStatusLogService statusLogService, RemoteUserService userService,
                                         MesApprovalProperties approvalProperties, MesApprovalClient approvalClient,
                                         PlatformTransactionManager transactionManager) {
        this.requestMapper = requestMapper;
        this.itemMapper = itemMapper;
        this.workorderMapper = workorderMapper;
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.materialMapper = materialMapper;
        this.bomMapper = bomMapper;
        this.bomItemMapper = bomItemMapper;
        this.codeService = codeService;
        this.statusLogService = statusLogService;
        this.userService = userService;
        this.approvalProperties = approvalProperties;
        this.approvalClient = approvalClient;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * 按物料清单计算子件需求：用量 × 工单计划数 ÷ 基准数量 × (1 + 损耗率)，保留 6 位小数（四舍五入）。
     * 先乘后除，只在最后舍入一次，避免中间结果的舍入误差累积
     *
     * @param qty      子件用量（对应基准数量）
     * @param planQty  工单计划数量
     * @param baseQty  基准数量，为空或不大于 0 时按 1 计算
     * @param lossRate 损耗率，为空时按 0 计算
     * @return 需求数量
     */
    public static BigDecimal bomRequirement(BigDecimal qty, BigDecimal planQty, BigDecimal baseQty, BigDecimal lossRate) {
        BigDecimal base = baseQty == null || baseQty.signum() <= 0 ? BigDecimal.ONE : baseQty;
        BigDecimal factor = BigDecimal.ONE.add(Quantities.orZero(lossRate));
        return Quantities.orZero(qty).multiply(Quantities.orZero(planQty)).multiply(factor)
            .divide(base, QTY_SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public MesMaterialRequestVo queryById(Long requestId) {
        return toDetail(requireRequest(requestId));
    }

    @Override
    public MesMaterialRequestVo queryForApproval(String businessId) {
        Long requestId = ApprovalBusinessIds.parseId(businessId);
        if (requestId == null) {
            throw new ServiceException("领退料单不存在", HttpStatus.NOT_FOUND);
        }
        approvalClient.checkReadAccess(businessId);
        // 流程参与人可以查看，不按数据权限过滤（租户隔离仍然生效）；只开放提交过审批的单据
        MesMaterialRequest request = requestMapper.selectById(requestId);
        if (request == null || StringUtils.isBlank(request.getApprovalBusinessId())) {
            throw new ServiceException("领退料单不存在", HttpStatus.NOT_FOUND);
        }
        return toDetail(request);
    }

    private MesMaterialRequestVo toDetail(MesMaterialRequest request) {
        Long requestId = request.getRequestId();
        MesMaterialRequestVo vo = BeanMapper.map(request, MesMaterialRequestVo.class);
        fillHeaders(List.of(vo));
        List<MesMaterialRequestItemVo> items = BeanMapper.mapList(itemMapper.selectList(QueryBuilder.lambda(MesMaterialRequestItem.class)
            .eq(MesMaterialRequestItem::getRequestId, requestId)
            .orderByAsc(MesMaterialRequestItem::getItemId)
            .build()), MesMaterialRequestItemVo.class);
        fillItems(items);
        vo.setItems(items);
        return vo;
    }

    @Override
    public PageResult<MesMaterialRequestVo> queryPageList(MesMaterialRequestBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesMaterialRequest> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesMaterialRequestVo> result = PageResults.of(
            requestMapper.selectPageInScope(pageParam.toPage(MesMaterialRequest.class), lqw), MesMaterialRequestVo.class);
        fillHeaders(result.getItems());
        return result;
    }

    @Override
    public List<MesMaterialRequestVo> queryList(MesMaterialRequestBo bo) {
        List<MesMaterialRequestVo> vos = BeanMapper.mapList(
            requestMapper.selectListInScope(buildQueryWrapper(bo, true)), MesMaterialRequestVo.class);
        fillHeaders(vos);
        Map<Long, String> names = UserNicknames.of(userService, MesLookups.ids(vos, MesMaterialRequestVo::getOperatorUserId));
        vos.forEach(vo -> vo.setOperatorUserName(SafeLookup.get(names, vo.getOperatorUserId())));
        return vos;
    }

    @Override
    public long countList(MesMaterialRequestBo bo) {
        return requestMapper.countInScope(buildQueryWrapper(bo, false));
    }

    private LambdaQueryWrapper<MesMaterialRequest> buildQueryWrapper(MesMaterialRequestBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesMaterialRequest.class)
            .likeIfText(MesMaterialRequest::getRequestCode, bo.getRequestCode())
            .eqIfText(MesMaterialRequest::getRequestType, bo.getRequestType())
            .eqIfText(MesMaterialRequest::getRequestStatus, bo.getRequestStatus())
            .eqIfPresent(MesMaterialRequest::getWorkorderId, bo.getWorkorderId())
            .inIfNotEmpty(MesMaterialRequest::getWorkorderId, WorkorderFilters.idsByCode(workorderMapper, bo.getWorkorderCode()))
            .orderByDesc(defaultOrder, MesMaterialRequest::getRequestId)
            .build();
    }

    /**
     * 列表与详情的表头：工单编号、产品名称、仓库名称
     */
    private void fillHeaders(List<MesMaterialRequestVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesWorkorder> workorders = MesLookups.byIds(workorderMapper,
            MesLookups.ids(vos, MesMaterialRequestVo::getWorkorderId), MesWorkorder::getWorkorderId);
        Map<Long, MesMaterial> products = MesLookups.byIds(materialMapper,
            MesLookups.ids(workorders.values(), MesWorkorder::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesWarehouse> warehouses = MesLookups.byIds(warehouseMapper,
            MesLookups.ids(vos, MesMaterialRequestVo::getWarehouseId), MesWarehouse::getWarehouseId);
        for (MesMaterialRequestVo vo : vos) {
            MesWorkorder workorder = SafeLookup.get(workorders, vo.getWorkorderId());
            if (workorder != null) {
                vo.setWorkorderCode(workorder.getWorkorderCode());
                MesMaterial product = SafeLookup.get(products, workorder.getMaterialId());
                vo.setProductName(product == null ? null : product.getMaterialName());
            }
            MesWarehouse warehouse = SafeLookup.get(warehouses, vo.getWarehouseId());
            vo.setWarehouseName(warehouse == null ? null : warehouse.getWarehouseName());
        }
    }

    /**
     * 明细：物料编码、名称、规格、单位与库位名称
     */
    private void fillItems(List<MesMaterialRequestItemVo> items) {
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(items, MesMaterialRequestItemVo::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesLocation> locations = MesLookups.byIds(locationMapper,
            MesLookups.ids(items, MesMaterialRequestItemVo::getLocationId), MesLocation::getLocationId);
        for (MesMaterialRequestItemVo item : items) {
            MesMaterial material = SafeLookup.get(materials, item.getMaterialId());
            if (material != null) {
                item.setMaterialCode(material.getMaterialCode());
                item.setMaterialName(material.getMaterialName());
                item.setSpecification(material.getSpecification());
                item.setUnit(material.getUnit());
                item.setTraceMode(material.getTraceMode());
            }
            MesLocation location = SafeLookup.get(locations, item.getLocationId());
            item.setLocationName(location == null ? null : location.getLocationName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesMaterialRequestBo bo) {
        MaterialRequestType type = CodeEnum.of(MaterialRequestType.class, bo.getRequestType(), "单据类型");
        requireWorkorderFor(bo.getWorkorderId(), type);
        MesWarehouse warehouse = WarehouseChecks.requireEnabled(warehouseMapper, bo.getWarehouseId());
        checkItems(bo.getItems(), type, warehouse);

        MesMaterialRequest request = new MesMaterialRequest();
        request.setRequestCode(codeService.next(codeRule(type)));
        request.setRequestType(type.code());
        request.setWorkorderId(bo.getWorkorderId());
        request.setWarehouseId(bo.getWarehouseId());
        request.setRequestStatus(MaterialRequestStatus.DRAFT.code());
        request.setRemark(bo.getRemark());
        requestMapper.insert(request);
        insertItems(request.getRequestId(), toItems(bo.getItems(), Map.of()));
        bo.setRequestId(request.getRequestId());
        statusLogService.record(BizType.MATERIAL_REQUEST, request.getRequestId(), request.getRequestCode(), "create",
            null, MaterialRequestStatus.DRAFT.code(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesMaterialRequestBo bo) {
        MesMaterialRequest existing = requireRequest(bo.getRequestId());
        StateGuard.require(OBJECT, MaterialRequestStatus.class, existing.getRequestStatus(), "修改", MaterialRequestStatus.DRAFT);
        if (StringUtils.isNotBlank(bo.getRequestType()) && !bo.getRequestType().equals(existing.getRequestType())) {
            throw new ServiceException("单据类型不能修改，请新建单据");
        }
        MaterialRequestType type = CodeEnum.of(MaterialRequestType.class, existing.getRequestType(), "单据类型");
        requireWorkorderFor(bo.getWorkorderId(), type);
        MesWarehouse warehouse = WarehouseChecks.requireEnabled(warehouseMapper, bo.getWarehouseId());
        checkItems(bo.getItems(), type, warehouse);

        int rows = requestMapper.update(new MesMaterialRequest(), Wrappers.<MesMaterialRequest>lambdaUpdate()
            .set(MesMaterialRequest::getWorkorderId, bo.getWorkorderId())
            .set(MesMaterialRequest::getWarehouseId, bo.getWarehouseId())
            .set(MesMaterialRequest::getRemark, bo.getRemark())
            .eq(MesMaterialRequest::getRequestId, existing.getRequestId())
            .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.DRAFT.code()));
        if (rows == 0) {
            throw new ServiceException("领退料单状态已变化，请刷新后重试");
        }
        // 明细整体替换；按物料清单计算的需求数量按物料保留，不取前端传入的值
        List<MesMaterialRequestItem> oldItems = selectItems(existing.getRequestId());
        Map<Long, BigDecimal> planQty = new HashMap<>();
        for (MesMaterialRequestItem item : oldItems) {
            if (item.getPlanQty() != null) {
                planQty.put(item.getMaterialId(), item.getPlanQty());
            }
        }
        itemMapper.delete(QueryBuilder.lambda(MesMaterialRequestItem.class)
            .eq(MesMaterialRequestItem::getRequestId, existing.getRequestId()).build());
        insertItems(existing.getRequestId(), toItems(bo.getItems(), planQty));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        List<MesMaterialRequest> requests = requestMapper.selectByIdsInScope(distinct);
        if (requests.size() != distinct.size()) {
            throw new ServiceException("领退料单不存在");
        }
        for (MesMaterialRequest request : requests) {
            StateGuard.require(OBJECT + "“" + request.getRequestCode() + "”", MaterialRequestStatus.class,
                request.getRequestStatus(), "删除", MaterialRequestStatus.DRAFT);
        }
        int rows = requestMapper.delete(QueryBuilder.lambda(MesMaterialRequest.class)
            .in(MesMaterialRequest::getRequestId, distinct)
            .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.DRAFT.code())
            .build());
        if (rows != distinct.size()) {
            throw new ServiceException("领退料单状态已变化，请刷新后重试");
        }
        itemMapper.delete(QueryBuilder.lambda(MesMaterialRequestItem.class)
            .in(MesMaterialRequestItem::getRequestId, distinct).build());
        discardAfterCommit(requests);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long generateByBom(MesMaterialRequestGenerateBo bo) {
        MesWorkorder workorder = requireWorkorderFor(bo.getWorkorderId(), MaterialRequestType.ISSUE);
        WarehouseChecks.requireEnabled(warehouseMapper, bo.getWarehouseId());
        MesBom bom = resolveBom(workorder);
        List<MesBomItem> bomItems = bomItemMapper.selectList(QueryBuilder.lambda(MesBomItem.class)
            .eq(MesBomItem::getBomId, bom.getBomId())
            .orderByAsc(MesBomItem::getSortNo, MesBomItem::getItemId)
            .build());
        if (bomItems.isEmpty()) {
            throw new ServiceException("物料清单“" + bom.getBomCode() + "”没有明细，不能生成领料单");
        }
        // 同一子件出现多行时合并为一行
        Map<Long, BigDecimal> requirements = new LinkedHashMap<>();
        for (MesBomItem bomItem : bomItems) {
            BigDecimal need = bomRequirement(bomItem.getQty(), workorder.getPlanQty(), bom.getBaseQty(), bomItem.getLossRate());
            requirements.merge(bomItem.getMaterialId(), need, BigDecimal::add);
        }
        List<MesMaterialRequestItem> items = new ArrayList<>();
        requirements.forEach((materialId, need) -> {
            if (need.signum() > 0) {
                MesMaterialRequestItem item = new MesMaterialRequestItem();
                item.setMaterialId(materialId);
                item.setPlanQty(need);
                item.setRequestQty(need);
                item.setActualQty(BigDecimal.ZERO);
                items.add(item);
            }
        });
        if (items.isEmpty()) {
            throw new ServiceException("按物料清单“" + bom.getBomCode() + "”计算的需求数量都为 0，不能生成领料单");
        }

        MesMaterialRequest request = new MesMaterialRequest();
        request.setRequestCode(codeService.next(CodeRules.MATERIAL_ISSUE));
        request.setRequestType(MaterialRequestType.ISSUE.code());
        request.setWorkorderId(workorder.getWorkorderId());
        request.setWarehouseId(bo.getWarehouseId());
        request.setRequestStatus(MaterialRequestStatus.DRAFT.code());
        request.setRemark("按物料清单 " + bom.getBomCode() + " 生成");
        requestMapper.insert(request);
        insertItems(request.getRequestId(), items);
        statusLogService.record(BizType.MATERIAL_REQUEST, request.getRequestId(), request.getRequestCode(), "create",
            null, MaterialRequestStatus.DRAFT.code(), request.getRemark());
        return request.getRequestId();
    }

    /**
     * 工单的物料清单：工单指定的版本，未指定时取产品的默认版本（启用的）
     */
    private MesBom resolveBom(MesWorkorder workorder) {
        if (workorder.getBomId() != null) {
            MesBom bom = bomMapper.selectById(workorder.getBomId());
            if (bom == null) {
                throw new ServiceException("工单的物料清单不存在");
            }
            return bom;
        }
        List<MesBom> defaults = bomMapper.selectList(QueryBuilder.lambda(MesBom.class)
            .eq(MesBom::getMaterialId, workorder.getMaterialId())
            .eq(MesBom::getIsDefault, SystemConstants.YES)
            .eq(MesBom::getStatus, SystemConstants.NORMAL)
            .orderByDesc(MesBom::getBomId)
            .build());
        if (defaults.isEmpty()) {
            throw new ServiceException("工单没有物料清单，不能按 BOM 生成领料单（请为产品设置默认的物料清单）");
        }
        return defaults.getFirst();
    }

    /**
     * 提交。需要审批的补料单分三步，各自独立提交：本地改为审批中 → 发起审批（跨模块调用，不参与本地事务）
     * → 发起失败时改回草稿。审批结果由 {@link #completed} 处理
     */
    @Override
    public String submit(Long requestId) {
        MesMaterialRequest request = requireRequest(requestId);
        StateGuard.require(OBJECT, MaterialRequestStatus.class, request.getRequestStatus(), "提交", MaterialRequestStatus.DRAFT);
        requireWorkorderStillAllows(request, WorkorderFilters.require(workorderMapper, request.getWorkorderId()), "提交");
        if (!needsApproval(request)) {
            transaction.executeWithoutResult(status -> {
                MesMaterialRequest change = new MesMaterialRequest();
                change.setRequestStatus(MaterialRequestStatus.SUBMITTED.code());
                change.setSubmitTime(LocalDateTime.now());
                transit(request, change, "submit", null);
            });
            // 上一轮被退回、撤销的审批已无用，清理留在发起人待办中的流程实例
            approvalClient.discard(request.getApprovalBusinessId());
            return MaterialRequestStatus.SUBMITTED.code();
        }
        submitForApproval(request);
        return MaterialRequestStatus.APPROVING.code();
    }

    /**
     * 开启补料审批时，补料单需要审批；领料、退料照常直接提交
     */
    private boolean needsApproval(MesMaterialRequest request) {
        return approvalProperties.getSupplement().isEnabled()
            && MaterialRequestType.SUPPLEMENT.code().equals(request.getRequestType());
    }

    private void submitForApproval(MesMaterialRequest request) {
        String flowCode = approvalProperties.getSupplement().getFlowCode();
        String businessId = approvalClient.prepare(request.getRequestId(), request.getApprovalBusinessId());
        String title = approvalTitle(request);
        transaction.executeWithoutResult(status -> {
            int rows = requestMapper.update(new MesMaterialRequest(), Wrappers.<MesMaterialRequest>lambdaUpdate()
                .set(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.APPROVING.code())
                .set(MesMaterialRequest::getSubmitTime, LocalDateTime.now())
                .set(MesMaterialRequest::getApprovalBusinessId, businessId)
                .eq(MesMaterialRequest::getRequestId, request.getRequestId())
                .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.DRAFT.code()));
            if (rows == 0) {
                throw new ServiceException("领退料单状态已变化，请刷新后重试");
            }
            statusLogService.record(BizType.MATERIAL_REQUEST, request.getRequestId(), request.getRequestCode(),
                "submit_approval", MaterialRequestStatus.DRAFT.code(), MaterialRequestStatus.APPROVING.code(), null);
        });
        try {
            approvalClient.start(flowCode, businessId, request.getRequestCode(), title);
        } catch (RuntimeException e) {
            revertFailedStart(request, businessId, e);
            throw e;
        }
    }

    /**
     * 发起审批失败：改回草稿，业务 ID 恢复为上一轮的值。改回也失败时记录日志，向用户报告发起失败的原因
     */
    private void revertFailedStart(MesMaterialRequest request, String businessId, RuntimeException cause) {
        try {
            transaction.executeWithoutResult(status -> {
                int rows = requestMapper.update(new MesMaterialRequest(), Wrappers.<MesMaterialRequest>lambdaUpdate()
                    .set(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.DRAFT.code())
                    .set(MesMaterialRequest::getSubmitTime, request.getSubmitTime())
                    .set(MesMaterialRequest::getApprovalBusinessId, request.getApprovalBusinessId())
                    .eq(MesMaterialRequest::getRequestId, request.getRequestId())
                    .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.APPROVING.code())
                    .eq(MesMaterialRequest::getApprovalBusinessId, businessId));
                if (rows > 0) {
                    statusLogService.record(BizType.MATERIAL_REQUEST, request.getRequestId(), request.getRequestCode(),
                        "approval_failed", MaterialRequestStatus.APPROVING.code(), MaterialRequestStatus.DRAFT.code(),
                        cause.getMessage());
                }
            });
        } catch (RuntimeException e) {
            log.error("发起审批失败后改回草稿也失败，需要人工处理，领退料单: {}", request.getRequestCode(), e);
            cause.addSuppressed(e);
        }
    }

    /**
     * 审批人在待办中看到的标题，例如“补料申请：工单 WO001 原料仓 M001 × 5 等 3 项物料”
     */
    private String approvalTitle(MesMaterialRequest request) {
        MesWorkorder workorder = request.getWorkorderId() == null ? null : workorderMapper.selectById(request.getWorkorderId());
        MesWarehouse warehouse = request.getWarehouseId() == null ? null : warehouseMapper.selectById(request.getWarehouseId());
        List<MesMaterialRequestItem> items = selectItems(request.getRequestId());
        StringBuilder title = new StringBuilder("补料申请：");
        if (workorder != null) {
            title.append("工单 ").append(workorder.getWorkorderCode()).append(' ');
        }
        if (warehouse != null) {
            title.append(warehouse.getWarehouseName()).append(' ');
        }
        if (!items.isEmpty()) {
            MesMaterialRequestItem first = items.getFirst();
            MesMaterial material = first.getMaterialId() == null ? null : materialMapper.selectById(first.getMaterialId());
            if (material != null) {
                title.append(material.getMaterialCode()).append(" × ")
                    .append(Quantities.orZero(first.getRequestQty()).stripTrailingZeros().toPlainString()).append(' ');
            }
            if (items.size() > 1) {
                title.append("等 ");
            }
            title.append(items.size()).append(" 项物料");
        }
        return title.toString().strip();
    }

    @Override
    public String flowCode() {
        return approvalProperties.getSupplement().getFlowCode();
    }

    /**
     * 调用方已忽略租户隔离：按主键读出单据，只取所属租户
     */
    @Override
    public Optional<String> tenantOf(Long bizId) {
        return Optional.ofNullable(requestMapper.selectById(bizId)).map(MesMaterialRequest::getTenantId);
    }

    @Override
    public void resubmitted(Long bizId, String businessId) {
        MesMaterialRequest request = currentRound(bizId, businessId);
        if (request == null) {
            return;
        }
        int rows = requestMapper.update(new MesMaterialRequest(), Wrappers.<MesMaterialRequest>lambdaUpdate()
            .set(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.APPROVING.code())
            .eq(MesMaterialRequest::getRequestId, bizId)
            .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.DRAFT.code())
            .eq(MesMaterialRequest::getRequestType, MaterialRequestType.SUPPLEMENT.code())
            .eq(MesMaterialRequest::getApprovalBusinessId, businessId));
        if (rows == 0) {
            // 本模块发起或重新发起时单据已是审批中，这里只处理在待办中重新提交的情况
            log.debug("领退料单 {} 不在草稿，忽略提交事件，业务ID: {}", request.getRequestCode(), businessId);
            return;
        }
        statusLogService.record(BizType.MATERIAL_REQUEST, bizId, request.getRequestCode(), "submit_approval",
            MaterialRequestStatus.DRAFT.code(), MaterialRequestStatus.APPROVING.code(), "在待办中重新提交");
    }

    @Override
    public void completed(Long bizId, String businessId, ApprovalOutcome outcome, String comment) {
        MesMaterialRequest request = currentRound(bizId, businessId);
        if (request == null) {
            return;
        }
        MaterialRequestStatus to = outcome == ApprovalOutcome.APPROVED ? MaterialRequestStatus.SUBMITTED : MaterialRequestStatus.DRAFT;
        int rows = requestMapper.update(new MesMaterialRequest(), Wrappers.<MesMaterialRequest>lambdaUpdate()
            .set(MesMaterialRequest::getRequestStatus, to.code())
            .eq(MesMaterialRequest::getRequestId, bizId)
            .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.APPROVING.code())
            .eq(MesMaterialRequest::getApprovalBusinessId, businessId));
        if (rows == 0) {
            log.info("领退料单 {} 已不在审批中（事件重复或状态已变化），忽略审批结果：{}，业务ID: {}",
                request.getRequestCode(), outcome.label(), businessId);
            return;
        }
        statusLogService.record(BizType.MATERIAL_REQUEST, bizId, request.getRequestCode(), outcome.action(),
            MaterialRequestStatus.APPROVING.code(), to.code(), outcome.reason(comment));
    }

    /**
     * 审批结果对应的单据：不存在或已开始新一轮审批（业务 ID 不同）时为 null
     */
    private MesMaterialRequest currentRound(Long bizId, String businessId) {
        MesMaterialRequest request = requestMapper.selectById(bizId);
        if (request == null || !Objects.equals(request.getApprovalBusinessId(), businessId)) {
            log.info("审批结果不属于领退料单 {} 当前的审批，忽略，业务ID: {}", bizId, businessId);
            return null;
        }
        return request;
    }

    /**
     * 草稿被删除或取消后，在事务提交后清理它们被退回、撤销后留在发起人待办中的流程实例
     */
    private void discardAfterCommit(Collection<MesMaterialRequest> requests) {
        List<String> businessIds = requests.stream().map(MesMaterialRequest::getApprovalBusinessId)
            .filter(StringUtils::isNotBlank).toList();
        if (businessIds.isEmpty()) {
            return;
        }
        Runnable discard = () -> businessIds.forEach(approvalClient::discard);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    discard.run();
                }
            });
        } else {
            discard.run();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long requestId, MesMaterialRequestCompleteBo bo) {
        MesMaterialRequest request = requireRequest(requestId);
        StateGuard.require(OBJECT, MaterialRequestStatus.class, request.getRequestStatus(), "完成", MaterialRequestStatus.SUBMITTED);
        boolean returning = MaterialRequestType.RETURN.code().equals(request.getRequestType());
        // 锁住工单行：与工单的关闭、取消、暂停等状态动作依次执行，按最新的工单状态判断能否完成；
        // 同一工单的退料也因此依次完成，下面计算的可退数量不会被并发完成的退料越过
        MesWorkorder workorder = WorkorderFilters.lock(workorderMapper, request.getWorkorderId());
        requireWorkorderStillAllows(request, workorder, "完成");
        List<MesMaterialRequestItem> items = selectItems(requestId);
        Map<Long, MesMaterialRequestCompleteBo.Item> inputs = new HashMap<>();
        for (MesMaterialRequestCompleteBo.Item input : bo.getItems()) {
            if (inputs.put(input.getItemId(), input) != null) {
                throw new ServiceException("明细重复填写");
            }
        }
        Set<Long> itemIds = items.stream().map(MesMaterialRequestItem::getItemId).collect(Collectors.toSet());
        if (!itemIds.containsAll(inputs.keySet())) {
            throw new ServiceException("明细不属于该领退料单");
        }
        if (inputs.size() != items.size()) {
            throw new ServiceException("请填写全部明细的实发数量");
        }
        MesWarehouse warehouse = warehouseMapper.selectById(request.getWarehouseId());
        if (warehouse == null) {
            throw new ServiceException("仓库不存在");
        }
        WarehouseChecks.checkLocations(locationMapper, warehouse,
            inputs.values().stream().map(MesMaterialRequestCompleteBo.Item::getLocationId).toList());

        List<MesMaterialRequestItem> changes = new ArrayList<>();
        for (MesMaterialRequestItem item : items) {
            MesMaterialRequestCompleteBo.Item input = inputs.get(item.getItemId());
            MesMaterialRequestItem change = new MesMaterialRequestItem();
            change.setItemId(item.getItemId());
            change.setMaterialId(item.getMaterialId());
            change.setActualQty(input.getActualQty());
            change.setBatchCode(StringUtils.isNotBlank(input.getBatchCode()) ? input.getBatchCode().trim() : item.getBatchCode());
            change.setLocationId(input.getLocationId() != null ? input.getLocationId() : item.getLocationId());
            changes.add(change);
        }
        requireBatchCodes(changes);
        if (returning) {
            checkReturnLimit(request, changes);
        }

        MesMaterialRequest change = new MesMaterialRequest();
        change.setRequestStatus(MaterialRequestStatus.COMPLETED.code());
        change.setCompleteTime(LocalDateTime.now());
        change.setOperatorUserId(UserContext.userId());
        // 带原状态条件：重复或并发完成同一张单据时只有一次成功，实发数量只写入一次
        transit(request, change, "complete", null);
        // 实体中的物料 ID 只用于上面的退料校验，不随明细更新
        changes.forEach(item -> item.setMaterialId(null));
        itemMapper.updateById(changes);
    }

    /**
     * 按批次追溯的物料（{@link TraceMode#BATCH}）实发（实退）数量大于 0 时必须有物料批次号，缺少时整单拒绝并列出这些物料。
     * 按物料当前的追溯方式判断
     */
    private void requireBatchCodes(List<MesMaterialRequestItem> changes) {
        List<MesMaterialRequestItem> blank = changes.stream()
            .filter(item -> Quantities.orZero(item.getActualQty()).signum() > 0 && StringUtils.isBlank(item.getBatchCode()))
            .toList();
        if (blank.isEmpty()) {
            return;
        }
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(blank, MesMaterialRequestItem::getMaterialId), MesMaterial::getMaterialId);
        List<String> missing = blank.stream()
            .map(item -> SafeLookup.get(materials, item.getMaterialId()))
            .filter(material -> material != null && TraceMode.BATCH.code().equals(material.getTraceMode()))
            .map(material -> material.getMaterialCode() + " " + material.getMaterialName())
            .distinct()
            .toList();
        if (!missing.isEmpty()) {
            throw new ServiceException("物料“" + String.join("”、“", missing) + "”按批次追溯，请填写物料批次号");
        }
    }

    /**
     * 退料上限：每个物料的实退数不能超过该工单该物料已完成的（领料 + 补料 − 退料）实发数
     */
    private void checkReturnLimit(MesMaterialRequest request, List<MesMaterialRequestItem> returning) {
        Map<Long, BigDecimal> returnQty = new LinkedHashMap<>();
        for (MesMaterialRequestItem item : returning) {
            returnQty.merge(item.getMaterialId(), Quantities.orZero(item.getActualQty()), BigDecimal::add);
        }
        Map<Long, BigDecimal> available = issuedBalance(request.getWorkorderId(), request.getRequestId());
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper, returnQty.keySet(), MesMaterial::getMaterialId);
        returnQty.forEach((materialId, qty) -> {
            BigDecimal limit = available.getOrDefault(materialId, BigDecimal.ZERO).max(BigDecimal.ZERO);
            if (qty.compareTo(limit) > 0) {
                MesMaterial material = materials.get(materialId);
                String name = material == null ? String.valueOf(materialId)
                    : material.getMaterialCode() + " " + material.getMaterialName();
                throw new ServiceException("物料“" + name + "”最多还能退 " + Quantities.format(limit)
                    + "（该工单已完成的领料、补料扣除退料后的实发数）");
            }
        });
    }

    /**
     * 工单各物料已完成的净实发数：领料 + 补料 − 退料（不含指定的单据）。
     * 调用前已锁住工单行；这里用加锁读取（for update）：MySQL 默认的可重复读下，普通读取可能读到事务开始时的快照，
     * 看不到刚提交的退料，加锁读取总是读到已提交的最新数据
     */
    private Map<Long, BigDecimal> issuedBalance(Long workorderId, Long excludeRequestId) {
        Map<Long, String> types = requestMapper.selectList(QueryBuilder.lambda(MesMaterialRequest.class)
                .select(MesMaterialRequest::getRequestId, MesMaterialRequest::getRequestType)
                .eq(MesMaterialRequest::getWorkorderId, workorderId)
                .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.COMPLETED.code())
                .ne(MesMaterialRequest::getRequestId, excludeRequestId)
                .last("for update")
                .build())
            .stream().collect(Collectors.toMap(MesMaterialRequest::getRequestId, MesMaterialRequest::getRequestType, (a, b) -> a));
        Map<Long, BigDecimal> balance = new HashMap<>();
        if (types.isEmpty()) {
            return balance;
        }
        List<MesMaterialRequestItem> items = itemMapper.selectList(QueryBuilder.lambda(MesMaterialRequestItem.class)
            .select(MesMaterialRequestItem::getRequestId, MesMaterialRequestItem::getMaterialId, MesMaterialRequestItem::getActualQty)
            .in(MesMaterialRequestItem::getRequestId, types.keySet())
            .last("for update")
            .build());
        for (MesMaterialRequestItem item : items) {
            BigDecimal qty = Quantities.orZero(item.getActualQty());
            if (MaterialRequestType.RETURN.code().equals(types.get(item.getRequestId()))) {
                qty = qty.negate();
            }
            balance.merge(item.getMaterialId(), qty, BigDecimal::add);
        }
        return balance;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long requestId) {
        MesMaterialRequest request = requireRequest(requestId);
        StateGuard.require(OBJECT, MaterialRequestStatus.class, request.getRequestStatus(), "取消",
            MaterialRequestStatus.DRAFT, MaterialRequestStatus.SUBMITTED);
        MesMaterialRequest change = new MesMaterialRequest();
        change.setRequestStatus(MaterialRequestStatus.CANCELLED.code());
        transit(request, change, "cancel", null);
        discardAfterCommit(List.of(request));
    }

    @Override
    public List<MesStatusLogVo> logs(Long requestId) {
        requireRequest(requestId);
        return statusLogService.list(BizType.MATERIAL_REQUEST, requestId);
    }

    /**
     * 带原状态条件的状态变更，并记录履历
     */
    private void transit(MesMaterialRequest request, MesMaterialRequest change, String action, String reason) {
        int rows = requestMapper.update(change, Wrappers.<MesMaterialRequest>lambdaUpdate()
            .eq(MesMaterialRequest::getRequestId, request.getRequestId())
            .eq(MesMaterialRequest::getRequestStatus, request.getRequestStatus()));
        if (rows == 0) {
            throw new ServiceException("领退料单状态已变化，请刷新后重试");
        }
        statusLogService.record(BizType.MATERIAL_REQUEST, request.getRequestId(), request.getRequestCode(), action,
            request.getRequestStatus(), change.getRequestStatus(), reason);
    }

    /**
     * 按 ID 取当前用户数据权限内的单据，不存在或不在数据权限内时抛出业务异常
     */
    private MesMaterialRequest requireRequest(Long requestId) {
        MesMaterialRequest request = requestId == null ? null : requestMapper.selectByIdInScope(requestId);
        if (request == null) {
            throw new ServiceException("领退料单不存在");
        }
        return request;
    }

    private List<MesMaterialRequestItem> selectItems(Long requestId) {
        return itemMapper.selectList(QueryBuilder.lambda(MesMaterialRequestItem.class)
            .eq(MesMaterialRequestItem::getRequestId, requestId)
            .orderByAsc(MesMaterialRequestItem::getItemId)
            .build());
    }

    /**
     * 工单必须允许该类型的领退料：领料、补料要求已下达、生产中或已暂停；退料另外允许已完工
     */
    private MesWorkorder requireWorkorderFor(Long workorderId, MaterialRequestType type) {
        MesWorkorder workorder = WorkorderFilters.require(workorderMapper, workorderId);
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), type.label(),
            allowedWorkorderStatuses(type));
        return workorder;
    }

    /**
     * 单据建好之后工单可能已关闭、取消或完工：提交与完成时按新增时相同的要求重新检查，不允许时拒绝并提示可以取消单据
     */
    private static void requireWorkorderStillAllows(MesMaterialRequest request, MesWorkorder workorder, String action) {
        MaterialRequestType type = CodeEnum.of(MaterialRequestType.class, request.getRequestType(), "单据类型");
        if (!CodeEnum.in(workorder.getWorkorderStatus(), allowedWorkorderStatuses(type))) {
            String current = CodeEnum.find(WorkorderStatus.class, workorder.getWorkorderStatus())
                .map(WorkorderStatus::label).orElse(workorder.getWorkorderStatus());
            throw new ServiceException("工单“" + workorder.getWorkorderCode() + "”当前为“" + current + "”，不能" + action
                + type.label() + "单“" + request.getRequestCode() + "”，可以取消该单据");
        }
    }

    private static WorkorderStatus[] allowedWorkorderStatuses(MaterialRequestType type) {
        return type == MaterialRequestType.RETURN
            ? new WorkorderStatus[]{WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED,
                WorkorderStatus.COMPLETED}
            : new WorkorderStatus[]{WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED};
    }

    /**
     * 明细：物料存在、不重复（领料、补料的物料还必须启用），库位属于单据的仓库
     */
    private void checkItems(List<MesMaterialRequestItemBo> items, MaterialRequestType type, MesWarehouse warehouse) {
        Set<Long> seen = new HashSet<>();
        for (MesMaterialRequestItemBo item : items) {
            if (!seen.add(item.getMaterialId())) {
                MesMaterial material = materialMapper.selectById(item.getMaterialId());
                String name = material == null ? String.valueOf(item.getMaterialId()) : material.getMaterialName();
                throw new ServiceException("物料“" + name + "”重复，同一物料请合并为一行");
            }
        }
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper, seen, MesMaterial::getMaterialId);
        for (Long materialId : seen) {
            MesMaterial material = materials.get(materialId);
            if (material == null) {
                throw new ServiceException("明细中的物料不存在");
            }
            if (type != MaterialRequestType.RETURN && !SystemConstants.NORMAL.equals(material.getStatus())) {
                throw new ServiceException("物料“" + material.getMaterialName() + "”已停用");
            }
        }
        WarehouseChecks.checkLocations(locationMapper, warehouse,
            items.stream().map(MesMaterialRequestItemBo::getLocationId).filter(Objects::nonNull).toList());
    }

    private static List<MesMaterialRequestItem> toItems(List<MesMaterialRequestItemBo> items, Map<Long, BigDecimal> planQty) {
        return items.stream().map(bo -> {
            MesMaterialRequestItem item = BeanMapper.map(bo, MesMaterialRequestItem.class);
            item.setItemId(null);
            item.setPlanQty(planQty.get(bo.getMaterialId()));
            item.setActualQty(BigDecimal.ZERO);
            item.setBatchCode(StringUtils.isBlank(bo.getBatchCode()) ? null : bo.getBatchCode().trim());
            return item;
        }).toList();
    }

    private void insertItems(Long requestId, List<MesMaterialRequestItem> items) {
        items.forEach(item -> item.setRequestId(requestId));
        itemMapper.insert(items);
    }

    private static String codeRule(MaterialRequestType type) {
        return switch (type) {
            case ISSUE -> CodeRules.MATERIAL_ISSUE;
            case SUPPLEMENT -> CodeRules.MATERIAL_SUPPLEMENT;
            case RETURN -> CodeRules.MATERIAL_RETURN;
        };
    }

}
