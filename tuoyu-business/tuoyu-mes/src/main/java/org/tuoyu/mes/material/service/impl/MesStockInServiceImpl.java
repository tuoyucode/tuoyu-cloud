package org.tuoyu.mes.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
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
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.enums.WarehouseType;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.material.domain.MesStockIn;
import org.tuoyu.mes.material.domain.bo.MesStockInBo;
import org.tuoyu.mes.material.domain.vo.MesStockInVo;
import org.tuoyu.mes.material.enums.StockInStatus;
import org.tuoyu.mes.material.mapper.MesStockInMapper;
import org.tuoyu.mes.material.service.IMesStockInService;
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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 完工入库
 * <p>
 * 工单的已入库数量（stocked_qty）只在这里、入库完成时条件累加，其他代码不写这一列。
 * 列表、导出与按 ID 的读取、状态动作都限定在当前用户的数据权限内（按创建部门与创建者），不在其内的单据按不存在处理；
 * 读取工单、汇总同一工单的入库数量是单据之间的内部校验，不加数据权限。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesStockInServiceImpl implements IMesStockInService {

    private static final String OBJECT = "入库单";

    /**
     * 允许新增与确认入库单的工单状态
     */
    private static final WorkorderStatus[] STOCK_IN_WORKORDER_STATUSES = {
        WorkorderStatus.IN_PROGRESS, WorkorderStatus.COMPLETED
    };

    private final MesStockInMapper stockInMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesWarehouseMapper warehouseMapper;
    private final MesLocationMapper locationMapper;
    private final MesMaterialMapper materialMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;
    private final RemoteUserService userService;

    @Override
    public MesStockInVo queryById(Long stockInId) {
        MesStockInVo vo = BeanMapper.map(requireStockIn(stockInId), MesStockInVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesStockInVo> queryPageList(MesStockInBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesStockIn> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesStockInVo> result = PageResults.of(
            stockInMapper.selectPageInScope(pageParam.toPage(MesStockIn.class), lqw), MesStockInVo.class);
        fill(result.getItems());
        return result;
    }

    @Override
    public List<MesStockInVo> queryList(MesStockInBo bo) {
        List<MesStockInVo> vos = BeanMapper.mapList(stockInMapper.selectListInScope(buildQueryWrapper(bo, true)), MesStockInVo.class);
        fill(vos);
        Map<Long, String> names = UserNicknames.of(userService, MesLookups.ids(vos, MesStockInVo::getOperatorUserId));
        vos.forEach(vo -> vo.setOperatorUserName(SafeLookup.get(names, vo.getOperatorUserId())));
        return vos;
    }

    @Override
    public long countList(MesStockInBo bo) {
        return stockInMapper.countInScope(buildQueryWrapper(bo, false));
    }

    private LambdaQueryWrapper<MesStockIn> buildQueryWrapper(MesStockInBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesStockIn.class)
            .likeIfText(MesStockIn::getStockInCode, bo.getStockInCode())
            .eqIfText(MesStockIn::getStockInStatus, bo.getStockInStatus())
            .eqIfPresent(MesStockIn::getWorkorderId, bo.getWorkorderId())
            .inIfNotEmpty(MesStockIn::getWorkorderId, WorkorderFilters.idsByCode(workorderMapper, bo.getWorkorderCode()))
            .orderByDesc(defaultOrder, MesStockIn::getStockInId)
            .build();
    }

    /**
     * 工单编号、产品、仓库与库位名称
     */
    private void fill(List<MesStockInVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesWorkorder> workorders = MesLookups.byIds(workorderMapper,
            MesLookups.ids(vos, MesStockInVo::getWorkorderId), MesWorkorder::getWorkorderId);
        Map<Long, MesMaterial> materials = MesLookups.byIds(materialMapper,
            MesLookups.ids(vos, MesStockInVo::getMaterialId), MesMaterial::getMaterialId);
        Map<Long, MesWarehouse> warehouses = MesLookups.byIds(warehouseMapper,
            MesLookups.ids(vos, MesStockInVo::getWarehouseId), MesWarehouse::getWarehouseId);
        Map<Long, MesLocation> locations = MesLookups.byIds(locationMapper,
            MesLookups.ids(vos, MesStockInVo::getLocationId), MesLocation::getLocationId);
        for (MesStockInVo vo : vos) {
            MesWorkorder workorder = SafeLookup.get(workorders, vo.getWorkorderId());
            vo.setWorkorderCode(workorder == null ? null : workorder.getWorkorderCode());
            MesMaterial material = SafeLookup.get(materials, vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getMaterialCode());
                vo.setMaterialName(material.getMaterialName());
                vo.setUnit(material.getUnit());
            }
            MesWarehouse warehouse = SafeLookup.get(warehouses, vo.getWarehouseId());
            vo.setWarehouseName(warehouse == null ? null : warehouse.getWarehouseName());
            MesLocation location = SafeLookup.get(locations, vo.getLocationId());
            vo.setLocationName(location == null ? null : location.getLocationName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesStockInBo bo) {
        // 先锁住工单行：同一工单的入库单依次新增，下面汇总的数量不会被并发的新增越过
        MesWorkorder workorder = WorkorderFilters.lock(workorderMapper, bo.getWorkorderId());
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), "入库",
            STOCK_IN_WORKORDER_STATUSES);
        MesWarehouse warehouse = WarehouseChecks.requireEnabled(warehouseMapper, bo.getWarehouseId());
        if (!WarehouseType.FINISHED.code().equals(warehouse.getWarehouseType())) {
            String typeLabel = CodeEnum.find(WarehouseType.class, warehouse.getWarehouseType())
                .map(WarehouseType::label).orElse(warehouse.getWarehouseType());
            throw new ServiceException("完工入库只能入成品仓，仓库“" + warehouse.getWarehouseName() + "”是" + typeLabel);
        }
        WarehouseChecks.checkLocations(locationMapper, warehouse, Collections.singletonList(bo.getLocationId()));
        BigDecimal remaining = Quantities.orZero(workorder.getCompletedQty()).subtract(activeQty(workorder.getWorkorderId()));
        if (bo.getQty().compareTo(remaining) > 0) {
            throw new ServiceException("本工单最多还能入库 " + Quantities.format(remaining.max(BigDecimal.ZERO))
                + "（已提交与已入库的数量合计不能超过完工数量 " + Quantities.format(workorder.getCompletedQty()) + "）");
        }

        MesStockIn stockIn = new MesStockIn();
        stockIn.setStockInCode(codeService.next(CodeRules.STOCK_IN));
        stockIn.setWorkorderId(workorder.getWorkorderId());
        stockIn.setMaterialId(workorder.getMaterialId());
        stockIn.setWarehouseId(warehouse.getWarehouseId());
        stockIn.setLocationId(bo.getLocationId());
        stockIn.setQty(bo.getQty());
        stockIn.setBatchCode(workorder.getBatchCode());
        stockIn.setStockInStatus(StockInStatus.SUBMITTED.code());
        stockIn.setRemark(bo.getRemark());
        stockInMapper.insert(stockIn);
        statusLogService.record(BizType.STOCK_IN, stockIn.getStockInId(), stockIn.getStockInCode(), "create",
            null, StockInStatus.SUBMITTED.code(), null);
    }

    /**
     * 工单已提交与已入库的入库数量合计。用加锁读取（for update）：MySQL 默认的可重复读下，普通读取可能读到事务开始时的快照，
     * 加锁读取总是读到已提交的最新数据；不用聚合函数，PostgreSQL 的 for update 不能与聚合一起使用
     */
    private BigDecimal activeQty(Long workorderId) {
        return stockInMapper.selectList(QueryBuilder.lambda(MesStockIn.class)
                .select(MesStockIn::getQty)
                .eq(MesStockIn::getWorkorderId, workorderId)
                .in(MesStockIn::getStockInStatus, StockInStatus.SUBMITTED.code(), StockInStatus.COMPLETED.code())
                .last("for update")
                .build())
            .stream().map(s -> Quantities.orZero(s.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long stockInId) {
        MesStockIn stockIn = requireStockIn(stockInId);
        StateGuard.require(OBJECT, StockInStatus.class, stockIn.getStockInStatus(), "入库", StockInStatus.SUBMITTED);
        // 先锁工单、再改入库单，与撤销报工（锁工单后再锁已提交的入库单）的加锁顺序一致，避免两边互相等待
        MesWorkorder workorder = WorkorderFilters.lock(workorderMapper, stockIn.getWorkorderId());
        // 入库单提交之后工单可能已暂停或关闭：按新增时相同的要求（生产中、已完工）重新检查加锁读到的工单状态
        if (!CodeEnum.in(workorder.getWorkorderStatus(), STOCK_IN_WORKORDER_STATUSES)) {
            String current = CodeEnum.find(WorkorderStatus.class, workorder.getWorkorderStatus())
                .map(WorkorderStatus::label).orElse(workorder.getWorkorderStatus());
            throw new ServiceException("工单“" + workorder.getWorkorderCode() + "”当前为“" + current + "”，不能确认入库单“"
                + stockIn.getStockInCode() + "”，可以取消该单据");
        }
        MesStockIn change = new MesStockIn();
        change.setStockInStatus(StockInStatus.COMPLETED.code());
        change.setCompleteTime(LocalDateTime.now());
        change.setOperatorUserId(UserContext.userId());
        // 带原状态条件：重复或并发完成同一张入库单时只有一次成功，数量只累加一次
        transit(stockIn, change, "complete");
        // 条件累加（数据库中原子执行，不先读出再写回）：入库后的已入库数量不能超过完工数量（完工数量可能因报工撤销而减少）
        int rows = workorderMapper.update(null, Wrappers.<MesWorkorder>lambdaUpdate()
            .setSql("stocked_qty = stocked_qty + {0}", stockIn.getQty())
            .eq(MesWorkorder::getWorkorderId, stockIn.getWorkorderId())
            .apply("stocked_qty + {0} <= completed_qty", stockIn.getQty()));
        if (rows == 0) {
            throw new ServiceException("入库后工单的已入库数量将超过完工数量，请核对工单完工数量后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long stockInId) {
        MesStockIn stockIn = requireStockIn(stockInId);
        StateGuard.require(OBJECT, StockInStatus.class, stockIn.getStockInStatus(), "取消", StockInStatus.SUBMITTED);
        // 加锁顺序与入库完成相同：先工单、后入库单
        WorkorderFilters.lock(workorderMapper, stockIn.getWorkorderId());
        MesStockIn change = new MesStockIn();
        change.setStockInStatus(StockInStatus.CANCELLED.code());
        transit(stockIn, change, "cancel");
    }

    @Override
    public List<MesStatusLogVo> logs(Long stockInId) {
        requireStockIn(stockInId);
        return statusLogService.list(BizType.STOCK_IN, stockInId);
    }

    private void transit(MesStockIn stockIn, MesStockIn change, String action) {
        int rows = stockInMapper.update(change, Wrappers.<MesStockIn>lambdaUpdate()
            .eq(MesStockIn::getStockInId, stockIn.getStockInId())
            .eq(MesStockIn::getStockInStatus, stockIn.getStockInStatus()));
        if (rows == 0) {
            throw new ServiceException("入库单状态已变化，请刷新后重试");
        }
        statusLogService.record(BizType.STOCK_IN, stockIn.getStockInId(), stockIn.getStockInCode(), action,
            stockIn.getStockInStatus(), change.getStockInStatus(), null);
    }

    /**
     * 按 ID 取当前用户数据权限内的入库单，不存在或不在数据权限内时抛出业务异常
     */
    private MesStockIn requireStockIn(Long stockInId) {
        MesStockIn stockIn = stockInId == null ? null : stockInMapper.selectByIdInScope(stockInId);
        if (stockIn == null) {
            throw new ServiceException("入库单不存在");
        }
        return stockIn;
    }

}
