package org.tuoyu.mes.material.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.material.domain.MesMaterialRequest;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 领退料单视图对象 mes_material_request
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesMaterialRequest.class)
public class MesMaterialRequestVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long requestId;

    private String requestCode;

    /**
     * 类型（issue 领料、supplement 补料、return 退料）
     */
    private String requestType;

    private Long workorderId;

    private String workorderCode;

    /**
     * 工单的产品名称
     */
    private String productName;

    private Long warehouseId;

    private String warehouseName;

    /**
     * 单据状态（draft 草稿、submitted 已提交、completed 已完成、cancelled 已取消）
     */
    private String requestStatus;

    private LocalDateTime submitTime;

    private LocalDateTime completeTime;

    private Long operatorUserId;

    /**
     * 仓库执行人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorUserId")
    private String operatorUserName;

    private String remark;

    private LocalDateTime createTime;

    /**
     * 明细（只在详情中返回）
     */
    private List<MesMaterialRequestItemVo> items;

}
