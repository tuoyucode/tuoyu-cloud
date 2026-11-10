package org.tuoyu.mes.equipment.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 点检项目 mes_check_item
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_check_item")
public class MesCheckItem extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "check_item_id")
    private Long checkItemId;

    /**
     * 项目名称
     */
    private String itemName;

    /**
     * 适用设备（与适用设备类型二选一）
     */
    private Long deviceId;

    /**
     * 适用设备类型（适用设备为空时按设备类型匹配）
     */
    private String deviceType;

    /**
     * 点检方法
     */
    private String checkMethod;

    /**
     * 点检标准
     */
    private String checkStandard;

    /**
     * 结果类型（judge 判定、numeric 数值）
     */
    private String resultType;

    /**
     * 下限（数值型）
     */
    private BigDecimal lowerLimit;

    /**
     * 上限（数值型）
     */
    private BigDecimal upperLimit;

    /**
     * 单位（数值型）
     */
    private String unit;

    /**
     * 是否必检（Y 是 N 否）
     */
    private String mandatory;

    /**
     * 排序号
     */
    private Integer sortNo;

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
