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
 * 不良记录 mes_defect_record
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_defect_record")
public class MesDefectRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "defect_id")
    private Long defectId;

    /**
     * 不良单号
     */
    private String defectCode;

    /**
     * 来源（report 报工、inspection 检验、manual 手工登记）
     */
    private String sourceType;

    /**
     * 来源单据
     */
    private Long sourceId;

    /**
     * 工单
     */
    private Long workorderId;

    /**
     * 工序任务
     */
    private Long taskId;

    /**
     * 产品
     */
    private Long materialId;

    /**
     * 不良数量
     */
    private BigDecimal defectQty;

    /**
     * 不良等级（minor 轻微、major 严重、critical 致命）
     */
    private String defectLevel;

    /**
     * 不良描述
     */
    private String description;

    /**
     * 处置方式（rework 返工、scrap 报废、concession 让步接收）
     */
    private String handleMethod;

    /**
     * 处置状态（pending 待处置、handled 已处置）
     */
    private String handleStatus;

    /**
     * 处置人（用户）
     */
    private Long handleUserId;

    /**
     * 处置时间
     */
    private LocalDateTime handleTime;

    /**
     * 处置说明
     */
    private String handleRemark;

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
