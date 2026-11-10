package org.tuoyu.mes.equipment.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenancePlanBo;
import org.tuoyu.mes.equipment.domain.vo.MesMaintenancePlanVo;

import java.util.Collection;

/**
 * 保养计划：设备、保养内容、周期、上次保养日期、下次到期日期与提前生成天数；到期（含提前天数）时生成保养工单
 *
 * @author tuoyu
 */
public interface IMesMaintenancePlanService {

    /**
     * 查询保养计划，不存在时抛出业务异常
     */
    MesMaintenancePlanVo queryById(Long planId);

    PageResult<MesMaintenancePlanVo> queryPageList(MesMaintenancePlanBo bo, PageParam pageParam);

    /**
     * 新增：下次到期日期为空时按上次保养日期加周期计算，没有上次保养日期时为今天
     */
    void insertByBo(MesMaintenancePlanBo bo);

    /**
     * 修改：有未完成的保养工单时不能改下次到期日期
     */
    void updateByBo(MesMaintenancePlanBo bo);

    /**
     * 删除：有未完成的保养工单时拒绝
     */
    void deleteByIds(Collection<Long> ids);

    /**
     * 为当前租户中已到生成日期的启用计划生成保养工单
     *
     * @return 生成的工单数
     */
    int generateDue();

}
