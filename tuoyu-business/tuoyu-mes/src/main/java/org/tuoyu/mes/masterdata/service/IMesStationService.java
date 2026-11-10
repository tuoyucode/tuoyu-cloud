package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesStationBo;
import org.tuoyu.mes.masterdata.domain.vo.MesStationVo;

import java.util.Collection;
import java.util.List;

/**
 * 工位
 *
 * @author tuoyu
 */
public interface IMesStationService {

    /**
     * 查询工位，不存在时抛出业务异常
     */
    MesStationVo queryById(Long stationId);

    PageResult<MesStationVo> queryPageList(MesStationBo bo, PageParam pageParam);

    List<MesStationVo> queryList(MesStationBo bo);

    long countList(MesStationBo bo);

    /**
     * 启用的工位（下拉选项），可按产线过滤；parentId 为产线 ID
     */
    List<MesOptionVo> options(Long lineId);

    /**
     * 新增工位：所属车间与工厂由产线带出
     */
    void insertByBo(MesStationBo bo);

    /**
     * 修改工位：工位上已有设备时不能更换产线
     */
    void updateByBo(MesStationBo bo);

    /**
     * 删除工位：还有设备的工位不能删除
     */
    void deleteByIds(Collection<Long> ids);

}
