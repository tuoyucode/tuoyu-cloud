package org.tuoyu.mes.material.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 领退料单 mes_material_request
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_material_request")
public class MesMaterialRequest extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "request_id")
    private Long requestId;

    /**
     * 单号
     */
    private String requestCode;

    /**
     * 类型（issue 领料、supplement 补料、return 退料）
     */
    private String requestType;

    /**
     * 工单
     */
    private Long workorderId;

    /**
     * 仓库
     */
    private Long warehouseId;

    /**
     * 单据状态（draft 草稿、approving 审批中、submitted 已提交、completed 已完成、cancelled 已取消）
     */
    private String requestStatus;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 完成时间
     */
    private LocalDateTime completeTime;

    /**
     * 仓库执行人（用户）
     */
    private Long operatorUserId;

    /**
     * 当前（最近一次）审批的业务 ID，没有审批过时为空
     */
    private String approvalBusinessId;

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
