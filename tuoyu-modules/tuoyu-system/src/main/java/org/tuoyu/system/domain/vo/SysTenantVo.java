package org.tuoyu.system.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.tuoyu.system.domain.SysTenant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysTenant.class)
public class SysTenantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;

    private String tenantName;

    private String contactUser;

    private String contactPhone;

    private String domain;

    private Long packageId;

    private LocalDateTime expireTime;

    private Integer accountCount;

    private String status;

    private String remark;

    private LocalDateTime createTime;

}
