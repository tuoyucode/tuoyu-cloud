package org.tuoyu.mes.material.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
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
@ExcelIgnoreUnannotated
@AutoMapper(target = MesMaterialRequest.class)
public class MesMaterialRequestVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long requestId;

    @ExcelProperty(value = "单号")
    private String requestCode;

    /**
     * 类型（issue 领料、supplement 补料、return 退料）
     */
    @ExcelProperty(value = "类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "issue=领料,supplement=补料,return=退料")
    private String requestType;

    private Long workorderId;

    @ExcelProperty(value = "工单编号")
    private String workorderCode;

    /**
     * 工单的产品名称
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

    private Long warehouseId;

    @ExcelProperty(value = "仓库")
    private String warehouseName;

    /**
     * 单据状态（draft 草稿、approving 审批中、submitted 已提交、completed 已完成、cancelled 已取消）
     */
    @ExcelProperty(value = "单据状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "draft=草稿,approving=审批中,submitted=已提交,completed=已完成,cancelled=已取消")
    private String requestStatus;

    @ExcelProperty(value = "提交时间")
    private LocalDateTime submitTime;

    @ExcelProperty(value = "完成时间")
    private LocalDateTime completeTime;

    private Long operatorUserId;

    /**
     * 仓库执行人名称
     */
    @ExcelProperty(value = "仓库执行人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorUserId")
    private String operatorUserName;

    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 当前（最近一次）审批的业务 ID，用于查看审批记录与撤销审批；没有审批过时为空
     */
    private String approvalBusinessId;

    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;

    /**
     * 明细（只在详情中返回）
     */
    private List<MesMaterialRequestItemVo> items;

}
