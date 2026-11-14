package org.tuoyu.gen.service;

import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.gen.config.properties.GenProperties;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;

import java.util.Locale;
import java.util.Set;

/**
 * 导入表结构与同步数据库时业务表与字段的默认配置：类名、包名、业务名等取自代码生成配置与表名，
 * 字段的 Java 类型、显示类型、查询方式与各项勾选按列名与列类型推断。
 *
 * @author tuoyu
 */
final class GenTableDefaults {

    static final String FRONTEND_ELE = "ele";

    static final String FRONTEND_ANTD = "antd";

    private static final String YES = "1";

    private static final String TYPE_STRING = "String";

    private static final String TYPE_BOOLEAN = "Boolean";

    private static final String TYPE_INTEGER = "Integer";

    private static final String TYPE_LONG = "Long";

    private static final String TYPE_BIG_DECIMAL = "BigDecimal";

    private static final String HTML_INPUT = "input";

    private static final String HTML_TEXTAREA = "textarea";

    private static final String HTML_INPUT_NUMBER = "inputNumber";

    private static final String HTML_SWITCH = "switch";

    private static final String HTML_DATETIME = "datetime";

    private static final String QUERY_EQ = "EQ";

    private static final String QUERY_LIKE = "LIKE";

    private static final String QUERY_BETWEEN = "BETWEEN";

    /**
     * 字符串长度达到它时默认用多行文本框
     */
    private static final int TEXTAREA_LENGTH = 500;

    private static final Set<String> STRING_TYPES = Set.of("char", "varchar", "enum", "set", "nchar", "nvarchar",
        "varchar2", "nvarchar2", "long varchar", "long nvarchar", "uniqueidentifier", "uuid", "json", "jsonb", "xml");

    private static final Set<String> TEXT_TYPES = Set.of("tinytext", "text", "mediumtext", "longtext", "binary",
        "varbinary", "blob", "ntext", "image", "bytea", "clob", "nclob", "long", "long raw", "raw");

    private static final Set<String> TIME_TYPES = Set.of("datetime", "time", "date", "timestamp", "year", "interval",
        "smalldatetime", "datetime2", "datetimeoffset", "timestamptz", "timestamp with time zone",
        "timestamp without time zone", "time with time zone", "time without time zone");

    private static final Set<String> NUMBER_TYPES = Set.of("tinyint", "smallint", "mediumint", "int", "int2", "int4",
        "int8", "number", "integer", "bit", "boolean", "bool", "bigint", "float", "float4", "float8", "double",
        "decimal", "dec", "fixed", "numeric", "real", "double precision", "binary_float", "binary_double",
        "smallserial", "serial", "serial2", "serial4", "serial8", "bigserial", "money", "smallmoney");

    private static final Set<String> DECIMAL_TYPES = Set.of("decimal", "dec", "fixed", "numeric", "money", "smallmoney");

    private static final Set<String> FLOAT_TYPES = Set.of("float4", "real", "binary_float");

    private static final Set<String> DOUBLE_TYPES = Set.of("float", "float8", "double", "double precision", "binary_double");

    private static final Set<String> LONG_TYPES = Set.of("bigint", "int8", "bigserial", "serial8");

    private static final Set<String> INTEGER_TYPES = Set.of("tinyint", "smallint", "mediumint", "int", "int2", "int4",
        "integer", "smallserial", "serial", "serial2", "serial4");

    private static final Set<String> BOOLEAN_TYPES = Set.of("bit", "boolean", "bool");

    /**
     * 长度为 1 时可能表示开关的数值类型（还要求列名像开关）
     */
    private static final Set<String> FLAG_NUMBER_TYPES = Set.of("tinyint", "number", "numeric", "decimal");

    /**
     * 由框架填写的列（审计与租户），导入时不勾选新增、修改、列表与查询
     */
    private static final Set<String> FRAMEWORK_COLUMNS = Set.of("create_dept", "create_by", "create_time",
        "update_by", "update_time", "tenant_id");

    private static final String[] SWITCH_SUFFIXES = {"status", "flag", "enabled", "disabled", "available", "visible"};

    private static final String[] SWITCH_PREFIXES = {"is_", "has_", "enable_", "disable_"};

    private static final String[] NUMBER_INPUT_SUFFIXES = {"sort", "order_num", "order", "rank", "seq", "sequence"};

    private static final String[] IMAGE_SUFFIXES = {"image", "avatar", "logo", "picture"};

    private static final String[] FILE_SUFFIXES = {"file", "attachment"};

    private static final String[] TEXTAREA_SUFFIXES = {"remark", "description", "desc", "note"};

    private static final String[] EDITOR_SUFFIXES = {"content", "html", "body"};

    private GenTableDefaults() {
    }

    /**
     * 按代码生成配置初始化导入的业务表
     *
     * @param table      数据库中的表（表名、表描述）
     * @param dataName   数据源名称
     * @param properties 代码生成配置
     */
    static void initTable(GenTable table, String dataName, GenProperties properties) {
        String tableName = table.getTableName();
        String packageName = properties.getPackageName();
        table.setClassName(className(tableName, properties));
        table.setPackageName(packageName);
        table.setModuleName(moduleName(packageName));
        table.setBusinessName(businessName(tableName));
        table.setFunctionName(functionName(table.getTableComment()));
        table.setFunctionAuthor(properties.getAuthor());
        table.setFrontendType(frontendType(properties.getFrontendType()));
        table.setDataName(dataName);
        // 创建、更新时间由审计填充写入，不沿用数据库中表的时间
        table.setCreateTime(null);
        table.setUpdateTime(null);
    }

    private static String className(String tableName, GenProperties properties) {
        String name = tableName;
        if (properties.isAutoRemovePre() && StringUtils.isNotBlank(properties.getTablePrefix())) {
            for (String prefix : StringUtils.str2List(properties.getTablePrefix(), ",", true, true)) {
                if (name.startsWith(prefix)) {
                    name = name.substring(prefix.length());
                    break;
                }
            }
        }
        return StringUtils.convertToCamelCase(name);
    }

    private static String moduleName(String packageName) {
        if (packageName == null) {
            return null;
        }
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private static String businessName(String tableName) {
        return StringUtils.toCamelCase(tableName.substring(tableName.indexOf('_') + 1));
    }

    private static String functionName(String tableComment) {
        if (tableComment != null && tableComment.endsWith("表")) {
            return tableComment.substring(0, tableComment.length() - 1);
        }
        return tableComment;
    }

    private static String frontendType(String configured) {
        if (StringUtils.isBlank(configured)) {
            return FRONTEND_ELE;
        }
        if (!FRONTEND_ELE.equals(configured) && !FRONTEND_ANTD.equals(configured)) {
            throw new ServiceException("代码生成配置 gen.frontendType 只能是 ele（Element Plus）或 antd（Ant Design Vue）");
        }
        return configured;
    }

    /**
     * 按列名与列类型初始化字段（读取表结构得到的列名、注释、类型、位置、主键、必填、自增保持不变）
     *
     * @param column  数据库中的列
     * @param tableId 业务表编号
     */
    static void initColumn(GenTableColumn column, Long tableId) {
        String name = column.getColumnName() == null ? "" : column.getColumnName().toLowerCase(Locale.ROOT);
        ColumnType type = ColumnType.parse(column.getColumnType());
        column.setTableId(tableId);
        column.setCreateTime(null);
        column.setUpdateTime(null);
        column.setJavaField(StringUtils.toCamelCase(name));
        column.setJavaType(TYPE_STRING);
        column.setHtmlType(HTML_INPUT);
        column.setQueryType(QUERY_EQ);

        boolean booleanColumn = isBooleanColumn(type, name);
        if (STRING_TYPES.contains(type.name()) || TEXT_TYPES.contains(type.name())) {
            if (booleanColumn) {
                column.setJavaType(TYPE_BOOLEAN);
                column.setHtmlType(HTML_SWITCH);
            } else {
                boolean text = TEXT_TYPES.contains(type.name()) || type.length() >= TEXTAREA_LENGTH;
                column.setHtmlType(text ? HTML_TEXTAREA : HTML_INPUT);
            }
        } else if (TIME_TYPES.contains(type.name())) {
            column.setJavaType("LocalDateTime");
            column.setHtmlType(HTML_DATETIME);
        } else if (NUMBER_TYPES.contains(type.name())) {
            String javaType = numberJavaType(type, booleanColumn);
            column.setJavaType(javaType);
            column.setHtmlType(TYPE_BOOLEAN.equals(javaType) ? HTML_SWITCH : HTML_INPUT_NUMBER);
        }

        boolean pk = column.isPk();
        boolean framework = FRAMEWORK_COLUMNS.contains(name);
        boolean systemColumn = "del_flag".equals(name) || "version".equals(name);
        if (!framework && !systemColumn && !pk) {
            column.setIsInsert(YES);
        }
        if (!framework && !systemColumn) {
            column.setIsEdit(YES);
            column.setIsList(YES);
        }
        if (!framework && !systemColumn && !"id".equals(name) && !"remark".equals(name) && !pk) {
            column.setIsQuery(YES);
        }

        if (StringUtils.endsWithIgnoreCase(name, "name")) {
            column.setQueryType(QUERY_LIKE);
        }
        if (HTML_DATETIME.equals(column.getHtmlType()) && column.isQuery()) {
            column.setQueryType(QUERY_BETWEEN);
        }

        adjustHtmlType(column, name);
    }

    private static void adjustHtmlType(GenTableColumn column, String name) {
        boolean booleanType = TYPE_BOOLEAN.equals(column.getJavaType());
        if (isSwitchName(name) || booleanType) {
            column.setHtmlType(HTML_SWITCH);
        }
        if (StringUtils.endsWithIgnoreCase(name, "status")) {
            column.setHtmlType(booleanType ? HTML_SWITCH : "radio");
        } else if (StringUtils.endsWithIgnoreCase(name, "type") || StringUtils.endsWithIgnoreCase(name, "sex")) {
            column.setHtmlType("select");
        } else if (StringUtils.endsWithAny(name, NUMBER_INPUT_SUFFIXES)) {
            column.setHtmlType(HTML_INPUT_NUMBER);
        } else if (StringUtils.endsWithAny(name, IMAGE_SUFFIXES)) {
            column.setHtmlType("imageUpload");
        } else if (StringUtils.endsWithAny(name, FILE_SUFFIXES)) {
            column.setHtmlType("fileUpload");
        } else if (StringUtils.endsWithAny(name, TEXTAREA_SUFFIXES)) {
            column.setHtmlType(HTML_TEXTAREA);
        } else if (StringUtils.endsWithAny(name, EDITOR_SUFFIXES)) {
            column.setHtmlType("editor");
        }
    }

    /**
     * 数值类型的 Java 类型（按顺序判断）
     */
    private static String numberJavaType(ColumnType type, boolean booleanColumn) {
        String name = type.name();
        if (booleanColumn) {
            return TYPE_BOOLEAN;
        }
        if (DECIMAL_TYPES.contains(name)) {
            return TYPE_BIG_DECIMAL;
        }
        if (FLOAT_TYPES.contains(name)) {
            return "Float";
        }
        if (DOUBLE_TYPES.contains(name)) {
            return "Double";
        }
        if (LONG_TYPES.contains(name)) {
            return TYPE_LONG;
        }
        if (INTEGER_TYPES.contains(name)) {
            return TYPE_INTEGER;
        }
        if ("number".equals(name)) {
            if (type.precision() > 0) {
                return TYPE_BIG_DECIMAL;
            }
            return type.length() >= 1 && type.length() <= 9 ? TYPE_INTEGER : TYPE_LONG;
        }
        return TYPE_LONG;
    }

    /**
     * 是否按布尔处理：bit、boolean、bool；长度为 1 的 tinyint、number、numeric、decimal 且列名像开关。
     * char(1) 不算（框架中的状态字段是字符 '0' / '1'，配合字典使用）
     */
    private static boolean isBooleanColumn(ColumnType type, String name) {
        if (type.precision() > 0) {
            return false;
        }
        if (BOOLEAN_TYPES.contains(type.name())) {
            return true;
        }
        return FLAG_NUMBER_TYPES.contains(type.name()) && type.length() == 1 && isSwitchName(name);
    }

    private static boolean isSwitchName(String name) {
        return StringUtils.endsWithAny(name, SWITCH_SUFFIXES) || StringUtils.startsWithAny(name, SWITCH_PREFIXES);
    }

    /**
     * 列类型：类型名（小写，去掉 unsigned、zerofill）、长度与精度（没有或不是整数时为 0）
     */
    private record ColumnType(String name, int length, int precision) {

        static ColumnType parse(String columnType) {
            if (columnType == null) {
                return new ColumnType("", 0, 0);
            }
            int open = columnType.indexOf('(');
            String name = (open < 0 ? columnType : columnType.substring(0, open)).trim().toLowerCase(Locale.ROOT)
                .replace(" unsigned", "").replace(" zerofill", "");
            int length = 0;
            int precision = 0;
            if (open >= 0) {
                int close = columnType.indexOf(')', open);
                String inner = close < 0 ? columnType.substring(open + 1) : columnType.substring(open + 1, close);
                int comma = inner.indexOf(',');
                length = toInt(comma < 0 ? inner : inner.substring(0, comma));
                precision = comma < 0 ? 0 : toInt(inner.substring(comma + 1));
            }
            return new ColumnType(name, length, precision);
        }

        private static int toInt(String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                // enum('a','b') 等括号中不是长度的类型按没有长度处理
                return 0;
            }
        }
    }
}
