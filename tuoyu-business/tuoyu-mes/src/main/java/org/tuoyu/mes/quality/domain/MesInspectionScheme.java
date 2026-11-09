package org.tuoyu.mes.quality.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 检验方案 mes_inspection_scheme
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_inspection_scheme")
public class MesInspectionScheme extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "scheme_id")
    private Long schemeId;

    /**
     * 方案编码
     */
    private String schemeCode;

    /**
     * 方案名称
     */
    private String schemeName;

    /**
     * 检验类型（first 首检、patrol 巡检、final 完工检）
     */
    private String inspectionType;

    /**
     * 适用产品（为空表示通用）
     */
    private Long materialId;

    /**
     * 适用工序编码（为空表示不限）
     */
    private String operationCode;

    /**
     * 抽样数量
     */
    private BigDecimal sampleQty;

    /**
     * 检验项目与标准（每行一项）
     */
    private String checkItems;

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
