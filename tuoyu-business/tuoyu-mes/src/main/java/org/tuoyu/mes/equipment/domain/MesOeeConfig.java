package org.tuoyu.mes.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * OEE 配置（按设备或产线：每日计划生产时间与标准节拍） mes_oee_config
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_oee_config")
public class MesOeeConfig extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "config_id")
    private Long configId;

    /**
     * 对象类型（device 设备、line 产线）
     */
    private String targetType;

    /**
     * 设备或产线
     */
    private Long targetId;

    /**
     * 每日计划生产时间（分钟，为空表示未配置）
     */
    private Integer plannedMinutes;

    /**
     * 标准节拍（分钟/件，为空时取工序的单件标准工时）
     */
    private BigDecimal cycleMinutes;

    /**
     * 备注
     */
    private String remark;

}
