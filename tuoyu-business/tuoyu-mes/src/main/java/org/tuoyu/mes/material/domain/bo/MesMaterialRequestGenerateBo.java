package org.tuoyu.mes.material.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 按物料清单生成领料单的参数
 *
 * @author tuoyu
 */
@Data
public class MesMaterialRequestGenerateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工单不能为空")
    private Long workorderId;

    @NotNull(message = "仓库不能为空")
    private Long warehouseId;

}
