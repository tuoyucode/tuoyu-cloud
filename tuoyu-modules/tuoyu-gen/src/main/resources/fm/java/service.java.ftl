<#assign pkType = pkColumn.javaType>
<#assign pkField = pkColumn.javaField>
package ${gen.packageName}.service;

import ${gen.packageName}.domain.vo.${ClassName}Vo;
import ${gen.packageName}.domain.bo.${ClassName}Bo;
<#if table.crud>
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
</#if>

import java.util.Collection;
import java.util.List;

/**
 * ${functionName}Service接口
 *
 * @author ${gen.author}
 * @date ${gen.datetime}
 */
public interface I${ClassName}Service {

    /**
     * 查询${functionName}
     *
     * @param ${pkField} 主键
     * @return ${functionName}
     */
    ${ClassName}Vo queryById(${pkType} ${pkField});

<#if table.crud>
    /**
     * 分页查询${functionName}列表
     *
     * @param bo        查询条件
     * @param pageParam 分页与排序
     * @return ${functionName}分页列表
     */
    PageResult<${ClassName}Vo> queryPageList(${ClassName}Bo bo, PageParam pageParam);

</#if>
    /**
     * 查询符合条件的${functionName}列表
     *
     * @param bo 查询条件
     * @return ${functionName}列表
     */
    List<${ClassName}Vo> queryList(${ClassName}Bo bo);

<#if enableExport>
    /**
     * 统计符合条件的${functionName}数量（导出前检查行数）
     *
     * @param bo 查询条件
     * @return 数量
     */
    long countList(${ClassName}Bo bo);

</#if>
<#if gen.enableUnique>
    /**
     * 检查组合唯一字段是否可用
     *
     * @param bo ${functionName}
     * @return 没有其他记录使用相同的值时为 true
     */
    boolean checkUnique(${ClassName}Bo bo);

</#if>
    /**
     * 新增${functionName}
     *
     * @param bo ${functionName}
     */
    void insertByBo(${ClassName}Bo bo);

    /**
     * 修改${functionName}
     *
     * @param bo ${functionName}
     */
    void updateByBo(${ClassName}Bo bo);

<#if enableStatus>
    /**
     * 修改${functionName}状态
     *
     * @param ${pkField} 主键
     * @param status 状态
     */
    void updateStatus(${pkType} ${pkField}, ${statusColumn.javaType} status);

</#if>
<#if gen.enableSort>
    /**
     * 调整${functionName}排序
     *
     * @param ${pkField}  主键
     * @param sortValue 排序值
     */
    void updateSort(${pkType} ${pkField}, ${gen.sortColumn.javaType} sortValue);

</#if>
    /**
     * 删除${functionName}
     *
     * @param ids     主键
     * @param isValid 是否在删除前校验
     */
    void deleteWithValidByIds(Collection<${pkType}> ids, Boolean isValid);

}
