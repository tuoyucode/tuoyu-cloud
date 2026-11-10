package org.tuoyu.mes.equipment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.equipment.domain.MesCheckRecordDetail;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 点检记录明细视图对象 mes_check_record_detail
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesCheckRecordDetail.class)
public class MesCheckRecordDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long detailId;

    private Long checkItemId;

    private String itemName;

    private String checkMethod;

    private String checkStandard;

    private String resultType;

    private BigDecimal lowerLimit;

    private BigDecimal upperLimit;

    private String unit;

    private String mandatory;

    private BigDecimal measuredValue;

    /**
     * 单项结果（ok 合格、ng 不合格，为空表示未检）
     */
    private String itemResult;

    private Integer sortNo;

    private String remark;

}
