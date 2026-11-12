package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.UniqueCodes;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.domain.bo.MesLocationBo;
import org.tuoyu.mes.masterdata.domain.bo.MesWarehouseBo;
import org.tuoyu.mes.masterdata.domain.vo.MesLocationVo;
import org.tuoyu.mes.masterdata.domain.vo.MesWarehouseVo;
import org.tuoyu.mes.masterdata.enums.WarehouseType;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.masterdata.service.IMesWarehouseService;
import org.tuoyu.mes.material.domain.MesMaterialRequest;
import org.tuoyu.mes.material.domain.MesMaterialRequestItem;
import org.tuoyu.mes.material.domain.MesStockIn;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestMapper;
import org.tuoyu.mes.material.mapper.MesStockInMapper;

import java.util.Collection;
import java.util.List;

/**
 * 仓库与库位
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesWarehouseServiceImpl implements IMesWarehouseService {

    private final MesWarehouseMapper warehouseMapper;
    private final MesLocationMapper locationMapper;
    private final MesFactoryMapper factoryMapper;
    private final MesMaterialRequestMapper requestMapper;
    private final MesMaterialRequestItemMapper requestItemMapper;
    private final MesStockInMapper stockInMapper;

    @Override
    public MesWarehouseVo queryById(Long warehouseId) {
        MesWarehouse warehouse = MasterdataSupport.require(warehouseMapper.selectById(warehouseId), "仓库不存在");
        MesWarehouseVo vo = BeanMapper.map(warehouse, MesWarehouseVo.class);
        fillWarehouseNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesWarehouseVo> queryPageList(MesWarehouseBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesWarehouse> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesWarehouseVo> result = PageResults.of(
            warehouseMapper.selectPage(pageParam.toPage(MesWarehouse.class), lqw), MesWarehouseVo.class);
        fillWarehouseNames(result.getItems());
        return result;
    }

    @Override
    public List<MesWarehouseVo> queryList(MesWarehouseBo bo) {
        List<MesWarehouseVo> list = BeanMapper.mapList(warehouseMapper.selectList(buildQueryWrapper(bo, true)),
            MesWarehouseVo.class);
        fillWarehouseNames(list);
        return list;
    }

    @Override
    public long countList(MesWarehouseBo bo) {
        return warehouseMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(Long factoryId, String warehouseType) {
        return warehouseMapper.selectList(QueryBuilder.lambda(MesWarehouse.class)
                .select(MesWarehouse::getWarehouseId, MesWarehouse::getWarehouseCode, MesWarehouse::getWarehouseName,
                    MesWarehouse::getFactoryId)
                .eq(MesWarehouse::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesWarehouse::getFactoryId, factoryId)
                .eqIfText(MesWarehouse::getWarehouseType, warehouseType)
                .orderByAsc(MesWarehouse::getSortNo, MesWarehouse::getWarehouseId)
                .build())
            .stream()
            .map(w -> new MesOptionVo(w.getWarehouseId(), w.getWarehouseCode(), w.getWarehouseName(),
                w.getFactoryId()))
            .toList();
    }

    private LambdaQueryWrapper<MesWarehouse> buildQueryWrapper(MesWarehouseBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesWarehouse.class)
            .eqIfPresent(MesWarehouse::getFactoryId, bo.getFactoryId())
            .likeIfText(MesWarehouse::getWarehouseCode, bo.getWarehouseCode())
            .likeIfText(MesWarehouse::getWarehouseName, bo.getWarehouseName())
            .eqIfText(MesWarehouse::getWarehouseType, bo.getWarehouseType())
            .eqIfText(MesWarehouse::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesWarehouse::getSortNo)
            .orderByAsc(defaultOrder, MesWarehouse::getWarehouseId)
            .build();
    }

    private void fillWarehouseNames(List<MesWarehouseVo> list) {
        MasterdataSupport.fill(list, MesWarehouseVo::getFactoryId, factoryMapper, MesFactory::getFactoryId,
            (vo, factory) -> vo.setFactoryName(factory.getFactoryName()));
    }

    @Override
    public void insertByBo(MesWarehouseBo bo) {
        checkWarehouseCodeUnique(bo.getWarehouseCode(), null);
        MasterdataSupport.require(factoryMapper.selectById(bo.getFactoryId()), "所属工厂不存在");
        MesWarehouse warehouse = BeanMapper.map(bo, MesWarehouse.class);
        warehouse.setWarehouseId(null);
        normalize(warehouse);
        UniqueCodes.write(() -> warehouseMapper.insert(warehouse), duplicateCode(warehouse.getWarehouseCode()));
        bo.setWarehouseId(warehouse.getWarehouseId());
    }

    @Override
    public void updateByBo(MesWarehouseBo bo) {
        checkWarehouseCodeUnique(bo.getWarehouseCode(), bo.getWarehouseId());
        MasterdataSupport.require(factoryMapper.selectById(bo.getFactoryId()), "所属工厂不存在");
        MesWarehouse warehouse = BeanMapper.map(bo, MesWarehouse.class);
        normalize(warehouse);
        if (UniqueCodes.write(() -> warehouseMapper.updateById(warehouse), duplicateCode(bo.getWarehouseCode())) == 0) {
            throw new ServiceException("修改仓库失败，仓库不存在");
        }
    }

    private static void normalize(MesWarehouse warehouse) {
        CodeEnum.of(WarehouseType.class, warehouse.getWarehouseType(), "仓库类型");
        warehouse.setStatus(MasterdataSupport.statusOrNormal(warehouse.getStatus()));
        warehouse.setSortNo(MasterdataSupport.sortOrZero(warehouse.getSortNo()));
    }

    private void checkWarehouseCodeUnique(String code, Long excludeId) {
        boolean exists = warehouseMapper.exists(QueryBuilder.lambda(MesWarehouse.class)
            .func(UniqueCodes.sameCode(MesWarehouse::getWarehouseCode, code))
            .neIfPresent(MesWarehouse::getWarehouseId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException(duplicateCode(code));
        }
    }

    private static String duplicateCode(String code) {
        return "仓库编码“" + code + "”已存在";
    }

    @Override
    public void deleteByIds(Collection<Long> ids) {
        if (locationMapper.exists(QueryBuilder.lambda(MesLocation.class).in(MesLocation::getWarehouseId, ids).build())) {
            throw new ServiceException("仓库下还有库位，不能删除");
        }
        if (requestMapper.exists(QueryBuilder.lambda(MesMaterialRequest.class)
            .in(MesMaterialRequest::getWarehouseId, ids).build())
            || stockInMapper.exists(QueryBuilder.lambda(MesStockIn.class).in(MesStockIn::getWarehouseId, ids).build())) {
            throw new ServiceException("仓库已有领退料或入库记录，不能删除，可以停用");
        }
        if (warehouseMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除仓库失败，仓库不存在");
        }
    }

    @Override
    public MesLocationVo queryLocationById(Long locationId) {
        MesLocation location = MasterdataSupport.require(locationMapper.selectById(locationId), "库位不存在");
        MesLocationVo vo = BeanMapper.map(location, MesLocationVo.class);
        fillLocationNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesLocationVo> queryLocationPageList(MesLocationBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesLocation> lqw = QueryBuilder.lambda(MesLocation.class)
            .eqIfPresent(MesLocation::getWarehouseId, bo.getWarehouseId())
            .likeIfText(MesLocation::getLocationCode, bo.getLocationCode())
            .likeIfText(MesLocation::getLocationName, bo.getLocationName())
            .eqIfText(MesLocation::getStatus, bo.getStatus())
            .orderByAsc(!pageParam.hasSort(), MesLocation::getSortNo)
            .orderByAsc(!pageParam.hasSort(), MesLocation::getLocationId)
            .build();
        PageResult<MesLocationVo> result = PageResults.of(
            locationMapper.selectPage(pageParam.toPage(MesLocation.class), lqw), MesLocationVo.class);
        fillLocationNames(result.getItems());
        return result;
    }

    @Override
    public List<MesOptionVo> locationOptions(Long warehouseId) {
        return locationMapper.selectList(QueryBuilder.lambda(MesLocation.class)
                .select(MesLocation::getLocationId, MesLocation::getLocationCode, MesLocation::getLocationName,
                    MesLocation::getWarehouseId)
                .eq(MesLocation::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesLocation::getWarehouseId, warehouseId)
                .orderByAsc(MesLocation::getSortNo, MesLocation::getLocationId)
                .build())
            .stream()
            .map(l -> new MesOptionVo(l.getLocationId(), l.getLocationCode(), l.getLocationName(),
                l.getWarehouseId()))
            .toList();
    }

    private void fillLocationNames(List<MesLocationVo> list) {
        MasterdataSupport.fill(list, MesLocationVo::getWarehouseId, warehouseMapper, MesWarehouse::getWarehouseId,
            (vo, warehouse) -> vo.setWarehouseName(warehouse.getWarehouseName()));
    }

    @Override
    public void insertLocation(MesLocationBo bo) {
        MasterdataSupport.require(warehouseMapper.selectById(bo.getWarehouseId()), "所属仓库不存在");
        checkLocationCodeUnique(bo.getWarehouseId(), bo.getLocationCode(), null);
        MesLocation location = BeanMapper.map(bo, MesLocation.class);
        location.setLocationId(null);
        location.setStatus(MasterdataSupport.statusOrNormal(location.getStatus()));
        location.setSortNo(MasterdataSupport.sortOrZero(location.getSortNo()));
        UniqueCodes.write(() -> locationMapper.insert(location), duplicateLocationCode(location.getLocationCode()));
        bo.setLocationId(location.getLocationId());
    }

    @Override
    public void updateLocation(MesLocationBo bo) {
        MasterdataSupport.require(warehouseMapper.selectById(bo.getWarehouseId()), "所属仓库不存在");
        checkLocationCodeUnique(bo.getWarehouseId(), bo.getLocationCode(), bo.getLocationId());
        MesLocation location = BeanMapper.map(bo, MesLocation.class);
        if (UniqueCodes.write(() -> locationMapper.updateById(location), duplicateLocationCode(bo.getLocationCode())) == 0) {
            throw new ServiceException("修改库位失败，库位不存在");
        }
    }

    /**
     * 库位编码在同一仓库内唯一（不同仓库常用相同的货架编号）
     */
    private void checkLocationCodeUnique(Long warehouseId, String code, Long excludeId) {
        boolean exists = locationMapper.exists(QueryBuilder.lambda(MesLocation.class)
            .eq(MesLocation::getWarehouseId, warehouseId)
            .func(UniqueCodes.sameCode(MesLocation::getLocationCode, code))
            .neIfPresent(MesLocation::getLocationId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException(duplicateLocationCode(code));
        }
    }

    private static String duplicateLocationCode(String code) {
        return "库位编码“" + code + "”在该仓库中已存在";
    }

    @Override
    public void deleteLocations(Collection<Long> ids) {
        if (requestItemMapper.exists(QueryBuilder.lambda(MesMaterialRequestItem.class)
            .in(MesMaterialRequestItem::getLocationId, ids).build())
            || stockInMapper.exists(QueryBuilder.lambda(MesStockIn.class).in(MesStockIn::getLocationId, ids).build())) {
            throw new ServiceException("库位已有领退料或入库记录，不能删除，可以停用");
        }
        if (locationMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除库位失败，库位不存在");
        }
    }

}
