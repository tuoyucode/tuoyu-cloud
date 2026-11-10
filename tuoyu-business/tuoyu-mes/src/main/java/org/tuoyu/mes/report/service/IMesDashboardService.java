package org.tuoyu.mes.report.service;

import org.tuoyu.mes.report.domain.vo.MesDashboardVo;

/**
 * 生产看板（只读汇总）
 *
 * @author tuoyu
 */
public interface IMesDashboardService {

    /**
     * 生产看板
     *
     * @param workshopId 车间，为空表示全部
     * @param days       统计最近几天（含今天，1 ~ 90），为空时为 7
     * @return 看板数据
     */
    MesDashboardVo overview(Long workshopId, Integer days);

}
