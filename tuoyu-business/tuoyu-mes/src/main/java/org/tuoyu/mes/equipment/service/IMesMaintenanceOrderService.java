package org.tuoyu.mes.equipment.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceCompleteBo;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceOrderBo;
import org.tuoyu.mes.equipment.domain.vo.MesMaintenanceOrderVo;

import java.util.List;

/**
 * 保养工单：按保养计划到期生成，待保养 → 保养中 → 已完成；待保养、保养中可以取消
 *
 * @author tuoyu
 */
public interface IMesMaintenanceOrderService {

    /**
     * 查询保养工单，不存在或不在数据权限内时抛出业务异常
     */
    MesMaintenanceOrderVo queryById(Long orderId);

    PageResult<MesMaintenanceOrderVo> queryPageList(MesMaintenanceOrderBo bo, PageParam pageParam);

    /**
     * 导出用的列表（保养人名称已填好）
     */
    List<MesMaintenanceOrderVo> queryList(MesMaintenanceOrderBo bo);

    long countList(MesMaintenanceOrderBo bo);

    /**
     * 开始保养：待保养 → 保养中，保养人为当前用户
     */
    void start(Long orderId);

    /**
     * 完成保养：保养中 → 已完成，填写实际内容、用时与更换的备件；
     * 计划的上次保养日期设为今天，下次到期日期为今天加周期
     */
    void complete(Long orderId, MesMaintenanceCompleteBo bo);

    /**
     * 取消：待保养、保养中 → 已取消，原因必填；跳过本周期，计划的下次到期日期为本单到期日期加周期
     */
    void cancel(Long orderId, String reason);

    List<MesStatusLogVo> logs(Long orderId);

}
