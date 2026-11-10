package org.tuoyu.mes.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 点检记录明细（逐项结果，保存提交时的项目快照） mes_check_record_detail
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_check_record_detail")
public class MesCheckRecordDetail extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "detail_id")
    private Long detailId;

    /**
     * 点检记录
     */
    private Long checkRecordId;

    /**
     * 点检项目
     */
    private Long checkItemId;

    /**
     * 项目名称
     */
    private String itemName;

    /**
     * 点检方法
     */
    private String checkMethod;

    /**
     * 点检标准
     */
    private String checkStandard;

    /**
     * 结果类型（judge、numeric）
     */
    private String resultType;

    /**
     * 下限
     */
    private BigDecimal lowerLimit;

    /**
     * 上限
     */
    private BigDecimal upperLimit;

    /**
     * 单位
     */
    private String unit;

    /**
     * 是否必检（Y 是 N 否）
     */
    private String mandatory;

    /**
     * 测量值（数值型）
     */
    private BigDecimal measuredValue;

    /**
     * 单项结果（ok 合格、ng 不合格，为空表示未检）
     */
    private String itemResult;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 说明
     */
    private String remark;

}
