package org.tuoyu.mes.material.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.material.domain.MesStockIn;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 完工入库单视图对象 mes_stock_in
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesStockIn.class)
public class MesStockInVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long stockInId;

    private String stockInCode;

    private Long workorderId;

    private String workorderCode;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private String unit;

    private Long warehouseId;

    private String warehouseName;

    private Long locationId;

    private String locationName;

    private BigDecimal qty;

    private String batchCode;

    /**
     * 单据状态（submitted 已提交、completed 已入库、cancelled 已取消）
     */
    private String stockInStatus;

    private LocalDateTime completeTime;

    private Long operatorUserId;

    /**
     * 仓库执行人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorUserId")
    private String operatorUserName;

    private String remark;

    private LocalDateTime createTime;

}
