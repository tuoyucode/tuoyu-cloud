package org.tuoyu.gen.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务表（gen_table）：导入的数据库表与它的代码生成配置。
 * <p>
 * 表与字段是平台共享的配置，没有租户。{@code options} 保存生成选项的 JSON（树表字段、上级菜单、导出、状态、
 * 组合唯一与排序），读取详情时拆到同名的非持久化属性中；保存时前端把选项放在 {@code params} 中提交。
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table")
public class GenTable extends BaseEntity {

    private static final String TPL_CRUD = "crud";

    private static final String TPL_TREE = "tree";

    private static final String TENANT_FIELD = "tenantId";

    /**
     * 实体基类 BaseEntity 中的审计字段，生成的实体中不声明
     */
    private static final String[] BASE_ENTITY_FIELDS = {"createDept", "createBy", "createTime", "updateBy", "updateTime"};

    /**
     * 编号
     */
    @TableId(value = "table_id")
    private Long tableId;

    /**
     * 数据源名称
     */
    @NotBlank(message = "数据源名称不能为空")
    private String dataName;

    /**
     * 表名称
     */
    @NotBlank(message = "表名称不能为空")
    private String tableName;

    /**
     * 表描述
     */
    @NotBlank(message = "表描述不能为空")
    private String tableComment;

    /**
     * 实体类名称（首字母大写）
     */
    @NotBlank(message = "实体类名称不能为空")
    private String className;

    /**
     * 模板类型：crud 单表，tree 树表
     */
    private String tplCategory;

    /**
     * 前端页面模板：ele（Element Plus）、antd（Ant Design Vue）
     */
    private String frontendType;

    /**
     * 生成包路径
     */
    @NotBlank(message = "生成包路径不能为空")
    private String packageName;

    /**
     * 生成模块名
     */
    @NotBlank(message = "生成模块名不能为空")
    private String moduleName;

    /**
     * 生成业务名
     */
    @NotBlank(message = "生成业务名不能为空")
    private String businessName;

    /**
     * 生成功能名
     */
    @NotBlank(message = "生成功能名不能为空")
    private String functionName;

    /**
     * 作者
     */
    @NotBlank(message = "作者不能为空")
    private String functionAuthor;

    /**
     * 主键字段
     */
    @TableField(exist = false)
    private GenTableColumn pkColumn;

    /**
     * 字段
     */
    @Valid
    @TableField(exist = false)
    private List<GenTableColumn> columns;

    /**
     * 生成选项（JSON）
     */
    private String options;

    /**
     * 备注
     */
    private String remark;

    /**
     * 树编码字段
     */
    @TableField(exist = false)
    private String treeCode;

    /**
     * 树父编码字段
     */
    @TableField(exist = false)
    private String treeParentCode;

    /**
     * 树名称字段
     */
    @TableField(exist = false)
    private String treeName;

    /**
     * 生成菜单 SQL 时使用的菜单编号（主菜单与 5 个按钮）
     */
    @TableField(exist = false)
    private List<Long> menuIds;

    /**
     * 上级菜单编号
     */
    @TableField(exist = false)
    private Long parentMenuId;

    /**
     * 上级菜单名称
     */
    @TableField(exist = false)
    private String parentMenuName;

    /**
     * 是否生成导出
     */
    @TableField(exist = false)
    private Boolean enableExport;

    /**
     * 是否生成状态切换
     */
    @TableField(exist = false)
    private Boolean enableStatus;

    /**
     * 状态字段
     */
    @TableField(exist = false)
    private String statusField;

    /**
     * 是否生成组合唯一校验
     */
    @TableField(exist = false)
    private Boolean enableUnique;

    /**
     * 组合唯一字段
     */
    @TableField(exist = false)
    private List<String> uniqueFields;

    /**
     * 是否生成排序调整
     */
    @TableField(exist = false)
    private Boolean enableSort;

    /**
     * 排序字段
     */
    @TableField(exist = false)
    private String sortField;

    /**
     * 树根节点的上级编码值
     */
    @TableField(exist = false)
    private String treeRootValue;

    /**
     * 树祖级字段
     */
    @TableField(exist = false)
    private String treeAncestorsField;

    /**
     * 树排序字段
     */
    @TableField(exist = false)
    private String treeOrderField;

    /**
     * 请求参数：保存时为生成选项，查询时为创建时间区间（beginTime、endTime）。
     * 本类自己声明，JSON 中总是输出（前端编辑页按它回填选项）
     */
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();

    /**
     * @return 是否为树表
     */
    public boolean isTree() {
        return TPL_TREE.equals(tplCategory);
    }

    /**
     * @return 是否为单表
     */
    public boolean isCrud() {
        return TPL_CRUD.equals(tplCategory);
    }

    /**
     * @return 是否按租户隔离（有 Java 属性名为 tenantId 的字段）：生成的实体继承 TenantEntity
     */
    public boolean isTenant() {
        return columns != null && columns.stream()
            .anyMatch(column -> StringUtils.equalsIgnoreCase(TENANT_FIELD, column.getJavaField()));
    }

    /**
     * 实体基类中已有的字段（生成的实体不声明）：审计字段，按租户隔离时还有 tenantId
     *
     * @param javaField Java 属性名
     * @return 是否为实体基类中的字段（忽略大小写）
     */
    public boolean isSuperColumn(String javaField) {
        if (isTenant() && StringUtils.equalsIgnoreCase(TENANT_FIELD, javaField)) {
            return true;
        }
        return StringUtils.equalsAnyIgnoreCase(javaField, BASE_ENTITY_FIELDS);
    }
}
