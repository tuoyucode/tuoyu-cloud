package org.tuoyu.gen.service;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.DateUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.enums.DataBaseType;
import org.tuoyu.common.mybatis.helper.DataBaseHelper;
import org.tuoyu.common.mybatis.utils.IdGeneratorUtil;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按业务表的配置生成代码：检查配置、清洗说明文字、构建模板上下文，按固定的顺序渲染后端、菜单 SQL 与前端模板。
 * <p>
 * 每个结果带模板路径（预览的键）与 zip 中的路径。说明文字（功能名、作者、字段注释）会写进 Java 字符串与注释、
 * SQL 字符串，渲染前去掉引号、反斜杠、反引号、换行与注释结束符；标识符类的配置由 {@link GenConfigChecker} 检查。
 *
 * @author tuoyu
 */
final class GenCodeGenerator {

    static final String DEFAULT_FUNCTION_NAME = "【请填写功能名称】";

    private static final int MENU_ID_COUNT = 6;

    private static final String DEFAULT_ROOT_VALUE = "0";

    private static final String TYPE_STRING = "String";

    private static final String QUERY_EQ = "EQ";

    private static final String QUERY_BETWEEN = "BETWEEN";

    private static final Set<String> COMPARE_QUERY_TYPES = Set.of("GT", "GE", "LT", "LE");

    private GenCodeGenerator() {
    }

    /**
     * 生成的一个文件
     *
     * @param template 模板路径（预览的键）
     * @param path     zip 中的路径
     * @param content  内容
     */
    record GeneratedFile(String template, String path, String content) {
    }

    /**
     * 模板与输出路径
     */
    private record Target(String template, String path) {
    }

    /**
     * 生成一张表的代码
     *
     * @param table 已保存的业务表（带字段）；渲染使用的说明文字、菜单编号与主键写在这个对象上，不写回数据库
     * @return 按固定顺序生成的文件
     */
    static List<GeneratedFile> generate(GenTable table) {
        List<GenTableColumn> columns = table.getColumns();
        if (columns == null || columns.isEmpty()) {
            throw new ServiceException("表【" + table.getTableName() + "】字段为空，请检查表结构");
        }
        GenOptions options = GenOptions.parse(table.getOptions());
        GenConfigChecker.checkGenerate(table, options);
        String frontendType = table.getFrontendType();
        if (StringUtils.isBlank(frontendType)) {
            frontendType = GenTableDefaults.FRONTEND_ELE;
        } else if (!GenTableDefaults.FRONTEND_ELE.equals(frontendType) && !GenTableDefaults.FRONTEND_ANTD.equals(frontendType)) {
            throw new ServiceException("不支持的前端模板类型: " + frontendType);
        }

        List<Long> menuIds = new ArrayList<>(MENU_ID_COUNT);
        for (int i = 0; i < MENU_ID_COUNT; i++) {
            menuIds.add(IdGeneratorUtil.nextLongId());
        }
        table.setMenuIds(menuIds);
        List<GenTableColumn> sorted = new ArrayList<>(columns);
        sorted.sort(Comparator.comparing(GenTableColumn::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
        table.setColumns(sorted);
        table.setPkColumn(sorted.stream().filter(GenTableColumn::isPk).findFirst().orElse(sorted.getFirst()));
        sanitize(table);

        Map<String, Object> context = buildContext(table, options);
        DataBaseType dataBaseType = DataBaseHelper.getDataBaseType(table.getDataName());
        List<GeneratedFile> files = new ArrayList<>();
        for (Target target : targets(table, frontendType, dataBaseType)) {
            files.add(new GeneratedFile(target.template(), target.path(), render(target.template(), context)));
        }
        return files;
    }

    /**
     * 清洗说明文字：回车、换行、制表符换成空格，删除引号、反斜杠与反引号，反复删除注释结束符，去掉两端空白
     */
    static String clean(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\r', '\n', '\t' -> builder.append(' ');
                case '\'', '"', '\\', '`' -> {
                    // 删除：这些字符可以结束 Java、SQL 字符串或改变其中的转义
                }
                default -> builder.append(c);
            }
        }
        String result = builder.toString();
        while (result.contains("*/")) {
            result = result.replace("*/", "");
        }
        return result.trim();
    }

    private static void sanitize(GenTable table) {
        String functionName = clean(table.getFunctionName());
        table.setFunctionName(StringUtils.isBlank(functionName) ? DEFAULT_FUNCTION_NAME : functionName);
        String author = clean(table.getFunctionAuthor());
        table.setFunctionAuthor(author == null ? "" : author);
        for (GenTableColumn column : table.getColumns()) {
            String comment = clean(column.getColumnComment());
            if (StringUtils.isBlank(comment)) {
                // 没有注释时以列名作为说明，同样清洗（列名来自数据库，可能带引号）
                comment = StringUtils.defaultString(clean(column.getColumnName()));
            }
            column.setColumnComment(comment);
        }
    }

    private static List<Target> targets(GenTable table, String frontendType, DataBaseType dataBaseType) {
        String javaDir = "main/java/" + table.getPackageName().replace('.', '/') + "/";
        String className = table.getClassName();
        String moduleName = table.getModuleName();
        String businessName = table.getBusinessName();
        String kebab = StringUtils.toUnderScoreCase(businessName).replace('_', '-');
        String pageDir = "vben/apps/web-" + frontendType + "/src/views/" + moduleName + "/" + businessName + "/";
        String sqlTemplate;
        if (dataBaseType.isOracle()) {
            sqlTemplate = "fm/sql/oracle.sql.ftl";
        } else if (dataBaseType.isPostgreSql()) {
            sqlTemplate = "fm/sql/postgres.sql.ftl";
        } else {
            sqlTemplate = "fm/sql/mysql.sql.ftl";
        }
        String vben = "fm/vben/" + frontendType + "/";
        return List.of(
            new Target("fm/java/domain.java.ftl", javaDir + "domain/" + className + ".java"),
            new Target("fm/java/vo.java.ftl", javaDir + "domain/vo/" + className + "Vo.java"),
            new Target("fm/java/bo.java.ftl", javaDir + "domain/bo/" + className + "Bo.java"),
            new Target("fm/java/mapper.java.ftl", javaDir + "mapper/" + className + "Mapper.java"),
            new Target("fm/java/service.java.ftl", javaDir + "service/I" + className + "Service.java"),
            new Target("fm/java/serviceImpl.java.ftl", javaDir + "service/impl/" + className + "ServiceImpl.java"),
            new Target("fm/java/controller.java.ftl", javaDir + "controller/" + className + "Controller.java"),
            new Target("fm/xml/mapper.xml.ftl", "main/resources/mapper/" + moduleName + "/" + className + "Mapper.xml"),
            new Target(sqlTemplate, businessName + "Menu.sql"),
            new Target("fm/vben/api.ts.ftl", "vben/packages/shared/src/api/" + moduleName + "/" + businessName + ".ts"),
            new Target(vben + "data.ts.ftl", pageDir + "data.ts"),
            new Target(vben + "drawer.vue.ftl", pageDir + kebab + "-drawer.vue"),
            new Target(vben + (table.isTree() ? "index-tree.vue.ftl" : "index.vue.ftl"), pageDir + "index.vue"),
            new Target("fm/vben/locale-zh-CN.json.ftl",
                "vben/packages/shared/src/locales/langs/zh-CN/" + moduleName + "/" + businessName + ".json"),
            new Target("fm/vben/locale-en-US.json.ftl",
                "vben/packages/shared/src/locales/langs/en-US/" + moduleName + "/" + businessName + ".json"));
    }

    /**
     * 模板上下文：前端模板（fm/vben）读取的键在顶层，后端模板另外使用 {@code gen} 中的派生信息
     */
    private static Map<String, Object> buildContext(GenTable table, GenOptions options) {
        List<GenTableColumn> columns = table.getColumns();
        String className = table.getClassName();
        String businessName = table.getBusinessName();
        String moduleName = table.getModuleName();

        Map<String, Object> context = new HashMap<>();
        context.put("table", table);
        context.put("columns", columns);
        context.put("pkColumn", table.getPkColumn());
        context.put("ClassName", className);
        context.put("className", StringUtils.uncapitalize(className));
        context.put("BusinessName", StringUtils.capitalize(businessName));
        context.put("businessName", businessName);
        context.put("moduleName", moduleName);
        context.put("functionName", table.getFunctionName());
        context.put("permissionPrefix", moduleName + ":" + businessName);
        boolean enableExport = options.bool(GenOptions.ENABLE_EXPORT, true);
        context.put("enableExport", enableExport);
        GenTableColumn statusColumn = GenOptions.findColumn(columns, options.string(GenOptions.STATUS_FIELD));
        boolean enableStatus = options.bool(GenOptions.ENABLE_STATUS, false) && statusColumn != null;
        context.put("statusColumn", statusColumn);
        context.put("enableStatus", enableStatus);
        context.put("statusField", statusColumn == null ? "" : statusColumn.getJavaField());
        context.put("statusActiveValue", statusColumn == null ? "" : statusColumn.getSwitchActiveValue());
        context.put("statusInactiveValue", statusColumn == null ? "" : statusColumn.getSwitchInactiveValue());
        if (table.isTree()) {
            context.put("treeCode", camelOption(options, GenOptions.TREE_CODE));
            context.put("treeParentCode", camelOption(options, GenOptions.TREE_PARENT_CODE));
            context.put("treeName", camelOption(options, GenOptions.TREE_NAME));
        }
        context.put("gen", backendContext(table, options, enableExport));
        return context;
    }

    private static String camelOption(GenOptions options, String key) {
        String value = options.string(key);
        return value == null ? "" : StringUtils.toCamelCase(value);
    }

    /**
     * 后端模板（fm/java、fm/xml、fm/sql）使用的派生信息
     */
    private static Map<String, Object> backendContext(GenTable table, GenOptions options, boolean enableExport) {
        List<GenTableColumn> columns = table.getColumns();
        GenTableColumn pk = table.getPkColumn();
        Map<String, Object> gen = new HashMap<>();
        gen.put("packageName", table.getPackageName());
        gen.put("tableName", table.getTableName());
        gen.put("author", table.getFunctionAuthor());
        gen.put("datetime", DateUtils.now());
        gen.put("baseEntity", table.isTenant() ? "TenantEntity" : "BaseEntity");
        String parentMenuId = options.string(GenOptions.PARENT_MENU_ID);
        gen.put("parentMenuId", StringUtils.isBlank(parentMenuId) ? String.valueOf(GenOptions.DEFAULT_PARENT_MENU_ID) : parentMenuId);

        // 实体：不声明实体基类中已有的字段
        List<GenTableColumn> domainColumns = columns.stream()
            .filter(column -> !table.isSuperColumn(column.getJavaField())).toList();
        gen.put("domainColumns", domainColumns);
        gen.put("domainImports", typeImports(domainColumns));

        // 视图对象：列表字段，以及修改时抽屉需要回填的字段
        List<GenTableColumn> voColumns = columns.stream()
            .filter(column -> column.isList()
                || (column.isEdit() && !column.isPk() && !table.isSuperColumn(column.getJavaField())))
            .toList();
        gen.put("voColumns", voColumns);
        gen.put("voImports", typeImports(voColumns));
        gen.put("voDict", voColumns.stream().anyMatch(column -> column.isList()
            && (StringUtils.isNotBlank(column.getDictType()) || column.getColumnComment().contains("（"))));
        gen.put("voTranslation", voColumns.stream().anyMatch(column -> "imageUpload".equals(column.getHtmlType())));

        // 业务对象：主键、查询条件与新增、修改的字段
        List<GenTableColumn> boColumns = columns.stream()
            .filter(column -> column.isPk() || column.isQuery()
                || (!table.isSuperColumn(column.getJavaField()) && (column.isInsert() || column.isEdit())))
            .toList();
        List<Map<String, Object>> boItems = new ArrayList<>();
        boolean boValidation = false;
        for (GenTableColumn column : boColumns) {
            Map<String, Object> item = new HashMap<>();
            item.put("column", column);
            String validation = "";
            String groups = "";
            if (column.isRequired() && !table.isSuperColumn(column.getJavaField()) && (column.isInsert() || column.isEdit())) {
                validation = TYPE_STRING.equals(column.getJavaType()) ? "NotBlank" : "NotNull";
                if (column.isInsert() && column.isEdit()) {
                    groups = "OnCreate.class, OnUpdate.class";
                } else {
                    groups = column.isInsert() ? "OnCreate.class" : "OnUpdate.class";
                }
                boValidation = true;
            }
            item.put("validation", validation);
            item.put("groups", groups);
            boItems.add(item);
        }
        gen.put("boColumns", boItems);
        gen.put("boValidation", boValidation);
        gen.put("boImports", typeImports(boColumns));

        // 查询条件
        List<Map<String, Object>> queryItems = new ArrayList<>();
        boolean hasBetween = false;
        boolean stringCompare = false;
        for (GenTableColumn column : columns) {
            if (!column.isQuery()) {
                continue;
            }
            String type = StringUtils.isBlank(column.getQueryType()) ? QUERY_EQ : column.getQueryType();
            boolean stringType = TYPE_STRING.equals(column.getJavaType());
            hasBetween |= QUERY_BETWEEN.equals(type);
            stringCompare |= stringType && COMPARE_QUERY_TYPES.contains(type);
            Map<String, Object> item = new HashMap<>();
            item.put("cap", column.getCapJavaField());
            item.put("type", type);
            item.put("string", stringType);
            queryItems.add(item);
        }
        gen.put("queryColumns", queryItems);
        gen.put("hasBetween", hasBetween);

        // 组合唯一：选项开启时取找得到的字段（数组或逗号分隔的字符串）
        List<GenTableColumn> uniqueColumns = new ArrayList<>();
        if (options.bool(GenOptions.ENABLE_UNIQUE, false)) {
            for (String name : options.stringList(GenOptions.UNIQUE_FIELDS)) {
                GenTableColumn column = GenOptions.findColumn(columns, StringUtils.trim(name));
                if (column != null && !uniqueColumns.contains(column)) {
                    uniqueColumns.add(column);
                }
            }
        }
        gen.put("uniqueColumns", uniqueColumns);
        gen.put("enableUnique", !uniqueColumns.isEmpty());

        GenTableColumn sortColumn = options.bool(GenOptions.ENABLE_SORT, false)
            ? GenOptions.findColumn(columns, options.string(GenOptions.SORT_FIELD)) : null;
        gen.put("sortColumn", sortColumn);
        gen.put("enableSort", sortColumn != null);

        // 树表：上级、祖级与树排序字段，根节点值
        boolean tree = table.isTree();
        GenTableColumn parentColumn = tree ? GenOptions.findColumn(columns, options.string(GenOptions.TREE_PARENT_CODE)) : null;
        GenTableColumn ancestorsColumn = tree ? GenOptions.findColumn(columns, options.string(GenOptions.TREE_ANCESTORS)) : null;
        GenTableColumn treeOrderColumn = tree ? GenOptions.findColumn(columns, options.string(GenOptions.TREE_ORDER_FIELD)) : null;
        boolean treeAncestors = parentColumn != null && ancestorsColumn != null;
        String rootValue = StringUtils.blankToDefault(options.string(GenOptions.TREE_ROOT_VALUE), DEFAULT_ROOT_VALUE);
        gen.put("treeParent", parentColumn != null);
        gen.put("treeParentColumn", parentColumn);
        gen.put("treeAncestors", treeAncestors);
        gen.put("treeAncestorsColumn", ancestorsColumn);
        gen.put("treeRootValue", rootValue);
        gen.put("treeRootLiteral", rootLiteral(parentColumn, rootValue));

        List<String> orderFields = new ArrayList<>();
        if (ancestorsColumn != null) {
            orderFields.add(ancestorsColumn.getCapJavaField());
        }
        if (parentColumn != null) {
            orderFields.add(parentColumn.getCapJavaField());
        }
        if (treeOrderColumn != null) {
            orderFields.add(treeOrderColumn.getCapJavaField());
        } else if (sortColumn != null) {
            orderFields.add(sortColumn.getCapJavaField());
        }
        orderFields.add(pk.getCapJavaField());
        gen.put("orderFields", orderFields);

        boolean uniqueString = uniqueColumns.stream().anyMatch(column -> TYPE_STRING.equals(column.getJavaType()));
        gen.put("needStringUtils", uniqueString || stringCompare || (tree && treeAncestors));
        gen.put("enableExport", enableExport);
        return gen;
    }

    /**
     * 字段用到的需要导入的类型
     */
    private static List<String> typeImports(List<GenTableColumn> columns) {
        Set<String> imports = new LinkedHashSet<>();
        for (GenTableColumn column : columns) {
            if ("LocalDateTime".equals(column.getJavaType())) {
                imports.add("java.time.LocalDateTime");
            } else if ("BigDecimal".equals(column.getJavaType())) {
                imports.add("java.math.BigDecimal");
            }
        }
        return new ArrayList<>(imports);
    }

    /**
     * 树根节点值按上级字段的类型写成 Java 字面量（值已按类型检查过格式）
     */
    private static String rootLiteral(GenTableColumn parentColumn, String value) {
        if (parentColumn == null || StringUtils.isBlank(value)) {
            return "null";
        }
        return switch (parentColumn.getJavaType()) {
            case "Long" -> value + "L";
            case "Float" -> value + "F";
            case "Double" -> value + "D";
            case "BigDecimal" -> "new java.math.BigDecimal(\"" + value + "\")";
            case "Integer" -> value;
            default -> "\"" + value + "\"";
        };
    }

    private static String render(String template, Map<String, Object> context) {
        try {
            StringWriter writer = new StringWriter();
            Templates.CONFIGURATION.getTemplate(template).process(context, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("渲染代码生成模板 " + template + " 失败", e);
        }
    }

    /**
     * 模板引擎配置：从类路径根目录加载模板，长整数不带千分位。第一次使用时创建，模板由它缓存
     */
    private static final class Templates {

        static final Configuration CONFIGURATION = create();

        private Templates() {
        }

        private static Configuration create() {
            Configuration configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
            configuration.setLocalizedLookup(false);
            configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
            configuration.setTemplateLoader(new ClassTemplateLoader(GenCodeGenerator.class.getClassLoader(), ""));
            configuration.setNumberFormat("computer");
            // 渲染失败由调用方带上模板路径抛出，模板引擎不另外输出错误
            configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            configuration.setLogTemplateExceptions(false);
            return configuration;
        }
    }
}
