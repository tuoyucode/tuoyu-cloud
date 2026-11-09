package ${packageName}.domain;

<#if table.tenant>
import org.tuoyu.common.mybatis.core.domain.TenantEntity;
<#else>
import org.tuoyu.common.mybatis.core.domain.BaseEntity;
</#if>
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
<#list importList as import>
import ${import};
</#list>

import java.io.Serial;

/**
 * ${functionName}对象 ${tableName}
 *
 * @author ${author}
 * @date ${datetime}
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("${tableName}")
public class ${ClassName} extends <#if table.tenant>TenantEntity<#else>BaseEntity</#if> {

    @Serial
    private static final long serialVersionUID = 1L;

<#list columns as column>
<#if !table.isSuperColumn(column.javaField)>
    /**
     * ${column.columnComment}
     */
<#if column.javaField=='delFlag'>
    @TableLogic
</#if>
<#if column.javaField=='version'>
    @Version
</#if>
<#if column.pk>
    @TableId(value = "${column.columnName}")
<#elseif column.needTableField>
    @TableField(value = "${column.columnName}")
</#if>
    private ${column.javaType} ${column.javaField};

</#if>
</#list>

}
