package org.tuoyu.mes.material.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 完工入库单 mes_stock_in
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_stock_in")
public class MesStockIn extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "stock_in_id")
    private Long stockInId;

    /**
     * 入库单号
     */
    private String stockInCode;

    /**
     * 工单
     */
    private Long workorderId;

    /**
     * 产品
     */
    private Long materialId;

    /**
     * 仓库
     */
    private Long warehouseId;

    /**
     * 库位
     */
    private Long locationId;

    /**
     * 入库数量
     */
    private BigDecimal qty;

    /**
     * 批次号
     */
    private String batchCode;

    /**
     * 单据状态（submitted 已提交、completed 已入库、cancelled 已取消）
     */
    private String stockInStatus;

    /**
     * 入库时间
     */
    private LocalDateTime completeTime;

    /**
     * 仓库执行人（用户）
     */
    private Long operatorUserId;

    /**
     * 删除标志（0 存在 1 删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
