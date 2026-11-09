package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesRouteBo;
import org.tuoyu.mes.masterdata.domain.vo.MesRouteVo;

import java.util.Collection;
import java.util.List;

/**
 * 工艺路线
 *
 * @author tuoyu
 */
public interface IMesRouteService {

    /**
     * 查询工艺路线的表头与工序（按工序顺序），不存在时抛出业务异常
     */
    MesRouteVo queryById(Long routeId);

    PageResult<MesRouteVo> queryPageList(MesRouteBo bo, PageParam pageParam);

    List<MesRouteVo> queryList(MesRouteBo bo);

    long countList(MesRouteBo bo);

    /**
     * 启用的工艺路线（下拉选项），可按产品过滤；name 为“名称 版本”，默认版本在前，parentId 为产品 ID
     */
    List<MesOptionVo> options(Long materialId);

    /**
     * 新增工艺路线（表头与工序）。设为默认版本时，同一产品的其他工艺路线取消默认
     */
    void insertByBo(MesRouteBo bo);

    /**
     * 修改工艺路线：已被工单使用的工艺路线只能改表头，不能改工序与产品
     */
    void updateByBo(MesRouteBo bo);

    /**
     * 删除工艺路线：被工单引用的不能删除
     */
    void deleteByIds(Collection<Long> ids);

}
