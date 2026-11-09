package org.tuoyu.mes.trace.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;

/**
 * 产品序列号 mes_sn
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_sn")
public class MesSn extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "sn_id")
    private Long snId;

    /**
     * 序列号
     */
    private String snCode;

    /**
     * 工单
     */
    private Long workorderId;

    /**
     * 产品
     */
    private Long materialId;

    /**
     * 生产批次号
     */
    private String batchCode;

    /**
     * 状态（created 已生成、completed 已完工、scrapped 已报废）
     */
    private String snStatus;

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
