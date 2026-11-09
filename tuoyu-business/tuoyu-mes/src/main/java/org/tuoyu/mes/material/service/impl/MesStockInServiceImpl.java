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
import org.tuoyu.mes.material.support.SafeLookup;
import org.tuoyu.mes.material.support.Quantities;
import org.tuoyu.mes.material.support.WarehouseChecks;
import org.tuoyu.mes.material.support.WorkorderFilters;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.enums.WorkorderStatus;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 完工入库
 * <p>
 * 工单的已入库数量（stocked_qty）只在这里、入库完成时条件累加，其他代码不写这一列。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesStockInServiceImpl implements IMesStockInService {

    private static final String OBJECT = "入库单";

    private final MesStockInMapper stockInMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesWarehouseMapper warehouseMapper;
    private final MesLocationMapper locationMapper;
    private final MesMaterialMapper materialMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;

    @Override
    public MesStockInVo queryById(Long stockInId) {
        MesStockInVo vo = BeanMapper.map(requireStockIn(stockInId), MesStockInVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesStockInVo> queryPageList(MesStockInBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesStockIn> lqw = QueryBuilder.lambda(MesStockIn.class)
            .likeIfText(MesStockIn::getStockInCode, bo.getStockInCode())
            .eqIfText(MesStockIn::getStockInStatus, bo.getStockInStatus())
            .eqIfPresent(MesStockIn::getWorkorderId, bo.getWorkorderId())
            .inIfNotEmpty(MesStockIn::getWorkorderId, WorkorderFilters.idsByCode(workorderMapper, bo.getWorkorderCode()))
            .orderByDesc(!pageParam.hasSort(), MesStockIn::getStockInId)
            .build();
        PageResult<MesStockInVo> result = PageResults.of(
            stockInMapper.selectPage(pageParam.toPage(MesStockIn.class), lqw), MesStockInVo.class);
        fill(result.getItems());
        return result;
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
        MesWorkorder workorder = WorkorderFilters.require(workorderMapper, bo.getWorkorderId());
        StateGuard.require("工单", WorkorderStatus.class, workorder.getWorkorderStatus(), "入库",
            WorkorderStatus.IN_PROGRESS, WorkorderStatus.COMPLETED);
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
     * 工单已提交与已入库的入库数量合计
     */
    private BigDecimal activeQty(Long workorderId) {
        return stockInMapper.selectList(QueryBuilder.lambda(MesStockIn.class)
                .select(MesStockIn::getQty)
                .eq(MesStockIn::getWorkorderId, workorderId)
                .in(MesStockIn::getStockInStatus, StockInStatus.SUBMITTED.code(), StockInStatus.COMPLETED.code())
                .build())
            .stream().map(s -> Quantities.orZero(s.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long stockInId) {
        MesStockIn stockIn = requireStockIn(stockInId);
        StateGuard.require(OBJECT, StockInStatus.class, stockIn.getStockInStatus(), "入库", StockInStatus.SUBMITTED);
        MesStockIn change = new MesStockIn();
        change.setStockInStatus(StockInStatus.COMPLETED.code());
        change.setCompleteTime(LocalDateTime.now());
        change.setOperatorUserId(UserContext.userId());
        transit(stockIn, change, "complete");
        // 条件累加：入库后的已入库数量不能超过完工数量（完工数量可能因报工撤销而减少）
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

    private MesStockIn requireStockIn(Long stockInId) {
        MesStockIn stockIn = stockInId == null ? null : stockInMapper.selectById(stockInId);
        if (stockIn == null) {
            throw new ServiceException("入库单不存在");
        }
        return stockIn;
    }

}
