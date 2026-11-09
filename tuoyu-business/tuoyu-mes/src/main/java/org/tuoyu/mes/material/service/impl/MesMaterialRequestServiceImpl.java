package org.tuoyu.mes.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import org.tuoyu.mes.material.support.SafeLookup;
import org.tuoyu.mes.material.support.Quantities;
import org.tuoyu.mes.material.support.WarehouseChecks;
import org.tuoyu.mes.material.support.WorkorderFilters;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 领退料单
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesMaterialRequestServiceImpl implements IMesMaterialRequestService {

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
        MesMaterialRequest request = requireRequest(requestId);
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
            requestMapper.selectPage(pageParam.toPage(MesMaterialRequest.class), lqw), MesMaterialRequestVo.class);
        fillHeaders(result.getItems());
        return result;
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
        List<MesMaterialRequest> requests = requestMapper.selectByIds(distinct);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long requestId) {
        MesMaterialRequest request = requireRequest(requestId);
        StateGuard.require(OBJECT, MaterialRequestStatus.class, request.getRequestStatus(), "提交", MaterialRequestStatus.DRAFT);
        MesMaterialRequest change = new MesMaterialRequest();
        change.setRequestStatus(MaterialRequestStatus.SUBMITTED.code());
        change.setSubmitTime(LocalDateTime.now());
        transit(request, change, "submit", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long requestId, MesMaterialRequestCompleteBo bo) {
        MesMaterialRequest request = requireRequest(requestId);
        StateGuard.require(OBJECT, MaterialRequestStatus.class, request.getRequestStatus(), "完成", MaterialRequestStatus.SUBMITTED);
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
        if (MaterialRequestType.RETURN.code().equals(request.getRequestType())) {
            checkReturnLimit(request, changes);
        }

        MesMaterialRequest change = new MesMaterialRequest();
        change.setRequestStatus(MaterialRequestStatus.COMPLETED.code());
        change.setCompleteTime(LocalDateTime.now());
        change.setOperatorUserId(UserContext.userId());
        transit(request, change, "complete", null);
        // 实体中的物料 ID 只用于上面的退料校验，不随明细更新
        changes.forEach(item -> item.setMaterialId(null));
        itemMapper.updateById(changes);
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
     * 工单各物料已完成的净实发数：领料 + 补料 − 退料（不含指定的单据）
     */
    private Map<Long, BigDecimal> issuedBalance(Long workorderId, Long excludeRequestId) {
        Map<Long, String> types = requestMapper.selectList(QueryBuilder.lambda(MesMaterialRequest.class)
                .select(MesMaterialRequest::getRequestId, MesMaterialRequest::getRequestType)
                .eq(MesMaterialRequest::getWorkorderId, workorderId)
                .eq(MesMaterialRequest::getRequestStatus, MaterialRequestStatus.COMPLETED.code())
                .ne(MesMaterialRequest::getRequestId, excludeRequestId)
                .build())
            .stream().collect(Collectors.toMap(MesMaterialRequest::getRequestId, MesMaterialRequest::getRequestType, (a, b) -> a));
        Map<Long, BigDecimal> balance = new HashMap<>();
        if (types.isEmpty()) {
            return balance;
        }
        List<MesMaterialRequestItem> items = itemMapper.selectList(QueryBuilder.lambda(MesMaterialRequestItem.class)
            .select(MesMaterialRequestItem::getRequestId, MesMaterialRequestItem::getMaterialId, MesMaterialRequestItem::getActualQty)
            .in(MesMaterialRequestItem::getRequestId, types.keySet())
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

    private MesMaterialRequest requireRequest(Long requestId) {
        MesMaterialRequest request = requestId == null ? null : requestMapper.selectById(requestId);
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
        if (type == MaterialRequestType.RETURN) {
            StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), type.label(),
                WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED, WorkorderStatus.COMPLETED);
        } else {
            StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), type.label(),
                WorkorderStatus.RELEASED, WorkorderStatus.IN_PROGRESS, WorkorderStatus.PAUSED);
        }
        return workorder;
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
