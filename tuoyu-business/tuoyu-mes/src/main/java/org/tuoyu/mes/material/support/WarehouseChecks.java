package org.tuoyu.mes.material.support;

import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * 领退料、入库共用的仓库与库位校验
 *
 * @author tuoyu
 */
public final class WarehouseChecks {

    private WarehouseChecks() {
    }

    /**
     * 仓库必须存在且启用
     */
    public static MesWarehouse requireEnabled(MesWarehouseMapper mapper, Long warehouseId) {
        MesWarehouse warehouse = warehouseId == null ? null : mapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new ServiceException("仓库不存在");
        }
        if (!SystemConstants.NORMAL.equals(warehouse.getStatus())) {
            throw new ServiceException("仓库“" + warehouse.getWarehouseName() + "”已停用");
        }
        return warehouse;
    }

    /**
     * 库位必须存在且属于给定的仓库（为空的库位忽略）
     */
    public static void checkLocations(MesLocationMapper mapper, MesWarehouse warehouse, Collection<Long> locationIds) {
        Map<Long, MesLocation> locations = MesLookups.byIds(mapper, locationIds, MesLocation::getLocationId);
        for (Long locationId : locationIds) {
            if (locationId == null) {
                continue;
            }
            MesLocation location = locations.get(locationId);
            if (location == null) {
                throw new ServiceException("库位不存在");
            }
            if (!Objects.equals(location.getWarehouseId(), warehouse.getWarehouseId())) {
                throw new ServiceException("库位“" + location.getLocationName() + "”不属于仓库“" + warehouse.getWarehouseName() + "”");
            }
        }
    }

}
