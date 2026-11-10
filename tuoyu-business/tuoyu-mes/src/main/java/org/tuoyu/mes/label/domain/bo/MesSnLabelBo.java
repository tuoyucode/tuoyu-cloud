package org.tuoyu.mes.label.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 序列号标签的打印范围：勾选的序列号，或某个工单的全部未报废序列号（二选一）
 *
 * @author tuoyu
 */
@Data
public class MesSnLabelBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 勾选的序列号（按勾选顺序打印）
     */
    @Size(max = MesLabelLimits.MAX_LABELS, message = "一次最多打印 " + MesLabelLimits.MAX_LABELS + " 个标签")
    private List<Long> snIds;

    /**
     * 按工单打印：该工单的全部未报废序列号（按序列号排序）
     */
    private Long workorderId;

}
