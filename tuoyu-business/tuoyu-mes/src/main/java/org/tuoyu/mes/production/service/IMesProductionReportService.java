package org.tuoyu.mes.production.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.production.domain.bo.MesProductionReportBo;
import org.tuoyu.mes.production.domain.vo.MesProductionReportVo;

import java.util.List;

/**
 * 报工：提交、确认、撤销，并联动工序任务与工单的数量与状态
 *
 * @author tuoyu
 */
public interface IMesProductionReportService {

    /**
     * 报工详情，不存在时抛出业务异常
     */
    MesProductionReportVo queryById(Long reportId);

    PageResult<MesProductionReportVo> queryPageList(MesProductionReportBo bo, PageParam pageParam);

    List<MesProductionReportVo> queryList(MesProductionReportBo bo);

    long countList(MesProductionReportBo bo);

    List<MesStatusLogVo> logs(Long reportId);

    /**
     * 提交报工：校验工单与任务状态、数量上限与首检，累加任务与工单数量，必要时自动完成任务与工单；
     * 有不良数时登记一条待处置的不良记录
     */
    void submit(MesProductionReportBo bo);

    /**
     * 确认：已提交 → 已确认，确认后不能撤销
     */
    void confirm(Long reportId);

    /**
     * 撤销：已提交 → 已撤销，扣回任务与工单数量，删除该报工产生的待处置不良记录
     */
    void cancel(Long reportId, String reason);

}
