package org.tuoyu.gen.service;

import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 生成配置的检查，保存与生成时都执行。
 * <p>
 * 包路径、模块名、业务名、类名、表名与字段的配置会进入 zip 中的路径与生成的 Java、SQL 代码，
 * 所以只接受标识符与固定的取值：不能借它们写到解压目录之外，或在生成的代码中插入语句。
 * 已保存的配置不满足时拒绝生成并写明是哪一项，不自动修正。
 *
 * @author tuoyu
 */
final class GenConfigChecker {

    private static final Pattern PACKAGE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");

    private static final Pattern MODULE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private static final Pattern CLASS_NAME = Pattern.compile("^[A-Z][A-Za-z0-9_]*$");

    private static final Pattern TABLE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");

    private static final Pattern JAVA_FIELD = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private static final Pattern DICT_TYPE = Pattern.compile("^[a-z][a-z0-9_]*$");

    private static final Pattern MENU_ID = Pattern.compile("^\\d+$");

    private static final Pattern INTEGER_VALUE = Pattern.compile("^-?\\d+$");

    private static final Pattern DECIMAL_VALUE = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private static final Pattern CODE_VALUE = Pattern.compile("^[A-Za-z0-9_-]+$");

    private static final Set<String> TPL_CATEGORIES = Set.of("crud", "tree");

    private static final Set<String> JAVA_TYPES = Set.of("String", "Integer", "Long", "Double", "Float", "BigDecimal",
        "Boolean", "LocalDateTime");

    private static final Set<String> HTML_TYPES = Set.of("input", "textarea", "inputNumber", "select", "radio",
        "checkbox", "switch", "datetime", "imageUpload", "fileUpload", "editor");

    private static final Set<String> QUERY_TYPES = Set.of("EQ", "NE", "GT", "GE", "LT", "LE", "LIKE", "BETWEEN");

    private GenConfigChecker() {
    }

    /**
     * 表名是否可以导入（与生成时的表名检查相同）
     */
    static boolean isValidTableName(String tableName) {
        return matches(TABLE_NAME, tableName);
    }

    /**
     * 保存前的检查
     *
     * @param table 请求中的业务表：字段取自 columns，生成选项取自 params
     */
    static void checkEdit(GenTable table) {
        String frontendType = table.getFrontendType();
        if (StringUtils.isNotBlank(frontendType) && !GenTableDefaults.FRONTEND_ELE.equals(frontendType)
            && !GenTableDefaults.FRONTEND_ANTD.equals(frontendType)) {
            throw new ServiceException("前端模板类型只能是 ele（Element Plus）或 antd（Ant Design Vue）");
        }
        checkTableAndColumns(table);
        GenOptions options = new GenOptions(table.getParams());
        List<GenTableColumn> columns = table.getColumns();
        if (!options.isEmpty() && columns != null && !columns.isEmpty()) {
            requireColumn(columns, options.string(GenOptions.STATUS_FIELD), "状态字段不存在，请刷新字段后重试");
            requireColumn(columns, options.string(GenOptions.SORT_FIELD), "排序字段不存在，请刷新字段后重试");
            requireColumn(columns, options.string(GenOptions.TREE_ANCESTORS), "树祖级字段不存在，请刷新字段后重试");
            requireColumn(columns, options.string(GenOptions.TREE_ORDER_FIELD), "树排序字段不存在，请刷新字段后重试");
            if (options.get(GenOptions.UNIQUE_FIELDS) instanceof Collection<?> uniqueFields) {
                for (Object field : uniqueFields) {
                    requireColumn(columns, field == null ? null : String.valueOf(field), "组合唯一字段不存在，请刷新字段后重试");
                }
            }
        }
        if (table.isTree()) {
            String treeCode = options.string(GenOptions.TREE_CODE);
            String treeParentCode = options.string(GenOptions.TREE_PARENT_CODE);
            String treeName = options.string(GenOptions.TREE_NAME);
            if (StringUtils.isBlank(treeCode)) {
                throw new ServiceException("树编码字段不能为空");
            }
            if (StringUtils.isBlank(treeParentCode)) {
                throw new ServiceException("树父编码字段不能为空");
            }
            if (StringUtils.isBlank(treeName)) {
                throw new ServiceException("树名称字段不能为空");
            }
            requireColumn(columns, treeCode, "树编码字段不存在，请刷新字段后重试");
            requireColumn(columns, treeParentCode, "树父编码字段不存在，请刷新字段后重试");
            requireColumn(columns, treeName, "树名称字段不存在，请刷新字段后重试");
        }
        checkOptions(table, options);
    }

    /**
     * 生成前的检查
     *
     * @param table   已保存的业务表（带字段）
     * @param options 已保存的生成选项
     */
    static void checkGenerate(GenTable table, GenOptions options) {
        checkTableAndColumns(table);
        checkOptions(table, options);
    }

    /**
     * 模板类型、包路径、模块名、业务名、类名、表名与每个字段的配置
     */
    private static void checkTableAndColumns(GenTable table) {
        if (!TPL_CATEGORIES.contains(String.valueOf(table.getTplCategory()))) {
            throw new ServiceException("模板类型只能是 crud（单表）或 tree（树表）");
        }
        if (!matches(PACKAGE_NAME, table.getPackageName())) {
            throw new ServiceException("生成包路径只能是以点分隔的 Java 标识符，例如 org.tuoyu.system");
        }
        if (!matches(MODULE_NAME, table.getModuleName())) {
            throw new ServiceException("生成模块名只能包含字母、数字与下划线，且以字母开头");
        }
        if (!matches(MODULE_NAME, table.getBusinessName())) {
            throw new ServiceException("生成业务名只能包含字母、数字与下划线，且以字母开头");
        }
        if (!matches(CLASS_NAME, table.getClassName())) {
            throw new ServiceException("实体类名称只能包含字母、数字与下划线，且以大写字母开头");
        }
        if (!matches(TABLE_NAME, table.getTableName())) {
            throw new ServiceException("表名称只能包含字母、数字、下划线与 $，且不能以数字开头");
        }
        if (table.getColumns() == null) {
            return;
        }
        for (GenTableColumn column : table.getColumns()) {
            checkColumn(column);
        }
    }

    private static void checkColumn(GenTableColumn column) {
        String prefix = "字段 " + column.getColumnName() + " 的";
        if (!matches(JAVA_FIELD, column.getJavaField())) {
            throw new ServiceException(prefix + " Java 属性名只能包含字母、数字与下划线，且不能以数字开头");
        }
        if (!JAVA_TYPES.contains(String.valueOf(column.getJavaType()))) {
            throw new ServiceException(prefix + " Java 类型不支持：" + column.getJavaType());
        }
        if (StringUtils.isNotBlank(column.getHtmlType()) && !HTML_TYPES.contains(column.getHtmlType())) {
            throw new ServiceException(prefix + "显示类型不支持：" + column.getHtmlType());
        }
        if (StringUtils.isNotBlank(column.getQueryType()) && !QUERY_TYPES.contains(column.getQueryType())) {
            throw new ServiceException(prefix + "查询方式不支持：" + column.getQueryType());
        }
        if (StringUtils.isNotBlank(column.getDictType()) && !matches(DICT_TYPE, column.getDictType())) {
            throw new ServiceException(prefix + "字典类型格式不正确");
        }
    }

    /**
     * 上级菜单编号（写进菜单 SQL）与树根节点值（写进生成的 Java 代码）
     */
    private static void checkOptions(GenTable table, GenOptions options) {
        String parentMenuId = options.string(GenOptions.PARENT_MENU_ID);
        if (StringUtils.isNotBlank(parentMenuId) && !matches(MENU_ID, parentMenuId)) {
            throw new ServiceException("上级菜单编号格式不正确");
        }
        String rootValue = options.string(GenOptions.TREE_ROOT_VALUE);
        if (table.isTree() && StringUtils.isNotBlank(rootValue)) {
            GenTableColumn parent = GenOptions.findColumn(table.getColumns(), options.string(GenOptions.TREE_PARENT_CODE));
            String javaType = parent == null ? null : parent.getJavaType();
            Pattern pattern = switch (javaType == null ? "" : javaType) {
                case "Long", "Integer" -> INTEGER_VALUE;
                case "Float", "Double", "BigDecimal" -> DECIMAL_VALUE;
                default -> CODE_VALUE;
            };
            if (!matches(pattern, rootValue)) {
                throw new ServiceException("树根节点值格式不正确");
            }
        }
    }

    private static void requireColumn(List<GenTableColumn> columns, String name, String message) {
        if (StringUtils.isNotBlank(name) && GenOptions.findColumn(columns, name) == null) {
            throw new ServiceException(message);
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }
}
