package org.tuoyu.mes.quality.domain;

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
 * 检验单 mes_inspection
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_inspection")
public class MesInspection extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "inspection_id")
    private Long inspectionId;

    /**
     * 检验单号
     */
    private String inspectionCode;

    /**
     * 检验类型（first 首检、patrol 巡检、final 完工检）
     */
    private String inspectionType;

    /**
     * 工单
     */
    private Long workorderId;

    /**
     * 工序任务（完工检可为空）
     */
    private Long taskId;

    /**
     * 检验方案
     */
    private Long schemeId;

    /**
     * 产品
     */
    private Long materialId;

    /**
     * 检验数量
     */
    private BigDecimal inspectQty;

    /**
     * 合格数量
     */
    private BigDecimal qualifiedQty;

    /**
     * 不良数量
     */
    private BigDecimal defectQty;

    /**
     * 检验员（用户）
     */
    private Long inspectorUserId;

    /**
     * 检验时间
     */
    private LocalDateTime inspectionTime;

    /**
     * 检验状态（pending 待检验、completed 已完成）
     */
    private String inspectionStatus;

    /**
     * 检验结论（passed 合格、rejected 不合格）
     */
    private String inspectionResult;

    /**
     * 检验说明
     */
    private String resultRemark;

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
