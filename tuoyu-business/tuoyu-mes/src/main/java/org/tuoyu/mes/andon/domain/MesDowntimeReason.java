package org.tuoyu.mes.andon.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 停机原因 mes_downtime_reason
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_downtime_reason")
public class MesDowntimeReason extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "reason_id")
    private Long reasonId;

    /**
     * 原因编码
     */
    private String reasonCode;

    /**
     * 原因名称
     */
    private String reasonName;

    /**
     * 原因分类（equipment 设备、material 物料、quality 质量、personnel 人员、other 其他）
     */
    private String reasonType;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 排序号
     */
    private Integer sortNo;

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
