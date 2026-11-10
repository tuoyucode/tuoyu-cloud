package org.tuoyu.mes.analysis.domain.row;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 不良记录按一个维度汇总的一行（聚合 SQL 的结果），只有所选维度对应的分组字段有值
 *
 * @author tuoyu
 */
@Data
public class DefectGroupRow {

    private String defectLevel;

    private String sourceType;

    private String handleMethod;

    private Long materialId;

    private String operationCode;

    private String operationName;

    private Long recordCount;

    private BigDecimal defectQty;

}
