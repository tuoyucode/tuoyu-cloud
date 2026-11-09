package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesLocationBo;
import org.tuoyu.mes.masterdata.domain.bo.MesWarehouseBo;
import org.tuoyu.mes.masterdata.domain.vo.MesLocationVo;
import org.tuoyu.mes.masterdata.domain.vo.MesWarehouseVo;

import java.util.Collection;
import java.util.List;

/**
 * 仓库与库位
 *
 * @author tuoyu
 */
public interface IMesWarehouseService {

    /**
     * 查询仓库，不存在时抛出业务异常
     */
    MesWarehouseVo queryById(Long warehouseId);

    PageResult<MesWarehouseVo> queryPageList(MesWarehouseBo bo, PageParam pageParam);

    List<MesWarehouseVo> queryList(MesWarehouseBo bo);

    long countList(MesWarehouseBo bo);

    /**
     * 启用的仓库（下拉选项），可按工厂与仓库类型过滤；parentId 为工厂 ID
     */
    List<MesOptionVo> options(Long factoryId, String warehouseType);

    void insertByBo(MesWarehouseBo bo);

    void updateByBo(MesWarehouseBo bo);

    /**
     * 删除仓库：还有库位或已有领退料、入库记录的仓库不能删除
     */
    void deleteByIds(Collection<Long> ids);

    /**
     * 查询库位，不存在时抛出业务异常
     */
    MesLocationVo queryLocationById(Long locationId);

    /**
     * 库位列表（通常按仓库查询）
     */
    PageResult<MesLocationVo> queryLocationPageList(MesLocationBo bo, PageParam pageParam);

    /**
     * 启用的库位（下拉选项），可按仓库过滤；parentId 为仓库 ID
     */
    List<MesOptionVo> locationOptions(Long warehouseId);

    /**
     * 新增库位：编码在同一仓库内唯一
     */
    void insertLocation(MesLocationBo bo);

    void updateLocation(MesLocationBo bo);

    /**
     * 删除库位：已有领退料、入库记录的库位不能删除
     */
    void deleteLocations(Collection<Long> ids);

}
