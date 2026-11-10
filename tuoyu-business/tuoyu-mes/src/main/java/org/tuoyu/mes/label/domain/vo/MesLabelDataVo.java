package org.tuoyu.mes.label.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 一个标签的打印数据：条码内容与可以显示的字段（属性名即 LabelField 的编码）
 *
 * @author tuoyu
 */
@Data
public class MesLabelDataVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签对应的对象：序列号标签为序列号 ID，批次标签为工单 ID
     */
    private Long id;

    /**
     * 条码内容：序列号标签为序列号，批次标签为批次号
     */
    private String barcode;

    private String snCode;

    private String batchCode;

    private String workorderCode;

    private String materialCode;

    private String materialName;

    private String specification;

    /**
     * 数量（批次标签为工单的计划数）
     */
    private BigDecimal quantity;

    /**
     * 数量的单位
     */
    private String unit;

    /**
     * 生产日期：工单的实际开工日期，未开工时为打印当天
     */
    private LocalDate productionDate;

}
