<#-- 实体：不声明实体基类（BaseEntity、按租户隔离时 TenantEntity）中已有的字段 -->
package ${gen.packageName}.domain;

import org.tuoyu.common.mybatis.core.domain.${gen.baseEntity};
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
<#list gen.domainImports as name>
import ${name};
</#list>

import java.io.Serial;

/**
 * ${functionName}对象 ${gen.tableName}
 *
 * @author ${gen.author}
 * @date ${gen.datetime}
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("${gen.tableName}")
public class ${ClassName} extends ${gen.baseEntity} {

    @Serial
    private static final long serialVersionUID = 1L;

<#list gen.domainColumns as column>
    /**
     * ${column.columnComment}
     */
<#if column.javaField == 'delFlag'>
    @TableLogic
</#if>
<#if column.javaField == 'version'>
    @Version
</#if>
<#if column.pk>
    @TableId(value = "${column.columnName?j_string}")
<#elseif column.needTableField>
    @TableField(value = "${column.columnName?j_string}")
</#if>
    private ${column.javaType} ${column.javaField};

</#list>
}
