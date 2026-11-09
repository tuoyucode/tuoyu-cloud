package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesLineBo;
import org.tuoyu.mes.masterdata.domain.vo.MesLineVo;

import java.util.Collection;
import java.util.List;

/**
 * 产线
 *
 * @author tuoyu
 */
public interface IMesLineService {

    /**
     * 查询产线，不存在时抛出业务异常
     */
    MesLineVo queryById(Long lineId);

    PageResult<MesLineVo> queryPageList(MesLineBo bo, PageParam pageParam);

    List<MesLineVo> queryList(MesLineBo bo);

    long countList(MesLineBo bo);

    /**
     * 启用的产线（下拉选项），可按车间过滤；parentId 为车间 ID
     */
    List<MesOptionVo> options(Long workshopId);

    /**
     * 新增产线：所属工厂由车间带出
     */
    void insertByBo(MesLineBo bo);

    /**
     * 修改产线：产线下已有工位或设备时不能更换车间
     */
    void updateByBo(MesLineBo bo);

    /**
     * 删除产线：还有工位、设备或工单的产线不能删除
     */
    void deleteByIds(Collection<Long> ids);

}
