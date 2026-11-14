package org.tuoyu.system.domain;

import org.tuoyu.common.mybatis.core.domain.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("verify_demo")
public class VerifyDemo extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "demo_id")
    private Long demoId;

    private String demoCode;

    private String demoName;

    private BigDecimal amount;

    private Integer demoSort;

    private String status;

    private String remark;

}
