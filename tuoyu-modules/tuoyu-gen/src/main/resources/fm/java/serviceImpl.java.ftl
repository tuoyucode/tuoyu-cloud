package ${packageName}.service.impl;

<#if table.tree>
import cn.hutool.core.util.ObjectUtil;
</#if>
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
<#if enableStatus || enableSort>
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
</#if>
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
<#if table.crud>
import org.tuoyu.common.core.domain.PageResult;
</#if>
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
<#if table.crud || enableExport>
import org.tuoyu.common.mybatis.core.page.PageParam;
</#if>
<#if table.crud>
import org.tuoyu.common.mybatis.core.page.PageResults;
</#if>
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import ${packageName}.domain.bo.${ClassName}Bo;
import ${packageName}.domain.vo.${ClassName}Vo;
import ${packageName}.domain.${ClassName};
import ${packageName}.mapper.${ClassName}Mapper;
import ${packageName}.service.I${ClassName}Service;

<#if table.tree && "" != treeAncestorsField>
import java.util.ArrayList;
</#if>
import java.util.Collection;
import java.util.List;
<#if hasBetween>
import java.util.Map;
</#if>

/**
 * ${functionName}Service业务层处理
 *
 * @author ${author}
 * @date ${datetime}
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ${ClassName}ServiceImpl implements I${ClassName}Service {

    private final ${ClassName}Mapper ${className}Mapper;

    /**
     * 查询${functionName}
     *
     * @param ${pkColumn.javaField} 主键
     * @return ${functionName}，不存在时为 null
     */
    @Override
    public ${ClassName}Vo queryById(${pkColumn.javaType} ${pkColumn.javaField}) {
        return BeanMapper.map(${className}Mapper.selectById(${pkColumn.javaField}), ${ClassName}Vo.class);
    }

<#if table.crud>
    /**
     * 分页查询${functionName}列表
     *
     * @param bo        查询条件
     * @param pageParam 分页参数（请求中带了 sort 时按它排序，否则按默认顺序）
     * @return ${functionName}分页列表
     */
    @Override
    public PageResult<${ClassName}Vo> queryPageList(${ClassName}Bo bo, PageParam pageParam) {
        LambdaQueryWrapper<${ClassName}> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        return PageResults.of(${className}Mapper.selectPage(pageParam.toPage(${ClassName}.class), lqw), ${ClassName}Vo.class);
    }
</#if>

    /**
     * 查询符合条件的${functionName}列表
     *
     * @param bo 查询条件
     * @return ${functionName}列表
     */
    @Override
    public List<${ClassName}Vo> queryList(${ClassName}Bo bo) {
        return BeanMapper.mapList(${className}Mapper.selectList(buildQueryWrapper(bo, true)), ${ClassName}Vo.class);
    }
<#if enableExport>

    /**
     * 统计符合条件的${functionName}数量（导出前先统计）
     *
     * @param bo 查询条件
     * @return 行数
     */
    @Override
    public long countList(${ClassName}Bo bo) {
        // 用分页插件统计：它会去掉 ORDER BY，各数据库都能执行
        return ${className}Mapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }
</#if>

<#if enableUnique>
    /**
     * 校验${functionName}是否满足组合唯一约束
     *
     * @param bo ${functionName}
     * @return 是否唯一
     */
    @Override
    public boolean checkUnique(${ClassName}Bo bo) {
        boolean hasUniqueValue = true;
<#list uniqueColumns as column>
<#if column.javaType == 'String'>
        hasUniqueValue = hasUniqueValue && StringUtils.isNotBlank(bo.get${column.capJavaField}());
<#else>
        hasUniqueValue = hasUniqueValue && bo.get${column.capJavaField}() != null;
</#if>
</#list>
        if (!hasUniqueValue) {
            return true;
        }
        return !${className}Mapper.exists(QueryBuilder.lambda(${ClassName}.class)
<#list uniqueColumns as column>
            .eq(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
</#list>
            .neIfPresent(${ClassName}::get${pkColumn.capJavaField}, bo.get${pkColumn.capJavaField}())
            .build());
    }

</#if>
    /**
     * 按查询条件构建查询包装器
     *
     * @param bo           查询条件
     * @param defaultOrder 是否加上默认排序
     * @return 查询包装器
     */
    private LambdaQueryWrapper<${ClassName}> buildQueryWrapper(${ClassName}Bo bo, boolean defaultOrder) {
<#if hasBetween>
        Map<String, Object> params = bo.getParams();
</#if>
        return QueryBuilder.lambda(${ClassName}.class)
<#list columns as column>
<#if column.query>
<#assign queryType = column.queryType>
<#assign javaType = column.javaType>
<#assign AttrName = column.capJavaField>
<#assign mpMethod = column.queryType?lower_case>
<#if queryType != 'BETWEEN'>
<#if javaType == 'String'>
<#assign condition = 'StringUtils.isNotBlank(bo.get'+AttrName+'())'>
<#if queryType == 'LIKE'>
            .likeIfText(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
<#elseif queryType == 'EQ'>
            .eqIfText(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
<#elseif queryType == 'NE'>
            .neIfText(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
<#else>
            .${mpMethod}(${condition}, ${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
</#if>
<#else>
<#assign condition = 'bo.get'+AttrName+'() != null'>
<#if queryType == 'EQ'>
            .eqIfPresent(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
<#elseif queryType == 'NE'>
            .neIfPresent(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
<#elseif queryType == 'GT'>
            .gtIfPresent(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
<#elseif queryType == 'LT'>
            .ltIfPresent(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
<#else>
            .${mpMethod}(${condition}, ${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
</#if>
</#if>
<#else>
            .betweenParams(${ClassName}::get${column.capJavaField}, params, "begin${column.capJavaField}", "end${column.capJavaField}")
</#if>
</#if>
</#list>
<#if table.tree && "" != treeAncestorsField>
            .orderByAsc(defaultOrder, ${ClassName}::get${treeAncestorsCap})
</#if>
<#if table.tree && "" != treeParentCode>
            .orderByAsc(defaultOrder, ${ClassName}::get${treeParentCap})
</#if>
<#if table.tree && "" != treeOrderField>
            .orderByAsc(defaultOrder, ${ClassName}::get${treeOrderCap})
<#elseif enableSort>
            .orderByAsc(defaultOrder, ${ClassName}::get${sortColumn.capJavaField})
</#if>
            .orderByAsc(defaultOrder, ${ClassName}::get${pkColumn.capJavaField})
            .build();
    }

    /**
     * 新增${functionName}，成功后把主键回填到 bo
     *
     * @param bo ${functionName}
     */
    @Override
    public void insertByBo(${ClassName}Bo bo) {
<#if enableUnique>
        if (!checkUnique(bo)) {
            throw new ServiceException("新增${functionName}失败，组合唯一字段已存在");
        }
</#if>
        ${ClassName} add = BeanMapper.map(bo, ${ClassName}.class);
<#if table.tree>
        fillTreeMetaBeforeSave(add, false);
</#if>
        validEntityBeforeSave(add);
        ${className}Mapper.insert(add);
        bo.set${pkColumn.capJavaField}(add.get${pkColumn.capJavaField}());
    }

    /**
     * 修改${functionName}
     *
     * @param bo ${functionName}
     */
    @Override
    public void updateByBo(${ClassName}Bo bo) {
<#if enableUnique>
        if (!checkUnique(bo)) {
            throw new ServiceException("修改${functionName}失败，组合唯一字段已存在");
        }
</#if>
        ${ClassName} update = BeanMapper.map(bo, ${ClassName}.class);
<#if table.tree>
        fillTreeMetaBeforeSave(update, true);
</#if>
        validEntityBeforeSave(update);
        if (${className}Mapper.updateById(update) == 0) {
            throw new ServiceException("修改${functionName}失败，数据不存在");
        }
    }

<#if enableStatus>
    /**
     * 修改${functionName}状态
     *
     * @param ${pkColumn.javaField} 主键
     * @param status 状态值
     */
    @Override
    public void updateStatus(${pkColumn.javaType} ${pkColumn.javaField}, ${statusColumn.javaType} status) {
        int rows = ${className}Mapper.update(Wrappers.<${ClassName}>lambdaUpdate()
            .set(${ClassName}::get${statusColumn.capJavaField}, status)
            .eq(${ClassName}::get${pkColumn.capJavaField}, ${pkColumn.javaField}));
        if (rows == 0) {
            throw new ServiceException("修改${functionName}状态失败，数据不存在");
        }
    }

</#if>
<#if enableSort>
    /**
     * 调整${functionName}排序
     *
     * @param ${pkColumn.javaField} 主键
     * @param sortValue 排序值
     */
    @Override
    public void updateSort(${pkColumn.javaType} ${pkColumn.javaField}, ${sortColumn.javaType} sortValue) {
        int rows = ${className}Mapper.update(Wrappers.<${ClassName}>lambdaUpdate()
            .set(${ClassName}::get${sortColumn.capJavaField}, sortValue)
            .eq(${ClassName}::get${pkColumn.capJavaField}, ${pkColumn.javaField}));
        if (rows == 0) {
            throw new ServiceException("调整${functionName}排序失败，数据不存在");
        }
    }

</#if>
    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(${ClassName} entity) {
        // 可在此扩展通用业务校验
    }
<#if table.tree>

    private void fillTreeMetaBeforeSave(${ClassName} entity, boolean updateMode) {
<#if "" != treeParentCode>
        if (entity.get${treeParentCap}() == null) {
            entity.set${treeParentCap}(${treeRootValueJavaLiteral});
        }
        if (ObjectUtil.equal(entity.get${pkColumn.capJavaField}(), entity.get${treeParentCap}())) {
            throw new ServiceException("${functionName}父节点不能选择自身");
        }
<#if "" != treeAncestorsField>
        ${ClassName} parent = null;
        if (!ObjectUtil.equal(entity.get${treeParentCap}(), ${treeRootValueJavaLiteral})) {
            parent = ${className}Mapper.selectById(entity.get${treeParentCap}());
            if (ObjectUtil.isNull(parent)) {
                throw new ServiceException("${functionName}父节点不存在");
            }
        }
        if (updateMode && entity.get${pkColumn.capJavaField}() != null && ObjectUtil.isNotNull(parent)
            && containsAncestor(parent.get${treeAncestorsCap}(), entity.get${pkColumn.capJavaField}())) {
            throw new ServiceException("不能选择当前节点或其子节点作为父节点");
        }
        String newAncestors = resolveAncestors(entity.get${treeParentCap}(), parent);
        if (updateMode && entity.get${pkColumn.capJavaField}() != null) {
            ${ClassName} oldEntity = ${className}Mapper.selectById(entity.get${pkColumn.capJavaField}());
            if (ObjectUtil.isNull(oldEntity)) {
                throw new ServiceException("${functionName}不存在，无法修改");
            }
            String oldAncestors = oldEntity.get${treeAncestorsCap}();
            entity.set${treeAncestorsCap}(newAncestors);
            if (!StringUtils.equals(oldAncestors, newAncestors)) {
                updateChildrenAncestors(entity.get${pkColumn.capJavaField}(), newAncestors, oldAncestors);
            }
        } else {
            entity.set${treeAncestorsCap}(newAncestors);
        }
</#if>
</#if>
    }
<#if "" != treeAncestorsField>

    private String resolveAncestors(${treeParentColumn.javaType} parentId, ${ClassName} parent) {
        if (ObjectUtil.equal(parentId, ${treeRootValueJavaLiteral})) {
            return "${treeRootValue}";
        }
        String parentAncestors = parent.get${treeAncestorsCap}();
        if (StringUtils.isBlank(parentAncestors)) {
            return String.valueOf(parentId);
        }
        return parentAncestors + StringUtils.SEPARATOR + parentId;
    }

    private void updateChildrenAncestors(${pkColumn.javaType} currentId, String newAncestors, String oldAncestors) {
        List<${ClassName}> children = ${className}Mapper.selectList(QueryBuilder.lambda(${ClassName}.class)
            .select(${ClassName}::get${pkColumn.capJavaField}, ${ClassName}::get${treeAncestorsCap})
            .findInSet(currentId, "${treeAncestorsColumnName}")
            .build());
        List<${ClassName}> updateList = new ArrayList<>();
        for (${ClassName} child : children) {
            String ancestors = child.get${treeAncestorsCap}();
            if (StringUtils.isBlank(ancestors)) {
                continue;
            }
            ${ClassName} update = new ${ClassName}();
            update.set${pkColumn.capJavaField}(child.get${pkColumn.capJavaField}());
            update.set${treeAncestorsCap}(StringUtils.replaceOnce(ancestors, oldAncestors, newAncestors));
            updateList.add(update);
        }
        if (!updateList.isEmpty()) {
            ${className}Mapper.updateById(updateList);
        }
    }

    private boolean containsAncestor(String ancestors, ${pkColumn.javaType} nodeId) {
        for (String item : StringUtils.splitList(ancestors)) {
            if (StringUtils.equals(item, String.valueOf(nodeId))) {
                return true;
            }
        }
        return false;
    }
</#if>
</#if>

    /**
     * 校验并批量删除${functionName}信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     */
    @Override
    public void deleteWithValidByIds(Collection<${pkColumn.javaType}> ids, Boolean isValid) {
        if (Boolean.TRUE.equals(isValid)) {
            // 可在此扩展删除前业务校验，不允许删除时抛出 ServiceException 并说明原因
        }
        if (${className}Mapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除${functionName}失败，数据不存在");
        }
    }

}
