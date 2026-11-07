package ${packageName}.domain.bo;

import ${packageName}.domain.${ClassName};
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import io.github.linpeilie.annotations.AutoMapper;
import java.io.Serial;
import java.io.Serializable;
<#if hasBetween>
import java.util.HashMap;
import java.util.Map;
</#if>
import lombok.Data;
import jakarta.validation.constraints.*;
<#list importList as import>
import ${import};
</#list>

/**
 * ${functionName}业务对象 ${tableName}
 *
 * @author ${author}
 * @date ${datetime}
 */
@Data
@AutoMapper(target = ${ClassName}.class, reverseConvertGenerate = false)
public class ${ClassName}Bo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

<#-- 主键总是保留：修改、状态、排序接口按它定位记录 -->
<#list columns as column>
<#if column.pk || column.query || (!table.isSuperColumn(column.javaField) && (column.insert || column.edit))>
    /**
     * ${column.columnComment}
     */
<#if column.insert && column.edit>
<#assign Group = "OnCreate.class, OnUpdate.class">
<#elseif column.insert>
<#assign Group = "OnCreate.class">
<#elseif column.edit>
<#assign Group = "OnUpdate.class">
</#if>
<#if column.required && !table.isSuperColumn(column.javaField) && (column.insert || column.edit)>
<#if column.javaType == 'String'>
    @NotBlank(message = "${column.columnComment}不能为空", groups = { ${Group} })
<#else>
    @NotNull(message = "${column.columnComment}不能为空", groups = { ${Group} })
</#if>
</#if>
    private ${column.javaType} ${column.javaField};

</#if>
</#list>
<#if hasBetween>
    /**
     * 查询参数
     */
    private Map<String, Object> params = new HashMap<>();
</#if>

}
