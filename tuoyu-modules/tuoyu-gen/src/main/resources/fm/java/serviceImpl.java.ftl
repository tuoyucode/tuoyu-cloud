<#assign pkType = pkColumn.javaType>
<#assign pkField = pkColumn.javaField>
<#assign pkCap = pkColumn.capJavaField>
<#assign mapper = className + "Mapper">
package ${gen.packageName}.service.impl;

<#if gen.treeParent>
import cn.hutool.core.util.ObjectUtil;
</#if>
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
<#if enableStatus || gen.enableSort>
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
<#if gen.needStringUtils>
import org.tuoyu.common.core.utils.StringUtils;
</#if>
<#if table.crud || enableExport>
import org.tuoyu.common.mybatis.core.page.PageParam;
</#if>
<#if table.crud>
import org.tuoyu.common.mybatis.core.page.PageResults;
</#if>
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import ${gen.packageName}.domain.bo.${ClassName}Bo;
import ${gen.packageName}.domain.vo.${ClassName}Vo;
import ${gen.packageName}.domain.${ClassName};
import ${gen.packageName}.mapper.${ClassName}Mapper;
import ${gen.packageName}.service.I${ClassName}Service;

<#if gen.treeAncestors>
import java.util.ArrayList;
</#if>
import java.util.Collection;
import java.util.List;
<#if gen.hasBetween>
import java.util.Map;
</#if>

/**
 * ${functionName}Service业务层处理
 *
 * @author ${gen.author}
 * @date ${gen.datetime}
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ${ClassName}ServiceImpl implements I${ClassName}Service {

    private final ${ClassName}Mapper ${mapper};

    /**
     * 查询${functionName}
     *
     * @param ${pkField} 主键
     * @return ${functionName}
     */
    @Override
    public ${ClassName}Vo queryById(${pkType} ${pkField}) {
        return BeanMapper.map(${mapper}.selectById(${pkField}), ${ClassName}Vo.class);
    }
<#if table.crud>

    /**
     * 分页查询${functionName}列表（请求中没有排序时使用默认排序）
     *
     * @param bo        查询条件
     * @param pageParam 分页与排序
     * @return ${functionName}分页列表
     */
    @Override
    public PageResult<${ClassName}Vo> queryPageList(${ClassName}Bo bo, PageParam pageParam) {
        LambdaQueryWrapper<${ClassName}> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        return PageResults.of(${mapper}.selectPage(pageParam.toPage(${ClassName}.class), lqw), ${ClassName}Vo.class);
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
        return BeanMapper.mapList(${mapper}.selectList(buildQueryWrapper(bo, true)), ${ClassName}Vo.class);
    }
<#if enableExport>

    /**
     * 统计符合条件的${functionName}数量（只执行 COUNT，不取数据）
     *
     * @param bo 查询条件
     * @return 数量
     */
    @Override
    public long countList(${ClassName}Bo bo) {
        return ${mapper}.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }
</#if>
<#if gen.enableUnique>

    /**
     * 检查组合唯一字段是否可用：有字段为空时不检查
     *
     * @param bo ${functionName}
     * @return 没有其他记录使用相同的值时为 true
     */
    @Override
    public boolean checkUnique(${ClassName}Bo bo) {
        boolean hasUniqueValue = true;
<#list gen.uniqueColumns as column>
<#if column.javaType == 'String'>
        hasUniqueValue = hasUniqueValue && StringUtils.isNotBlank(bo.get${column.capJavaField}());
<#else>
        hasUniqueValue = hasUniqueValue && bo.get${column.capJavaField}() != null;
</#if>
</#list>
        if (!hasUniqueValue) {
            return true;
        }
        return !${mapper}.exists(QueryBuilder.lambda(${ClassName}.class)
<#list gen.uniqueColumns as column>
            .eq(${ClassName}::get${column.capJavaField}, bo.get${column.capJavaField}())
</#list>
            .neIfPresent(${ClassName}::get${pkCap}, bo.get${pkCap}())
            .build());
    }
</#if>

    /**
     * 查询条件
     *
     * @param bo           查询条件
     * @param defaultOrder 是否加默认排序
     */
    private LambdaQueryWrapper<${ClassName}> buildQueryWrapper(${ClassName}Bo bo, boolean defaultOrder) {
<#if gen.hasBetween>
        Map<String, Object> params = bo.getParams();
</#if>
        return QueryBuilder.lambda(${ClassName}.class)
<#list gen.queryColumns as item>
<#assign X = item.cap>
<#assign getter = "bo.get" + X + "()">
<#if item.type == 'BETWEEN'>
            .betweenParams(${ClassName}::get${X}, params, "begin${X}", "end${X}")
<#elseif item.string>
<#if item.type == 'LIKE'>
            .likeIfText(${ClassName}::get${X}, ${getter})
<#elseif item.type == 'EQ'>
            .eqIfText(${ClassName}::get${X}, ${getter})
<#elseif item.type == 'NE'>
            .neIfText(${ClassName}::get${X}, ${getter})
<#else>
            .${item.type?lower_case}(StringUtils.isNotBlank(${getter}), ${ClassName}::get${X}, ${getter})
</#if>
<#else>
<#if item.type == 'EQ'>
            .eqIfPresent(${ClassName}::get${X}, ${getter})
<#elseif item.type == 'NE'>
            .neIfPresent(${ClassName}::get${X}, ${getter})
<#elseif item.type == 'GT'>
            .gtIfPresent(${ClassName}::get${X}, ${getter})
<#elseif item.type == 'LT'>
            .ltIfPresent(${ClassName}::get${X}, ${getter})
<#else>
            .${item.type?lower_case}(${getter} != null, ${ClassName}::get${X}, ${getter})
</#if>
</#if>
</#list>
<#list gen.orderFields as X>
            .orderByAsc(defaultOrder, ${ClassName}::get${X})
</#list>
            .build();
    }

    /**
     * 新增${functionName}
     *
     * @param bo ${functionName}
     */
    @Override
    public void insertByBo(${ClassName}Bo bo) {
<#if gen.enableUnique>
        if (!checkUnique(bo)) {
            throw new ServiceException("新增${functionName}失败，组合唯一字段已存在");
        }
</#if>
        ${ClassName} add = BeanMapper.map(bo, ${ClassName}.class);
<#if table.tree>
        fillTreeMetaBeforeSave(add, false);
</#if>
        validEntityBeforeSave(add);
        ${mapper}.insert(add);
        bo.set${pkCap}(add.get${pkCap}());
    }

    /**
     * 修改${functionName}
     *
     * @param bo ${functionName}
     */
    @Override
    public void updateByBo(${ClassName}Bo bo) {
<#if gen.enableUnique>
        if (!checkUnique(bo)) {
            throw new ServiceException("修改${functionName}失败，组合唯一字段已存在");
        }
</#if>
        ${ClassName} update = BeanMapper.map(bo, ${ClassName}.class);
<#if table.tree>
        fillTreeMetaBeforeSave(update, true);
</#if>
        validEntityBeforeSave(update);
        if (${mapper}.updateById(update) == 0) {
            throw new ServiceException("修改${functionName}失败，数据不存在");
        }
    }
<#if enableStatus>

    /**
     * 修改${functionName}状态
     *
     * @param ${pkField} 主键
     * @param status 状态
     */
    @Override
    public void updateStatus(${pkType} ${pkField}, ${statusColumn.javaType} status) {
        int rows = ${mapper}.update(Wrappers.<${ClassName}>lambdaUpdate()
            .set(${ClassName}::get${statusColumn.capJavaField}, status)
            .eq(${ClassName}::get${pkCap}, ${pkField}));
        if (rows == 0) {
            throw new ServiceException("修改${functionName}状态失败，数据不存在");
        }
    }
</#if>
<#if gen.enableSort>

    /**
     * 调整${functionName}排序
     *
     * @param ${pkField}  主键
     * @param sortValue 排序值
     */
    @Override
    public void updateSort(${pkType} ${pkField}, ${gen.sortColumn.javaType} sortValue) {
        int rows = ${mapper}.update(Wrappers.<${ClassName}>lambdaUpdate()
            .set(${ClassName}::get${gen.sortColumn.capJavaField}, sortValue)
            .eq(${ClassName}::get${pkCap}, ${pkField}));
        if (rows == 0) {
            throw new ServiceException("调整${functionName}排序失败，数据不存在");
        }
    }
</#if>

    /**
     * 保存前的数据校验（例如唯一约束），按需扩展
     */
    private void validEntityBeforeSave(${ClassName} entity) {
    }
<#if table.tree>

    /**
     * 保存前填写树的上级与祖级：没有上级时为根节点，不能选择自身或下级作为上级，上级变化时同步修改下级的祖级
     *
     * @param entity     ${functionName}
     * @param updateMode 是否为修改
     */
    private void fillTreeMetaBeforeSave(${ClassName} entity, boolean updateMode) {
<#if gen.treeParent>
<#assign parentCap = gen.treeParentColumn.capJavaField>
        if (entity.get${parentCap}() == null) {
            entity.set${parentCap}(${gen.treeRootLiteral});
        }
        if (ObjectUtil.equal(entity.get${pkCap}(), entity.get${parentCap}())) {
            throw new ServiceException("${functionName}父节点不能选择自身");
        }
<#if gen.treeAncestors>
<#assign ancestorsCap = gen.treeAncestorsColumn.capJavaField>
        ${ClassName} parent = null;
        if (!ObjectUtil.equal(entity.get${parentCap}(), ${gen.treeRootLiteral})) {
            parent = ${mapper}.selectById(entity.get${parentCap}());
            if (ObjectUtil.isNull(parent)) {
                throw new ServiceException("${functionName}父节点不存在");
            }
        }
        if (updateMode && entity.get${pkCap}() != null && ObjectUtil.isNotNull(parent)
            && containsAncestor(parent.get${ancestorsCap}(), entity.get${pkCap}())) {
            throw new ServiceException("不能选择当前节点或其子节点作为父节点");
        }
        String newAncestors = resolveAncestors(entity.get${parentCap}(), parent);
        if (updateMode && entity.get${pkCap}() != null) {
            ${ClassName} oldEntity = ${mapper}.selectById(entity.get${pkCap}());
            if (ObjectUtil.isNull(oldEntity)) {
                throw new ServiceException("${functionName}不存在，无法修改");
            }
            String oldAncestors = oldEntity.get${ancestorsCap}();
            entity.set${ancestorsCap}(newAncestors);
            if (!StringUtils.equals(oldAncestors, newAncestors)) {
                updateChildrenAncestors(entity.get${pkCap}(), newAncestors, oldAncestors);
            }
        } else {
            entity.set${ancestorsCap}(newAncestors);
        }
</#if>
</#if>
    }
<#if gen.treeAncestors>
<#assign parentType = gen.treeParentColumn.javaType>
<#assign parentField = gen.treeParentColumn.javaField>

    /**
     * 新的祖级：上级为根节点时为根节点值，否则为上级的祖级加上上级编号
     */
    private String resolveAncestors(${parentType} ${parentField}, ${ClassName} parent) {
        if (ObjectUtil.equal(${parentField}, ${gen.treeRootLiteral})) {
            return "${gen.treeRootValue}";
        }
        String parentAncestors = parent.get${ancestorsCap}();
        if (StringUtils.isBlank(parentAncestors)) {
            return String.valueOf(${parentField});
        }
        return parentAncestors + StringUtils.SEPARATOR + ${parentField};
    }

    /**
     * 上级变化后，把下级祖级中的原祖级替换为新祖级
     */
    private void updateChildrenAncestors(${pkType} currentId, String newAncestors, String oldAncestors) {
        List<${ClassName}> children = ${mapper}.selectList(QueryBuilder.lambda(${ClassName}.class)
            .select(${ClassName}::get${pkCap}, ${ClassName}::get${ancestorsCap})
            .findInSet(currentId, "${gen.treeAncestorsColumn.columnName?j_string}")
            .build());
        List<${ClassName}> updateList = new ArrayList<>();
        for (${ClassName} child : children) {
            String ancestors = child.get${ancestorsCap}();
            if (StringUtils.isBlank(ancestors)) {
                continue;
            }
            ${ClassName} update = new ${ClassName}();
            update.set${pkCap}(child.get${pkCap}());
            update.set${ancestorsCap}(StringUtils.replaceOnce(ancestors, oldAncestors, newAncestors));
            updateList.add(update);
        }
        if (!updateList.isEmpty()) {
            ${mapper}.updateById(updateList);
        }
    }

    /**
     * 祖级（逗号分隔）中是否含有该节点
     */
    private boolean containsAncestor(String ancestors, ${pkType} nodeId) {
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
     * 删除${functionName}
     *
     * @param ids     主键
     * @param isValid 是否在删除前校验
     */
    @Override
    public void deleteWithValidByIds(Collection<${pkType}> ids, Boolean isValid) {
        if (Boolean.TRUE.equals(isValid)) {
            // 删除前的业务校验（例如是否被引用），按需扩展
        }
        if (${mapper}.deleteByIds(ids) == 0) {
            throw new ServiceException("删除${functionName}失败，数据不存在");
        }
    }

}
