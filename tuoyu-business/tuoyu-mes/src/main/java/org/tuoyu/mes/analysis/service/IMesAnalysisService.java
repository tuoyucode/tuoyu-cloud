package org.tuoyu.mes.analysis.service;

import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.analysis.domain.bo.MesAnalysisQueryBo;
import org.tuoyu.mes.analysis.domain.vo.MesDefectParetoVo;
import org.tuoyu.mes.analysis.domain.vo.MesDowntimeAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesProgressAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesProgressRowVo;
import org.tuoyu.mes.analysis.domain.vo.MesTeamAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesWipAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesWorkhourAnalysisVo;
import org.tuoyu.mes.analysis.domain.vo.MesYieldAnalysisVo;

import java.util.List;

/**
 * 分析报表。查询条件不合法（缺少日期、范围超过 366 天、不支持的维度）时抛出带原因的业务异常（400）；
 * 结果按当前用户的数据权限过滤（按单据的创建部门与创建者），租户由框架隔离。
 *
 * @author tuoyu
 */
public interface IMesAnalysisService {

    /**
     * 生产进度：汇总与工单明细（分页）
     */
    MesProgressAnalysisVo progress(MesAnalysisQueryBo bo, PageParam pageParam);

    /**
     * 生产进度的工单明细行数（导出前检查）
     */
    long countProgressRows(MesAnalysisQueryBo bo);

    /**
     * 生产进度的全部工单明细（导出）
     */
    List<MesProgressRowVo> progressRows(MesAnalysisQueryBo bo);

    /**
     * 在制品（统计时刻的快照）
     */
    MesWipAnalysisVo wip(MesAnalysisQueryBo bo);

    /**
     * 良率与直通率
     */
    MesYieldAnalysisVo yield(MesAnalysisQueryBo bo);

    /**
     * 不良柏拉图
     */
    MesDefectParetoVo defect(MesAnalysisQueryBo bo);

    /**
     * 停机分析
     */
    MesDowntimeAnalysisVo downtime(MesAnalysisQueryBo bo);

    /**
     * 工时
     */
    MesWorkhourAnalysisVo workhour(MesAnalysisQueryBo bo);

    /**
     * 班组绩效
     */
    MesTeamAnalysisVo team(MesAnalysisQueryBo bo);

}
