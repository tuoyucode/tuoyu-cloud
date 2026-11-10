package org.tuoyu.mes.label.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.label.domain.MesLabelTemplate;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 标签模板视图对象 mes_label_template
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesLabelTemplate.class)
public class MesLabelTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long labelTemplateId;

    private String templateName;

    private String targetType;

    private Integer paperWidth;

    private Integer paperHeight;

    private Integer columnCount;

    private Integer columnGap;

    private String barcodeType;

    /**
     * 显示的字段（LabelField 的编码，按显示顺序）
     */
    private List<String> fields;

    private String defaultFlag;

    private String status;

    private String remark;

    private LocalDateTime createTime;

}
