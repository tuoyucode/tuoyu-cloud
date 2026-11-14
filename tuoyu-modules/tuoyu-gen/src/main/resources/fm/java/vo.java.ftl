<#-- 视图对象：列表字段（带 Excel 注解）与修改时需要回填的字段 -->
package ${gen.packageName}.domain.vo;

<#list gen.voImports as name>
import ${name};
</#list>
import ${gen.packageName}.domain.${ClassName};
import org.apache.fesod.sheet.annotation.ExcelIgnoreUnannotated;
import org.apache.fesod.sheet.annotation.ExcelProperty;
<#if gen.voDict>
import org.tuoyu.common.excel.annotation.ExcelDictFormat;
import org.tuoyu.common.excel.convert.ExcelDictConvert;
</#if>
<#if gen.voTranslation>
import org.tuoyu.common.translation.annotation.Translation;
import org.tuoyu.common.translation.constant.TransConstant;
</#if>
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * ${functionName}视图对象 ${gen.tableName}
 *
 * @author ${gen.author}
 * @date ${gen.datetime}
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ${ClassName}.class)
public class ${ClassName}Vo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

<#list gen.voColumns as column>
    /**
     * ${column.columnComment}
     */
<#if column.list>
<#if column.dictType?has_content>
    @ExcelProperty(value = "${column.columnLabel}", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "${column.dictType}")
<#elseif column.columnComment?contains("（")>
    @ExcelProperty(value = "${column.columnLabel}", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "${column.readConverterExp()}")
<#else>
    @ExcelProperty(value = "${column.columnLabel}")
</#if>
</#if>
    private ${column.javaType} ${column.javaField};

<#if (column.htmlType!'') == 'imageUpload'>
    /**
     * ${column.columnComment}Url
     */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "${column.javaField}")
    private String ${column.javaField}Url;

</#if>
</#list>
}
