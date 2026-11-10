package org.tuoyu.mes.equipment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.equipment.domain.MesCheckItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点检项目视图对象 mes_check_item
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesCheckItem.class)
public class MesCheckItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long checkItemId;

    private String itemName;

    private Long deviceId;

    private String deviceCode;

    private String deviceName;

    private String deviceType;

    private String checkMethod;

    private String checkStandard;

    /**
     * 结果类型（judge 判定、numeric 数值）
     */
    private String resultType;

    private BigDecimal lowerLimit;

    private BigDecimal upperLimit;

    private String unit;

    /**
     * 是否必检（Y 是 N 否）
     */
    private String mandatory;

    private Integer sortNo;

    /**
     * 状态（0 正常 1 停用）
     */
    private String status;

    private String remark;

    private LocalDateTime createTime;

}
