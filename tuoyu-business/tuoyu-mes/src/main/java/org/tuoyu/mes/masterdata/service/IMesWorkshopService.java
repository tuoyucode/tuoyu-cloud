package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesWorkshopBo;
import org.tuoyu.mes.masterdata.domain.vo.MesWorkshopVo;

import java.util.Collection;
import java.util.List;

/**
 * 车间
 *
 * @author tuoyu
 */
public interface IMesWorkshopService {

    /**
     * 查询车间，不存在时抛出业务异常
     */
    MesWorkshopVo queryById(Long workshopId);

    PageResult<MesWorkshopVo> queryPageList(MesWorkshopBo bo, PageParam pageParam);

    List<MesWorkshopVo> queryList(MesWorkshopBo bo);

    long countList(MesWorkshopBo bo);

    /**
     * 启用的车间（下拉选项），可按工厂过滤；parentId 为工厂 ID
     */
    List<MesOptionVo> options(Long factoryId);

    /**
     * 新增车间：所属工厂必须存在
     */
    void insertByBo(MesWorkshopBo bo);

    /**
     * 修改车间：车间下已有产线、班组或设备时不能更换工厂
     */
    void updateByBo(MesWorkshopBo bo);

    /**
     * 删除车间：还有产线、班组、设备或工单的车间不能删除
     */
    void deleteByIds(Collection<Long> ids);

}
