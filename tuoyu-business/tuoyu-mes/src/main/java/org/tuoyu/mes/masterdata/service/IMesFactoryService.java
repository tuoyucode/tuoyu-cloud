package org.tuoyu.mes.masterdata.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesFactoryBo;
import org.tuoyu.mes.masterdata.domain.vo.MesFactoryVo;

import java.util.Collection;
import java.util.List;

/**
 * 工厂
 *
 * @author tuoyu
 */
public interface IMesFactoryService {

    /**
     * 查询工厂，不存在时抛出业务异常
     */
    MesFactoryVo queryById(Long factoryId);

    PageResult<MesFactoryVo> queryPageList(MesFactoryBo bo, PageParam pageParam);

    List<MesFactoryVo> queryList(MesFactoryBo bo);

    long countList(MesFactoryBo bo);

    /**
     * 启用的工厂（下拉选项）
     */
    List<MesOptionVo> options();

    void insertByBo(MesFactoryBo bo);

    void updateByBo(MesFactoryBo bo);

    /**
     * 删除工厂：还有车间、仓库或设备的工厂不能删除
     */
    void deleteByIds(Collection<Long> ids);

}
