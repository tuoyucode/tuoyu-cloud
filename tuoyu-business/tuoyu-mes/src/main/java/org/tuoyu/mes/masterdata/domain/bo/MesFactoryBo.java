package org.tuoyu.mes.masterdata.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.mes.masterdata.domain.MesFactory;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工厂业务对象 mes_factory
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = MesFactory.class, reverseConvertGenerate = false)
public class MesFactoryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "工厂 ID 不能为空", groups = OnUpdate.class)
    private Long factoryId;

    /**
     * 工厂编码
     */
    @NotBlank(message = "工厂编码不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 64, message = "工厂编码不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String factoryCode;

    /**
     * 工厂名称
     */
    @NotBlank(message = "工厂名称不能为空", groups = {OnCreate.class, OnUpdate.class})
    @Size(max = 128, message = "工厂名称不能超过 128 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String factoryName;

    @Size(max = 64, message = "工厂简称不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String shortName;

    @Size(max = 255, message = "地址不能超过 255 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String address;

    @Size(max = 64, message = "联系人不能超过 64 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String contact;

    @Size(max = 32, message = "联系电话不能超过 32 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String contactPhone;

    /**
     * 状态（0 正常 1 停用）
     */
    @Pattern(regexp = "[01]", message = "状态只能是 0 或 1", groups = {OnCreate.class, OnUpdate.class})
    private String status;

    private Integer sortNo;

    @Size(max = 500, message = "备注不能超过 500 个字符", groups = {OnCreate.class, OnUpdate.class})
    private String remark;

}
