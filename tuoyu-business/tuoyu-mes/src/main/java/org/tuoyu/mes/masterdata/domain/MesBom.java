package org.tuoyu.mes.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 物料清单 mes_bom
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_bom")
public class MesBom extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "bom_id")
    private Long bomId;

    /**
     * 产品（成品或半成品）
     */
    private Long materialId;

    /**
     * BOM 编码
     */
    private String bomCode;

    /**
     * 版本
     */
    private String bomVersion;

    /**
     * 基准数量（明细用量对应的产品数量）
     */
    private BigDecimal baseQty;

    /**
     * 是否默认版本（Y 是 N 否）
     */
    private String isDefault;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

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
