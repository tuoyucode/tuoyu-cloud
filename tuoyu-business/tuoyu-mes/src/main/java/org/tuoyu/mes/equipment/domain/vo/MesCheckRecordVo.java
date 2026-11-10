package org.tuoyu.mes.equipment.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
import org.tuoyu.mes.equipment.domain.MesCheckRecord;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 点检记录视图对象 mes_check_record
 *
 * @author tuoyu
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = MesCheckRecord.class)
public class MesCheckRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long checkRecordId;

    @ExcelProperty(value = "点检单号")
    private String recordCode;

    private Long deviceId;

    @ExcelProperty(value = "设备编码")
    private String deviceCode;

    @ExcelProperty(value = "设备名称")
    private String deviceName;

    @ExcelProperty(value = "点检时间")
    private LocalDateTime checkTime;

    private Long checkerUserId;

    @ExcelProperty(value = "点检人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "checkerUserId")
    private String checkerName;

    @ExcelProperty(value = "点检结论", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "normal=正常,abnormal=异常")
    private String checkResult;

    @ExcelProperty(value = "不合格项数")
    private Integer abnormalCount;

    @ExcelProperty(value = "记录状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "completed=已提交,voided=已作废")
    private String recordStatus;

    @ExcelProperty(value = "已发起安灯", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "Y=是,N=否")
    private String andonRaised;

    @ExcelProperty(value = "发起安灯时间")
    private LocalDateTime andonTime;

    @ExcelProperty(value = "作废原因")
    private String voidReason;

    @ExcelProperty(value = "备注")
    private String remark;

    private LocalDateTime createTime;

    /**
     * 逐项结果（只在详情中返回）
     */
    private List<MesCheckRecordDetailVo> details;

}
