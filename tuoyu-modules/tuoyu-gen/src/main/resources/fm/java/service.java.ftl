package ${packageName}.service;

import ${packageName}.domain.vo.${ClassName}Vo;
import ${packageName}.domain.bo.${ClassName}Bo;
<#if table.crud>
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
</#if>

import java.util.Collection;
import java.util.List;

/**
 * ${functionName}Service接口
 * <p>
 * 写操作没有返回值，失败时抛出带原因的 ServiceException。
 *
 * @author ${author}
 * @date ${datetime}
 */
public interface I${ClassName}Service {

    /**
     * 查询${functionName}
     *
     * @param ${pkColumn.javaField} 主键
     * @return ${functionName}，不存在时为 null
     */
    ${ClassName}Vo queryById(${pkColumn.javaType} ${pkColumn.javaField});

<#if table.crud>
    /**
     * 分页查询${functionName}列表
     *
     * @param bo        查询条件
     * @param pageParam 分页参数
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
     * 统计符合条件的${functionName}数量（导出前先统计）
     *
     * @param bo 查询条件
     * @return 行数
     */
    long countList(${ClassName}Bo bo);
</#if>

<#if enableUnique>
    /**
     * 校验${functionName}是否满足组合唯一约束
     *
     * @param bo ${functionName}
     * @return 是否唯一
     */
    boolean checkUnique(${ClassName}Bo bo);

</#if>
    /**
     * 新增${functionName}，成功后把主键回填到 bo
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
     * @param ${pkColumn.javaField} 主键
     * @param status 状态值
     */
    void updateStatus(${pkColumn.javaType} ${pkColumn.javaField}, ${statusColumn.javaType} status);

</#if>
<#if enableSort>
    /**
     * 调整${functionName}排序
     *
     * @param ${pkColumn.javaField} 主键
     * @param sortValue 排序值
     */
    void updateSort(${pkColumn.javaType} ${pkColumn.javaField}, ${sortColumn.javaType} sortValue);

</#if>
    /**
     * 校验并批量删除${functionName}信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     */
    void deleteWithValidByIds(Collection<${pkColumn.javaType}> ids, Boolean isValid);

}
