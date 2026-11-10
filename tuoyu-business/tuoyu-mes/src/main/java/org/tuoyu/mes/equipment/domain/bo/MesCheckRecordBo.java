package org.tuoyu.mes.equipment.domain.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.tuoyu.common.core.validate.OnCreate;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 点检记录业务对象 mes_check_record：提交点检时的字段与列表的查询条件
 *
 * @author tuoyu
 */
@Data
public class MesCheckRecordBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "设备不能为空", groups = OnCreate.class)
    private Long deviceId;

    /**
     * 逐项结果
     */
    @Valid
    @NotEmpty(message = "请填写点检结果", groups = OnCreate.class)
    @Size(max = 200, message = "点检项目不能超过 200 项", groups = OnCreate.class)
    private List<MesCheckRecordDetailBo> details;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = OnCreate.class)
    private String remark;

    /**
     * 点检单号（查询条件，模糊）
     */
    private String recordCode;

    /**
     * 点检结论（查询条件）
     */
    private String checkResult;

    /**
     * 记录状态（查询条件）
     */
    private String recordStatus;

    /**
     * 点检时间的起点（查询条件）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginCheckTime;

    /**
     * 点检时间的终点（查询条件）
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endCheckTime;

}
