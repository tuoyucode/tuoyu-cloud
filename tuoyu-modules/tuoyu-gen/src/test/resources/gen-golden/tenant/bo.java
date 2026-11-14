package org.tuoyu.system.domain.bo;

import org.tuoyu.system.domain.VerifyDemo;
import io.github.linpeilie.annotations.AutoMapper;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import java.math.BigDecimal;

@Data
@AutoMapper(target = VerifyDemo.class, reverseConvertGenerate = false)
public class VerifyDemoBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long demoId;

    private String demoCode;

    private String demoName;

    private BigDecimal amount;

    private Integer demoSort;

    private String status;

    private String remark;

}
