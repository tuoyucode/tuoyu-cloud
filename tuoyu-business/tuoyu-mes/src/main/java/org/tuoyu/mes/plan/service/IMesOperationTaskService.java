package org.tuoyu.mes.plan.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.plan.domain.MesOperationTask;
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
     * 手动完成：生产中 → 已完成（工单暂停时不能完成）；完成后检查下一道工序能否自动完成，全部工序完成时工单自动完工
     */
    void complete(Long taskId);

    /**
     * 报工、撤销报工之后调用：上一道工序已完成（首道不要求），且本道已报数量（合格 + 不良）不少于上一道的合格数
     * （首道为工单计划数）时自动完成；完成后依次检查下一道工序，全部工序完成时工单自动完工。
     * 按 工单 → 工序任务 的顺序加锁，调用方已持有这些锁时不再等待
     *
     * @return 任务是否已自动完成
     */
    boolean completeIfReached(Long taskId);

    /**
     * 当前用户能否对该任务报工：任务派给当前用户或当前用户所在的班组（我的任务），或任务在当前用户的数据权限内
     */
    boolean reportable(MesOperationTask task);

}
