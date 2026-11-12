package org.tuoyu.mes.plan.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.plan.domain.bo.MesWorkorderBo;
import org.tuoyu.mes.plan.domain.vo.MesWorkorderVo;

import java.util.Collection;
import java.util.List;

/**
 * 生产工单：草稿的维护，以及下达、暂停、恢复、完工、关闭、取消等状态动作
 *
 * @author tuoyu
 */
public interface IMesWorkorderService {

    /**
     * 工单详情（含工序任务），不存在时抛出业务异常
     */
    MesWorkorderVo queryById(Long workorderId);

    PageResult<MesWorkorderVo> queryPageList(MesWorkorderBo bo, PageParam pageParam);

    List<MesWorkorderVo> queryList(MesWorkorderBo bo);

    long countList(MesWorkorderBo bo);

    /**
     * 工单下拉选项：按编号或批次号模糊匹配，可按状态过滤，最多 50 条，新建的在前
     *
     * @param keyword  编号或批次号
     * @param statuses 状态，多个用英文逗号分隔；为空时不过滤
     */
    List<MesOptionVo> options(String keyword, String statuses);

    /**
     * 状态履历
     */
    List<MesStatusLogVo> logs(Long workorderId);

    /**
     * 新增工单（草稿），编号自动生成
     */
    void insertByBo(MesWorkorderBo bo);

    /**
     * 修改工单，只允许草稿
     */
    void updateByBo(MesWorkorderBo bo);

    /**
     * 删除工单，只允许草稿
     */
    void deleteByIds(Collection<Long> ids);

    /**
     * 下达：草稿 → 已下达，生成批次号与工序任务
     */
    void release(Long workorderId);

    /**
     * 暂停：已下达、生产中 → 已暂停，原因必填
     */
    void pause(Long workorderId, String reason);

    /**
     * 恢复：已暂停 → 暂停前的状态
     */
    void resume(Long workorderId);

    /**
     * 完工：生产中 → 已完工，未完成的工序任务一并完成，已生成的序列号改为已完工
     */
    void complete(Long workorderId);

    /**
     * 关闭：已完工 → 已关闭，工序任务全部关闭
     */
    void close(Long workorderId, String reason);

    /**
     * 取消：草稿、已下达且没有报工 → 已取消，工序任务全部关闭
     */
    void cancel(Long workorderId, String reason);

    /**
     * 工序任务完成后调用：工单生产中且全部工序任务已完成时自动完工（不要求完工数量达到计划数量，
     * 有不良时末道的合格数会少于计划数），已生成的序列号一并改为已完工
     *
     * @return 是否已自动完工
     */
    boolean completeIfFinished(Long workorderId);

}
