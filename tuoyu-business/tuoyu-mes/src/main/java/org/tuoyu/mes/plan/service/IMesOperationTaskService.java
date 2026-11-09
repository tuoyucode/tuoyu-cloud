package org.tuoyu.mes.plan.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.plan.domain.bo.MesOperationTaskBo;
import org.tuoyu.mes.plan.domain.bo.MesTaskDispatchBo;
import org.tuoyu.mes.plan.domain.vo.MesOperationTaskVo;

import java.util.List;

/**
 * 工序任务：查询、派工、手动完成
 *
 * @author tuoyu
 */
public interface IMesOperationTaskService {

    /**
     * 工序任务详情，不存在时抛出业务异常
     */
    MesOperationTaskVo queryById(Long taskId);

    PageResult<MesOperationTaskVo> queryPageList(MesOperationTaskBo bo, PageParam pageParam);

    /**
     * 工单的工序任务选项，按工序顺序排列
     */
    List<MesOptionVo> options(Long workorderId);

    List<MesStatusLogVo> logs(Long taskId);

    /**
     * 派工：待派工、已派工 → 已派工
     */
    void dispatch(Long taskId, MesTaskDispatchBo bo);

    /**
     * 手动完成：生产中 → 已完成；末道工序完成时按完工数量判断工单是否自动完工
     */
    void complete(Long taskId);

    /**
     * 报工后调用：生产中的任务累计合格数达到计划数时自动完成；末道工序完成时按完工数量判断工单是否自动完工
     *
     * @return 任务是否已自动完成
     */
    boolean completeIfReached(Long taskId);

}
