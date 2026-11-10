package org.tuoyu.mes.equipment.domain.bo;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 点检的单项结果：判定型填写合格或不合格，数值型填写测量值（按上下限自动判定）。
 * 非必检项可以不填，此时记为未检
 *
 * @author tuoyu
 */
@Data
public class MesCheckRecordDetailBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "点检项目不能为空", groups = OnCreate.class)
    private Long checkItemId;

    /**
     * 判定结果（ok 合格、ng 不合格），判定型项目使用
     */
    private String itemResult;

    /**
     * 测量值，数值型项目使用
     */
    @Digits(integer = 12, fraction = 6, message = "测量值最多 12 位整数、6 位小数", groups = OnCreate.class)
    private BigDecimal measuredValue;

    @Size(max = 255, message = "说明不能超过 255 个字符", groups = OnCreate.class)
    private String remark;

}
