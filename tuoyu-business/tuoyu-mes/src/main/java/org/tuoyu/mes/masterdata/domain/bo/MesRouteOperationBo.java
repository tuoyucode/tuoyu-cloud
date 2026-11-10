package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesRouteOperation;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺路线工序 mes_route_operation
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesRouteOperation.class, reverseConvertGenerate = false)
public class MesRouteOperationBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工序顺序（大于 0，按升序执行，同一路线内不重复）
     */
    @NotNull(message = "工序顺序不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Min(value = 1, message = "工序顺序必须大于 0", groups = {OnCreate.class, OnUpdate.class})
    private Integer seqNo;

    @NotBlank(message = "工序编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "工序编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String operationCode;

    @NotBlank(message = "工序名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "工序名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String operationName;

    /**
     * 建议工位类型
     */
    @Size(max = 32, message = "工位类型不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String stationType;

    /**
     * 单件标准工时（分钟），不填时为 0
     */
    @DecimalMin(value = "0", message = "标准工时不能小于 0", groups = {OnCreate.class, OnUpdate.class})
    private BigDecimal standardMinutes;

    /**
     * 是否需要首检合格后才能批量报工（Y 是 N 否），不填时为 N
     */
    @Pattern(regexp = "[YN]", message = "是否首检只能是 Y 或 N", groups = {OnCreate.class, OnUpdate.class})
    private String firstInspection;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
