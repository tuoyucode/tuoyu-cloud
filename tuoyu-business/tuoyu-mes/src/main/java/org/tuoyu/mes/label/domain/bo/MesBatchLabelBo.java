package org.tuoyu.mes.label.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批次标签的打印范围：工单（每个工单一个批次）
 *
 * @author tuoyu
 */
@Data
public class MesBatchLabelBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "请选择要打印批次标签的工单")
    @Size(max = MesLabelLimits.MAX_LABELS, message = "一次最多打印 " + MesLabelLimits.MAX_LABELS + " 个标签")
    private List<Long> workorderIds;

}
