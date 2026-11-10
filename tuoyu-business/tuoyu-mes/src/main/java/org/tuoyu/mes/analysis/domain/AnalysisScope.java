package org.tuoyu.mes.analysis.domain;

import lombok.Getter;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.mes.analysis.domain.bo.MesAnalysisQueryBo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 校验后的统计范围，作为聚合 SQL 的参数（{@code q}）。
 * <p>
 * 时间按半开区间 [开始日期 0 点, 结束日期次日 0 点) 比较，SQL 中只用 {@code >=} 与 {@code <}，两种数据库都能用上时间列的索引；
 * {@link #now} 是统计时刻，判断逾期等与“现在”有关的口径时使用，同一次统计中保持一致。
 *
 * @author tuoyu
 */
@Getter
public final class AnalysisScope {

    /**
     * 统计范围最长的天数（含两端）
     */
    public static final int MAX_DAYS = 366;

    private final LocalDate beginDate;

    private final LocalDate endDate;

    /**
     * 开始日期 0 点（包含）
     */
    private final LocalDateTime beginTime;

    /**
     * 结束日期次日 0 点（不包含）
     */
    private final LocalDateTime endTime;

    private final Long factoryId;

    private final Long workshopId;

    private final Long lineId;

    private final Long materialId;

    private final LocalDateTime now;

    /**
     * 直接给出范围（不做长度校验）。接口的查询条件用 {@link #of(MesAnalysisQueryBo, LocalDateTime)}
     */
    public AnalysisScope(LocalDate beginDate, LocalDate endDate, Long factoryId, Long workshopId, Long lineId,
                         Long materialId, LocalDateTime now) {
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.beginTime = beginDate.atStartOfDay();
        this.endTime = endDate.plusDays(1).atStartOfDay();
        this.factoryId = factoryId;
        this.workshopId = workshopId;
        this.lineId = lineId;
        this.materialId = materialId;
        this.now = now;
    }

    /**
     * 校验查询条件：日期必填、结束不早于开始、最长 {@value #MAX_DAYS} 天，不满足时抛出带原因的业务异常（400）
     */
    public static AnalysisScope of(MesAnalysisQueryBo bo, LocalDateTime now) {
        if (bo == null || bo.getBeginDate() == null || bo.getEndDate() == null) {
            throw new ServiceException("请选择统计的开始日期与结束日期");
        }
        if (bo.getEndDate().isBefore(bo.getBeginDate())) {
            throw new ServiceException("结束日期不能早于开始日期");
        }
        long days = ChronoUnit.DAYS.between(bo.getBeginDate(), bo.getEndDate()) + 1;
        if (days > MAX_DAYS) {
            throw new ServiceException("统计范围最长 " + MAX_DAYS + " 天（当前为 " + days + " 天），请缩小时间范围");
        }
        return new AnalysisScope(bo.getBeginDate(), bo.getEndDate(), bo.getFactoryId(), bo.getWorkshopId(),
            bo.getLineId(), bo.getMaterialId(), now);
    }

}
