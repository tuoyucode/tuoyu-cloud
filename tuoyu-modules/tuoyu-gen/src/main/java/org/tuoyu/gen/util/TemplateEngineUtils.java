package org.tuoyu.gen.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import cn.hutool.extra.template.engine.freemarker.FreemarkerEngine;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.DateUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.mybatis.enums.DataBaseType;
import org.tuoyu.common.mybatis.helper.DataBaseHelper;
import org.tuoyu.gen.constant.GenConstants;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import org.tuoyu.gen.util.template.PathNamedTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * 模板引擎工具
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TemplateEngineUtils {

    /**
     * 项目空间路径
     */
    private static final String PROJECT_PATH = "main/java";

    /**
     * mybatis空间路径
     */
    private static final String MYBATIS_PATH = "main/resources/mapper";

    /**
     * 默认上级菜单，系统工具
     */
    private static final String DEFAULT_PARENT_MENU_ID = "1761400000000000003";

    // 模板引擎
    private static final TemplateEngine TEMPLATE_ENGINE;
    private static final Map<String, PathNamedTemplate> TEMPLATE_MAPPER;

    static {
        // 模板引擎初始化
        TemplateConfig templateConfig = new TemplateConfig(StandardCharsets.UTF_8, StringUtils.EMPTY, TemplateConfig.ResourceMode.CLASSPATH);
        templateConfig.setCustomEngine(FreemarkerEngine.class);
        TEMPLATE_ENGINE = TemplateUtil.createEngine(templateConfig);
        // 禁用数字千分位格式化 避免 Long 类型的 ID 渲染是被加上逗号
        if (TEMPLATE_ENGINE instanceof FreemarkerEngine freemarkerEngine) {
            freemarkerEngine.getConfiguration().setNumberFormat("computer");
        }
        TEMPLATE_MAPPER = PathNamedTemplate.form(TEMPLATE_ENGINE, GenConstants.TEMPLATE_PATHS);
    }

    /**
     * 构建模板上下文
     *
     * @param contextInit 模板上下文初始化函数
     * @return 模板上下文
     */
    public static Dict buildContext(Consumer<Dict> contextInit) {
        Dict context = new Dict();
        contextInit.accept(context);
        return context;
    }

    /**
     * 构建模板上下文
     *
     * @param genTable 代码生成业务表对象
     * @return 模板上下文
     */
    public static Dict buildContext(GenTable genTable) {
        fillBlankColumnComments(genTable);
        // 构建上下文
        Dict context = new Dict();
        String moduleName = genTable.getModuleName();
        String businessName = genTable.getBusinessName();
        String packageName = genTable.getPackageName();
        String tplCategory = genTable.getTplCategory();
        String functionName = genTable.getFunctionName();

        context.put("tplCategory", genTable.getTplCategory());
        context.put("tableName", genTable.getTableName());
        context.put("functionName", StringUtils.isNotEmpty(functionName) ? functionName : "【请填写功能名称】");
        context.put("ClassName", genTable.getClassName());
        context.put("className", StringUtils.uncapitalize(genTable.getClassName()));
        context.put("moduleName", moduleName);
        context.put("BusinessName", StringUtils.capitalize(businessName));
        context.put("businessName", businessName);
        context.put("basePackage", getPackagePrefix(packageName));
        context.put("packageName", packageName);
        context.put("author", genTable.getFunctionAuthor());
        context.put("datetime", DateUtils.now());
        context.put("pkColumn", genTable.getPkColumn());
        String dicts = getDicts(genTable);
        context.put("importList", getImportList(genTable));
        context.put("permissionPrefix", getPermissionPrefix(moduleName, businessName));
        context.put("columns", genTable.getColumns());
        context.put("table", genTable);
        context.put("dicts", dicts);
        context.put("dictsNoSymbol", StringUtils.replace(dicts, "'", StringUtils.EMPTY));
        setColumnFeatureContext(context, genTable, dicts);
        // 向模板上下文写入菜单相关变量
        String options = genTable.getOptions();
        Dict paramsObj = JsonUtils.parseMap(options);
        if (ObjectUtil.isNull(paramsObj)) {
            paramsObj = new Dict();
        }
        String parentMenuId = getParentMenuId(paramsObj);
        context.put("parentMenuId", parentMenuId);
        boolean enableExport = getBooleanOption(paramsObj, GenConstants.ENABLE_EXPORT, true);
        boolean enableStatus = getBooleanOption(paramsObj, GenConstants.ENABLE_STATUS, false);
        boolean enableUnique = getBooleanOption(paramsObj, GenConstants.ENABLE_UNIQUE, false);
        boolean enableSort = getBooleanOption(paramsObj, GenConstants.ENABLE_SORT, false);
        GenTableColumn statusColumn = getColumn(genTable, paramsObj.getStr(GenConstants.STATUS_FIELD));
        GenTableColumn sortColumn = getColumn(genTable, paramsObj.getStr(GenConstants.SORT_FIELD));
        List<GenTableColumn> uniqueColumns = getColumns(genTable, paramsObj.get(GenConstants.UNIQUE_FIELDS));
        context.put("enableExport", enableExport);
        context.put("enableStatus", enableStatus && ObjectUtil.isNotNull(statusColumn));
        context.put("statusColumn", statusColumn);
        context.put("statusField", ObjectUtil.isNotNull(statusColumn) ? statusColumn.getJavaField() : StringUtils.EMPTY);
        context.put("enableUnique", enableUnique && CollUtil.isNotEmpty(uniqueColumns));
        context.put("uniqueColumns", uniqueColumns);
        context.put("enableSort", enableSort && ObjectUtil.isNotNull(sortColumn));
        context.put("sortColumn", sortColumn);
        context.put("sortField", ObjectUtil.isNotNull(sortColumn) ? sortColumn.getJavaField() : StringUtils.EMPTY);
        context.put("statusActiveValue", ObjectUtil.isNotNull(statusColumn) ? statusColumn.getSwitchActiveValue() : StringUtils.EMPTY);
        context.put("statusInactiveValue", ObjectUtil.isNotNull(statusColumn) ? statusColumn.getSwitchInactiveValue() : StringUtils.EMPTY);

        // 向树形模板上下文写入树字段相关变量
        if (GenConstants.TPL_TREE.equals(tplCategory)) {
            setTreeContext(context, genTable, paramsObj);
        }

        return context;
    }

    /**
     * 没有注释的字段用字段名作为注释。模板中的代码注释、表单标签与文案都取自字段注释，
     * 数据库中的列没有注释（或编辑时清空了注释）时，渲染会因取不到值而失败
     *
     * @param genTable 代码生成业务表对象
     */
    private static void fillBlankColumnComments(GenTable genTable) {
        if (CollUtil.isEmpty(genTable.getColumns())) {
            return;
        }
        for (GenTableColumn column : genTable.getColumns()) {
            if (StringUtils.isBlank(column.getColumnComment())) {
                column.setColumnComment(column.getColumnName());
            }
        }
    }

    /**
     * 写入模板中常用的列派生开关，避免在模板内反复扫描和处理。
     *
     * @param context  模板上下文
     * @param genTable 代码生成业务表对象
     * @param dicts    字典类型表达式
     */
    private static void setColumnFeatureContext(Dict context, GenTable genTable, String dicts) {
        boolean hasBetween = false;
        boolean needImagePreview = false;
        boolean needImageUpload = false;
        boolean needFileUpload = false;
        boolean needEditor = false;
        boolean needCheckbox = false;
        boolean needSelect = false;
        boolean needTextArea = false;
        boolean needDigit = false;
        boolean needDateField = false;
        boolean needSwitchField = false;
        boolean needParseTime = false;
        String firstTreeListField = StringUtils.EMPTY;
        for (GenTableColumn column : genTable.getColumns()) {
            boolean writable = column.isInsert() || column.isEdit();
            hasBetween = hasBetween || column.isQuery() && StringUtils.equals(column.getQueryType(), GenConstants.QUERY_BETWEEN);
            needImagePreview = needImagePreview || column.isList() && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_IMAGE_UPLOAD);
            needImageUpload = needImageUpload || writable && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_IMAGE_UPLOAD);
            needFileUpload = needFileUpload || writable && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_FILE_UPLOAD);
            needEditor = needEditor || writable && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_EDITOR);
            needCheckbox = needCheckbox || writable && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_CHECKBOX);
            needSelect = needSelect || (writable || column.isQuery()) && StringUtils.equalsAny(column.getHtmlType(),
                GenConstants.HTML_SELECT, GenConstants.HTML_RADIO, GenConstants.HTML_SWITCH);
            needTextArea = needTextArea || writable && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_TEXTAREA);
            needDigit = needDigit || writable && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_INPUT_NUMBER);
            needDateField = needDateField || writable && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_DATETIME);
            needSwitchField = needSwitchField || (writable || column.isList()) && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_SWITCH);
            needParseTime = needParseTime || column.isList() && StringUtils.equals(column.getHtmlType(), GenConstants.HTML_DATETIME);
            if (StringUtils.isBlank(firstTreeListField) && column.isList()) {
                firstTreeListField = column.getJavaField();
            }
        }
        boolean needDateRange = hasDateRangeQuery(genTable);
        context.put("hasBetween", hasBetween);
        context.put("needAddDateRange", needDateRange);
        context.put("needDateRange", needDateRange);
        context.put("needDict", StringUtils.isNotBlank(dicts));
        context.put("needImagePreview", needImagePreview);
        context.put("needImageUpload", needImageUpload);
        context.put("needFileUpload", needFileUpload);
        context.put("needEditor", needEditor);
        context.put("needCheckbox", needCheckbox);
        context.put("needSelect", needSelect);
        context.put("needTextArea", needTextArea);
        context.put("needDigit", needDigit);
        context.put("needDateField", needDateField);
        context.put("needSwitchField", needSwitchField);
        context.put("needParseTime", needParseTime);
        context.put("firstTreeListField", firstTreeListField);
    }

    private static boolean hasDateRangeQuery(GenTable genTable) {
        for (GenTableColumn column : genTable.getColumns()) {
            if (column.isDateRangeQuery()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 向树形模板上下文写入树字段相关变量。
     *
     * @param context   模板上下文
     * @param genTable  代码生成业务表对象
     * @param paramsObj 已解析的 options 参数（避免重复解析）
     */
    public static void setTreeContext(Dict context, GenTable genTable, Dict paramsObj) {
        String treeCode = getTreeCode(paramsObj);
        String treeParentCode = getTreeParentCode(paramsObj);
        String treeName = getTreeName(paramsObj);
        GenTableColumn treeParentColumn = getColumn(genTable, paramsObj.getStr(GenConstants.TREE_PARENT_CODE));
        GenTableColumn treeAncestorsColumn = getColumn(genTable, paramsObj.getStr(GenConstants.TREE_ANCESTORS));
        GenTableColumn treeOrderColumn = getColumn(genTable, paramsObj.getStr(GenConstants.TREE_ORDER_FIELD));
        String treeRootValue = getTreeRootValue(paramsObj, treeParentColumn);

        context.put("treeCode", treeCode);
        context.put("treeParentCode", treeParentCode);
        context.put("treeName", treeName);
        context.put("treeParentColumn", treeParentColumn);
        context.put("treeAncestorsField", ObjectUtil.isNotNull(treeAncestorsColumn) ? treeAncestorsColumn.getJavaField() : StringUtils.EMPTY);
        context.put("treeAncestorsColumnName", ObjectUtil.isNotNull(treeAncestorsColumn) ? treeAncestorsColumn.getColumnName() : StringUtils.EMPTY);
        context.put("treeOrderField", ObjectUtil.isNotNull(treeOrderColumn) ? treeOrderColumn.getJavaField() : StringUtils.EMPTY);
        context.put("treeOrderColumn", treeOrderColumn);
        context.put("treeRootValue", treeRootValue);
        context.put("treeRootValueJavaLiteral", getJavaLiteral(treeParentColumn, treeRootValue));
        context.put("treeRootValueTsLiteral", getTsLiteral(treeParentColumn, treeRootValue));
        context.put("treeParentCap", StringUtils.capitalize(treeParentCode));
        context.put("treeAncestorsCap", StringUtils.capitalize(ObjectUtil.isNotNull(treeAncestorsColumn) ? treeAncestorsColumn.getJavaField() : StringUtils.EMPTY));
        context.put("treeOrderCap", StringUtils.capitalize(ObjectUtil.isNotNull(treeOrderColumn) ? treeOrderColumn.getJavaField() : StringUtils.EMPTY));
        String expandTreeName = paramsObj.getStr(GenConstants.TREE_NAME);
        int expandColumn = 0;
        for (GenTableColumn column : genTable.getColumns()) {
            if (column.isList()) {
                expandColumn++;
                if (column.getColumnName().equals(expandTreeName)) {
                    break;
                }
            }
        }
        context.put("expandColumn", expandColumn);
        if (paramsObj.containsKey(GenConstants.TREE_PARENT_CODE)) {
            context.put("tree_parent_code", paramsObj.get(GenConstants.TREE_PARENT_CODE));
        }
        if (paramsObj.containsKey(GenConstants.TREE_NAME)) {
            context.put("tree_name", paramsObj.get(GenConstants.TREE_NAME));
        }
    }

    /**
     * 获取模板信息
     *
     * @param tplCategory 生成模板分类：crud 或 tree
     * @param dsName      数据源名称
     * @return 模板列表
     */
    public static List<PathNamedTemplate> getTemplateList(String tplCategory, String dsName) {
        List<PathNamedTemplate> templates = new ArrayList<>();
        // 后端源码模板
        templates.add(TEMPLATE_MAPPER.get(GenConstants.JAVA_DOMAIN_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.JAVA_VO_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.JAVA_BO_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.JAVA_MAPPER_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.JAVA_SERVICE_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.JAVA_SERVICE_IMPL_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.JAVA_CONTROLLER_TEMPLATE_PATH));
        // MyBatis MapperXML 模板
        templates.add(TEMPLATE_MAPPER.get(GenConstants.XML_MAPPER_TEMPLATE_PATH));
        // 数据库模板
        DataBaseType dataBaseType = DataBaseHelper.getDataBaseType(dsName);
        if (dataBaseType.isOracle()) {
            templates.add(TEMPLATE_MAPPER.get(GenConstants.SQL_ORACLE_TEMPLATE_PATH));
        } else if (dataBaseType.isPostgreSql()) {
            templates.add(TEMPLATE_MAPPER.get(GenConstants.SQL_POSTGRES_TEMPLATE_PATH));
        } else if (dataBaseType.isSqlServer()) {
            templates.add(TEMPLATE_MAPPER.get(GenConstants.SQL_SQLSERVER_TEMPLATE_PATH));
        } else {
            // 默认使用MySQL模板
            templates.add(TEMPLATE_MAPPER.get(GenConstants.SQL_MYSQL_TEMPLATE_PATH));
        }
        // 前端模板
        templates.addAll(getFrontendTemplateList(tplCategory));
        return templates;
    }

    /**
     * 获取前端（Vben）模板列表（不涉及数据库，可单独测试）
     *
     * @param tplCategory 生成模板分类：crud 或 tree
     * @return 前端模板列表
     */
    public static List<PathNamedTemplate> getFrontendTemplateList(String tplCategory) {
        List<PathNamedTemplate> templates = new ArrayList<>();
        templates.add(TEMPLATE_MAPPER.get(GenConstants.VBEN_API_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.VBEN_DATA_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.VBEN_DRAWER_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.TPL_TREE.equals(tplCategory)
            ? GenConstants.VBEN_INDEX_TREE_TEMPLATE_PATH : GenConstants.VBEN_INDEX_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.VBEN_LOCALE_ZH_TEMPLATE_PATH));
        templates.add(TEMPLATE_MAPPER.get(GenConstants.VBEN_LOCALE_EN_TEMPLATE_PATH));
        return templates;
    }

    /**
     * 获取文件名
     *
     * @param template 模板路径
     * @param genTable 代码生成业务表对象
     * @return 模板对应的目标文件相对路径
     */
    public static String getFileName(String template, GenTable genTable) {
        // 文件名称
        String fileName = "";
        // 包路径
        String packageName = genTable.getPackageName();
        // 模块名
        String moduleName = genTable.getModuleName();
        // 大写类名
        String className = genTable.getClassName();
        // 业务名称
        String businessName = genTable.getBusinessName();

        String javaPath = PROJECT_PATH + "/" + StringUtils.replace(packageName, ".", "/");
        String mybatisPath = MYBATIS_PATH + "/" + moduleName;
        // templatePath
        // genFilePathFormat
        if (template.contains("domain.java.")) {
            fileName = StringUtils.format("{}/domain/{}.java", javaPath, className);
        } else if (template.contains("vo.java.")) {
            fileName = StringUtils.format("{}/domain/vo/{}Vo.java", javaPath, className);
        } else if (template.contains("bo.java.")) {
            fileName = StringUtils.format("{}/domain/bo/{}Bo.java", javaPath, className);
        } else if (template.contains("mapper.java.")) {
            fileName = StringUtils.format("{}/mapper/{}Mapper.java", javaPath, className);
        } else if (template.contains("service.java.")) {
            fileName = StringUtils.format("{}/service/I{}Service.java", javaPath, className);
        } else if (template.contains("serviceImpl.java.")) {
            fileName = StringUtils.format("{}/service/impl/{}ServiceImpl.java", javaPath, className);
        } else if (template.contains("controller.java.")) {
            fileName = StringUtils.format("{}/controller/{}Controller.java", javaPath, className);
        } else if (template.contains("mapper.xml.")) {
            fileName = StringUtils.format("{}/{}Mapper.xml", mybatisPath, className);
        } else if (template.startsWith(GenConstants.VBEN_TEMPLATE_ROOT_PATH + "/")) {
            fileName = getVbenFileName(template, moduleName, businessName);
        } else if (template.contains("sql.")) {
            fileName = businessName + "Menu.sql";
        }
        return fileName;
    }

    /**
     * Vben 模板的输出路径，与前端仓库 apps/web-antd/src 下的目录一一对应
     */
    private static String getVbenFileName(String template, String moduleName, String businessName) {
        String root = GenConstants.FRONTEND_TYPE_VBEN;
        String pageDir = StringUtils.format("{}/views/{}/{}", root, moduleName, businessName);
        String kebabName = StringUtils.replace(StringUtils.toUnderScoreCase(businessName), "_", "-");
        return switch (template) {
            case GenConstants.VBEN_API_TEMPLATE_PATH -> StringUtils.format("{}/api/{}/{}.ts", root, moduleName, businessName);
            case GenConstants.VBEN_DATA_TEMPLATE_PATH -> pageDir + "/data.ts";
            case GenConstants.VBEN_DRAWER_TEMPLATE_PATH -> pageDir + "/" + kebabName + "-drawer.vue";
            case GenConstants.VBEN_INDEX_TEMPLATE_PATH, GenConstants.VBEN_INDEX_TREE_TEMPLATE_PATH -> pageDir + "/index.vue";
            case GenConstants.VBEN_LOCALE_ZH_TEMPLATE_PATH ->
                StringUtils.format("{}/locales/langs/zh-CN/{}/{}.json", root, moduleName, businessName);
            case GenConstants.VBEN_LOCALE_EN_TEMPLATE_PATH ->
                StringUtils.format("{}/locales/langs/en-US/{}/{}.json", root, moduleName, businessName);
            default -> throw new ServiceException(StringUtils.format("未知的 Vben 模板: {}", template));
        };
    }

    /**
     * 获取包前缀
     *
     * @param packageName 包名称
     * @return 包前缀名称
     */
    public static String getPackagePrefix(String packageName) {
        int lastIndex = packageName.lastIndexOf(".");
        return StringUtils.substring(packageName, 0, lastIndex);
    }

    /**
     * 根据列类型获取导入包
     *
     * @param genTable 业务表对象
     * @return 返回需要导入的包列表
     */
    public static HashSet<String> getImportList(GenTable genTable) {
        List<GenTableColumn> columns = genTable.getColumns();
        HashSet<String> importList = new HashSet<>();
        for (GenTableColumn column : columns) {
            if (needImportColumn(column) && GenConstants.TYPE_DATE.equals(column.getJavaType())) {
                importList.add("java.time.LocalDateTime");
                importList.add("com.fasterxml.jackson.annotation.JsonFormat");
            } else if (needImportColumn(column) && GenConstants.TYPE_BIGDECIMAL.equals(column.getJavaType())) {
                importList.add("java.math.BigDecimal");
            }
        }
        return importList;
    }

    /**
     * 判断列是否需要生成类型导入。
     *
     * @param column 列对象
     * @return 需要生成类型导入返回 {@code true}
     */
    private static boolean needImportColumn(GenTableColumn column) {
        return !column.isSuperColumn() || column.isQuery();
    }

    /**
     * 根据列类型获取字典组
     *
     * @param genTable 业务表对象
     * @return 返回字典组
     */
    public static String getDicts(GenTable genTable) {
        List<GenTableColumn> columns = genTable.getColumns();
        Set<String> dicts = new HashSet<>();
        addDicts(dicts, columns);
        return StringUtils.join(dicts, ", ");
    }

    /**
     * 添加字典列表
     *
     * @param dicts   字典列表
     * @param columns 列集合
     */
    public static void addDicts(Set<String> dicts, List<GenTableColumn> columns) {
        for (GenTableColumn column : columns) {
            if (!column.isSuperColumn() && StringUtils.isNotEmpty(column.getDictType()) && StringUtils.equalsAny(
                column.getHtmlType(),
                GenConstants.HTML_SELECT, GenConstants.HTML_RADIO, GenConstants.HTML_CHECKBOX, GenConstants.HTML_SWITCH)) {
                dicts.add("'" + column.getDictType() + "'");
            }
        }
    }

    /**
     * 获取权限前缀
     *
     * @param moduleName   模块名称
     * @param businessName 业务名称
     * @return 返回权限前缀
     */
    public static String getPermissionPrefix(String moduleName, String businessName) {
        return StringUtils.format("{}:{}", moduleName, businessName);
    }

    /**
     * 获取上级菜单ID字段
     *
     * @param paramsObj 生成其他选项
     * @return 上级菜单ID字段
     */
    public static String getParentMenuId(Dict paramsObj) {
        if (CollUtil.isNotEmpty(paramsObj) && paramsObj.containsKey(GenConstants.PARENT_MENU_ID)
            && StringUtils.isNotEmpty(paramsObj.getStr(GenConstants.PARENT_MENU_ID))) {
            return paramsObj.getStr(GenConstants.PARENT_MENU_ID);
        }
        return DEFAULT_PARENT_MENU_ID;
    }

    /**
     * 获取树编码
     *
     * @param paramsObj 生成其他选项
     * @return 树编码
     */
    public static String getTreeCode(Map<String, Object> paramsObj) {
        if (CollUtil.isNotEmpty(paramsObj) && paramsObj.containsKey(GenConstants.TREE_CODE)) {
            return StringUtils.toCamelCase(Convert.toStr(paramsObj.get(GenConstants.TREE_CODE)));
        }
        return StringUtils.EMPTY;
    }

    /**
     * 获取树父编码
     *
     * @param paramsObj 生成其他选项
     * @return 树父编码
     */
    public static String getTreeParentCode(Dict paramsObj) {
        if (CollUtil.isNotEmpty(paramsObj) && paramsObj.containsKey(GenConstants.TREE_PARENT_CODE)) {
            return StringUtils.toCamelCase(paramsObj.getStr(GenConstants.TREE_PARENT_CODE));
        }
        return StringUtils.EMPTY;
    }

    /**
     * 获取树名称
     *
     * @param paramsObj 生成其他选项
     * @return 树名称
     */
    public static String getTreeName(Dict paramsObj) {
        if (CollUtil.isNotEmpty(paramsObj) && paramsObj.containsKey(GenConstants.TREE_NAME)) {
            return StringUtils.toCamelCase(paramsObj.getStr(GenConstants.TREE_NAME));
        }
        return StringUtils.EMPTY;
    }

    /**
     * 获取树根节点值。
     *
     * @param paramsObj        其他选项
     * @param treeParentColumn 父节点字段
     * @return 树根节点值
     */
    public static String getTreeRootValue(Dict paramsObj, GenTableColumn treeParentColumn) {
        String defaultValue = "0";
        if (ObjectUtil.isNotNull(treeParentColumn) && StringUtils.equals(treeParentColumn.getJavaType(), GenConstants.TYPE_STRING)) {
            defaultValue = "0";
        }
        if (CollUtil.isNotEmpty(paramsObj) && paramsObj.containsKey(GenConstants.TREE_ROOT_VALUE)) {
            return StringUtils.blankToDefault(paramsObj.getStr(GenConstants.TREE_ROOT_VALUE), defaultValue);
        }
        return defaultValue;
    }

    /**
     * 读取布尔类型生成选项。
     *
     * @param paramsObj    生成选项
     * @param key          选项键
     * @param defaultValue 默认值
     * @return 选项值
     */
    private static boolean getBooleanOption(Dict paramsObj, String key, boolean defaultValue) {
        if (CollUtil.isEmpty(paramsObj) || !paramsObj.containsKey(key)) {
            return defaultValue;
        }
        return Convert.toBool(paramsObj.get(key), defaultValue);
    }

    /**
     * 根据字段名查找业务表字段。
     *
     * @param genTable 业务表
     * @param field    字段名称或 Java 属性名
     * @return 业务表字段
     */
    private static GenTableColumn getColumn(GenTable genTable, String field) {
        if (StringUtils.isBlank(field) || CollUtil.isEmpty(genTable.getColumns())) {
            return null;
        }
        for (GenTableColumn column : genTable.getColumns()) {
            if (StringUtils.equalsAny(field, column.getColumnName(), column.getJavaField())) {
                return column;
            }
        }
        return null;
    }

    /**
     * 根据字段配置查找业务表字段列表。
     *
     * @param genTable    业务表
     * @param fieldValues 字段配置值
     * @return 业务表字段列表
     */
    private static List<GenTableColumn> getColumns(GenTable genTable, Object fieldValues) {
        List<String> fields = new ArrayList<>();
        if (fieldValues instanceof Collection<?> collection) {
            collection.stream().map(Convert::toStr).forEach(fields::add);
        } else if (ObjectUtil.isNotNull(fieldValues)) {
            fields.addAll(StringUtils.str2List(Convert.toStr(fieldValues), StringUtils.SEPARATOR, true, true));
        }
        List<GenTableColumn> columns = new ArrayList<>();
        for (String field : fields) {
            GenTableColumn column = getColumn(genTable, field);
            if (ObjectUtil.isNotNull(column)) {
                columns.add(column);
            }
        }
        return columns;
    }

    /**
     * 转换为 Java 字面量。
     *
     * @param column 业务表字段
     * @param value  字段值
     * @return Java 字面量
     */
    private static String getJavaLiteral(GenTableColumn column, String value) {
        if (ObjectUtil.isNull(column) || StringUtils.isBlank(value)) {
            return "null";
        }
        if (StringUtils.equals(column.getJavaType(), GenConstants.TYPE_LONG)) {
            return value + "L";
        }
        if (StringUtils.equals(column.getJavaType(), GenConstants.TYPE_FLOAT)) {
            return value + "F";
        }
        if (StringUtils.equals(column.getJavaType(), GenConstants.TYPE_DOUBLE)) {
            return value + "D";
        }
        if (StringUtils.equals(column.getJavaType(), GenConstants.TYPE_BIGDECIMAL)) {
            return "new java.math.BigDecimal(\"" + value + "\")";
        }
        if (StringUtils.equals(column.getJavaType(), GenConstants.TYPE_INTEGER)) {
            return value;
        }
        return "\"" + value + "\"";
    }

    /**
     * 转换为 TypeScript 字面量。
     *
     * @param column 业务表字段
     * @param value  字段值
     * @return TypeScript 字面量
     */
    private static String getTsLiteral(GenTableColumn column, String value) {
        if (ObjectUtil.isNull(column) || StringUtils.isBlank(value)) {
            return "undefined";
        }
        if (StringUtils.equalsAny(column.getJavaType(), GenConstants.TYPE_LONG, GenConstants.TYPE_INTEGER,
            GenConstants.TYPE_DOUBLE, GenConstants.TYPE_FLOAT, GenConstants.TYPE_BIGDECIMAL)) {
            return value;
        }
        return "'" + value + "'";
    }
}
