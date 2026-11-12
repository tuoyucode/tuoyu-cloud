package org.tuoyu.mes.analysis.domain.row;

import lombok.Data;

import java.time.LocalDate;

/**
 * 停机事件按一个维度汇总的一行（聚合 SQL 的结果），只有所选维度对应的分组字段有值
 *
 * @author tuoyu
 */
@Data
public class DowntimeGroupRow {

    /**
     * 上报日期（按日期分组时）
     */
    private LocalDate eventDate;

    private Long reasonId;

    private Long lineId;

    /**
     * 停机事件数（含未解决的）
     */
    private Long eventCount;

    /**
     * 其中未解决的事件数（已上报、已派单、处理中）
     */
    private Long openCount;

    /**
     * 其中已解决的事件数（有解决时间），平均停机时长的分母
     */
    private Long resolvedCount;

    /**
     * 已解决事件的停机时长合计（分钟）
     */
    private Long downtimeMinutes;

}
