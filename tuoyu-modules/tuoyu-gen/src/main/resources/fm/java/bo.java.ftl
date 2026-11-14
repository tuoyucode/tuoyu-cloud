<#-- 业务对象：主键、查询条件与新增、修改的字段；必填字段按新增、修改分组校验 -->
package ${gen.packageName}.domain.bo;

import ${gen.packageName}.domain.${ClassName};
<#if gen.boValidation>
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
</#if>
import io.github.linpeilie.annotations.AutoMapper;
import java.io.Serial;
import java.io.Serializable;
<#if gen.hasBetween>
import java.util.HashMap;
import java.util.Map;
</#if>
import lombok.Data;
<#if gen.boValidation>
import jakarta.validation.constraints.*;
</#if>
<#list gen.boImports as name>
import ${name};
</#list>

/**
 * ${functionName}业务对象 ${gen.tableName}
 *
 * @author ${gen.author}
 * @date ${gen.datetime}
 */
@Data
@AutoMapper(target = ${ClassName}.class, reverseConvertGenerate = false)
public class ${ClassName}Bo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

<#list gen.boColumns as item>
<#assign column = item.column>
    /**
     * ${column.columnComment}
     */
<#if item.validation?has_content>
    @${item.validation}(message = "${column.columnComment}不能为空", groups = { ${item.groups} })
</#if>
    private ${column.javaType} ${column.javaField};

</#list>
<#if gen.hasBetween>
    /**
     * 查询参数
     */
    private Map<String, Object> params = new HashMap<>();

</#if>
}
