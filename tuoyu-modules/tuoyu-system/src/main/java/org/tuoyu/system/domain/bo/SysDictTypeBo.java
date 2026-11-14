package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.common.core.constant.RegexConstants;
import org.tuoyu.system.domain.SysDictType;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 字典类型请求对象（新增、修改、列表条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysDictType.class, reverseConvertGenerate = false)
public class SysDictTypeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 字典主键
     */
    private Long dictId;

    /**
     * 字典名称
     */
    @NotBlank(message = "字典名称不能为空")
    @Size(min = 0, max = 100, message = "字典类型名称长度不能超过{max}个字符")
    private String dictName;

    /**
     * 字典类型编码：小写字母开头，只含小写字母、数字、下划线
     */
    @NotBlank(message = "字典类型不能为空")
    @Size(min = 0, max = 100, message = "字典类型类型长度不能超过{max}个字符")
    @Pattern(regexp = RegexConstants.DICTIONARY_TYPE, message = "字典类型必须以字母开头，且只能为（小写字母，数字，下滑线）")
    private String dictType;

    /**
     * 备注
     */
    private String remark;

    /**
     * 请求参数（创建时间区间 beginTime、endTime）
     */
    private Map<String, Object> params = new HashMap<>();

}
