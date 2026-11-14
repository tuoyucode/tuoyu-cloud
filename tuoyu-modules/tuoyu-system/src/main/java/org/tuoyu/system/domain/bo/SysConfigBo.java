package org.tuoyu.system.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.tuoyu.system.domain.SysConfig;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 参数配置请求对象（新增、修改、按键修改、列表条件）
 *
 * @author tuoyu
 */
@Data
@AutoMapper(target = SysConfig.class, reverseConvertGenerate = false)
public class SysConfigBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 参数主键
     */
    private Long configId;

    /**
     * 参数名称
     */
    @NotBlank(message = "参数名称不能为空")
    @Size(min = 0, max = 100, message = "参数名称不能超过{max}个字符")
    private String configName;

    /**
     * 参数键名
     */
    @NotBlank(message = "参数键名不能为空")
    @Size(min = 0, max = 100, message = "参数键名长度不能超过{max}个字符")
    private String configKey;

    /**
     * 参数键值
     */
    @NotBlank(message = "参数键值不能为空")
    @Size(min = 0, max = 500, message = "参数键值长度不能超过{max}个字符")
    private String configValue;

    /**
     * 是否系统内置（Y 是 N 否）
     */
    private String configType;

    /**
     * 备注
     */
    private String remark;

    /**
     * 请求参数（创建时间区间 beginTime、endTime）
     */
    private Map<String, Object> params = new HashMap<>();

}
