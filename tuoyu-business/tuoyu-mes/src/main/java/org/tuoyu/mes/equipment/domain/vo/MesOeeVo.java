package org.tuoyu.mes.equipment.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * OEE 的计算结果：口径相关的配置、汇总与按日趋势
 *
 * @author tuoyu
 */
@Data
public class MesOeeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象类型（device 设备、line 产线）
     */
    private String targetType;

    private Long targetId;

    /**
     * 设备或产线的编码与名称
     */
    private String targetName;

    private LocalDate beginDate;

    private LocalDate endDate;

    /**
     * 每日计划生产时间（分钟），未配置时为空
     */
    private Integer plannedMinutesPerDay;

    /**
     * 计划生产时间取自哪里（device 设备的配置、line 所属产线的配置），未配置时为空
     */
    private String plannedSource;

    /**
     * 配置的标准节拍（分钟/件），为空时按工序的单件标准工时计算
     */
    private BigDecimal cycleMinutes;

    /**
     * 产出取自哪里（station 设备所在工位的报工、line 产线的报工），设备没有关联工位与产线时为空
     */
    private String outputSource;

    /**
     * 产出来源的名称（工位或产线）
     */
    private String outputSourceName;

    private MesOeeStatVo summary;

    private List<MesOeeStatVo> days = new ArrayList<>();

}
