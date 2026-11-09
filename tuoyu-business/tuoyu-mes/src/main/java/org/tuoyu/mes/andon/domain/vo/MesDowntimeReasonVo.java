package org.tuoyu.mes.andon.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.mes.andon.domain.MesDowntimeReason;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 停机原因视图对象 mes_downtime_reason
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesDowntimeReason.class)
public class MesDowntimeReasonVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long reasonId;

    private String reasonCode;

    private String reasonName;

    private String reasonType;

    private String status;

    private Integer sortNo;

    private String remark;

    private LocalDateTime createTime;

}
