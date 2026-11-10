package org.tuoyu.mes.analysis.domain.row;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 没有填写实际工时、但有开始与结束时间的报工，工时由两者之差计算
 *
 * @author tuoyu
 */
@Data
public class ReportTimeRow {

    private Long userId;

    private Long teamId;

    private String operationCode;

    private String operationName;

    private Long materialId;

    private Long lineId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

}
