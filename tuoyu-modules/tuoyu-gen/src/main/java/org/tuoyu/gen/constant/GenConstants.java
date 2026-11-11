package org.tuoyu.gen.constant;

import cn.hutool.core.collection.CollUtil;

import java.util.Set;

/**
 * 代码生成通用常量
 *
 * @author tuoyu
 */
public interface GenConstants {
    /**
     * 单表（增删改查）
     */
    String TPL_CRUD = "crud";

    /**
     * 树表（增删改查）
     */
    String TPL_TREE = "tree";

    /**
     * 树编码字段
     */
    String TREE_CODE = "treeCode";

    /**
     * 树父编码字段
     */
    String TREE_PARENT_CODE = "treeParentCode";

    /**
     * 树名称字段
     */
    String TREE_NAME = "treeName";

    /**
     * 上级菜单ID字段
     */
    String PARENT_MENU_ID = "parentMenuId";

    /**
     * 上级菜单名称字段
     */
    String PARENT_MENU_NAME = "parentMenuName";

    /**
     * 是否启用导出能力
     */
    String ENABLE_EXPORT = "enableExport";

    /**
     * 是否启用状态切换能力
     */
    String ENABLE_STATUS = "enableStatus";

    /**
     * 状态字段
     */
    String STATUS_FIELD = "statusField";

    /**
     * 是否启用组合唯一校验
     */
    String ENABLE_UNIQUE = "enableUnique";

    /**
     * 组合唯一字段
     */
    String UNIQUE_FIELDS = "uniqueFields";

    /**
     * 是否启用排序调整能力
     */
    String ENABLE_SORT = "enableSort";

    /**
     * 排序字段
     */
    String SORT_FIELD = "sortField";

    /**
     * Vben 前端模板的根目录名，也是生成的 zip 中前端代码的根目录（与前端仓库的根目录一一对应）
     */
    String FRONTEND_ROOT = "vben";

    /**
     * 前端模板类型：Element Plus 版（前端仓库 apps/web-ele），新导入的表默认使用
     */
    String FRONTEND_TYPE_ELE = "ele";

    /**
     * 前端模板类型：Ant Design Vue 版（前端仓库 apps/web-antd）
     */
    String FRONTEND_TYPE_ANTD = "antd";

    /**
     * 支持的前端模板类型
     */
    Set<String> FRONTEND_TYPES = Set.of(FRONTEND_TYPE_ELE, FRONTEND_TYPE_ANTD);

    /**
     * 树根节点值
     */
    String TREE_ROOT_VALUE = "treeRootValue";

    /**
     * 树祖级字段
     */
    String TREE_ANCESTORS = "treeAncestors";

    /**
     * 树排序字段
     */
    String TREE_ORDER_FIELD = "treeOrderField";

    /**
     * 数据库字符串类型
     */
    String[] COLUMNTYPE_STR = {"char", "varchar", "enum", "set", "nchar", "nvarchar", "varchar2", "nvarchar2",
        "long varchar", "long nvarchar", "uniqueidentifier", "uuid", "json", "jsonb", "xml"};

    /**
     * 数据库文本类型
     */
    String[] COLUMNTYPE_TEXT = {"tinytext", "text", "mediumtext", "longtext", "binary", "varbinary", "blob",
        "ntext", "image", "bytea", "clob", "nclob", "long", "long raw", "raw"};

    /**
     * 数据库时间类型
     */
    String[] COLUMNTYPE_TIME = {"datetime", "time", "date", "timestamp", "year", "interval",
        "smalldatetime", "datetime2", "datetimeoffset", "timestamptz", "timestamp with time zone",
        "timestamp without time zone", "time with time zone", "time without time zone"};

    /**
     * 数据库数字类型
     */
    String[] COLUMNTYPE_NUMBER = {"tinyint", "smallint", "mediumint", "int", "int2", "int4", "int8", "number", "integer",
        "bit", "boolean", "bool", "bigint", "float", "float4", "float8", "double", "decimal", "dec", "fixed",
        "numeric", "real", "double precision", "binary_float", "binary_double", "smallserial", "serial",
        "serial2", "serial4", "serial8", "bigserial", "money", "smallmoney"};

    /**
     * 由框架填充的列：审计字段（BaseEntity）与租户（TenantEntity）。导入时默认不新增、不修改、不查询、不返回
     */
    String[] COLUMNNAME_FRAMEWORK_FILLED = {"create_dept", "create_by", "create_time", "update_by", "update_time",
        "tenant_id"};

    /**
     * BO对象 不需要添加字段
     */
    String[] COLUMNNAME_NOT_ADD = {"del_flag", "version"};

    /**
     * BO对象 不需要编辑字段
     */
    String[] COLUMNNAME_NOT_EDIT = {"del_flag", "version"};

    /**
     * VO对象 不需要返回字段
     */
    String[] COLUMNNAME_NOT_LIST = {"del_flag", "version"};

    /**
     * BO对象 不需要查询字段
     */
    String[] COLUMNNAME_NOT_QUERY = {"id", "del_flag", "remark", "version"};

    /**
     * Entity基类字段
     */
    String[] BASE_ENTITY = {"createDept", "createBy", "createTime", "updateBy", "updateTime"};

    /**
     * 租户字段：表中有这一列时实体继承 TenantEntity，由框架在插入时填写、更新时不改动
     */
    String TENANT_FIELD = "tenantId";

    /**
     * 文本框
     */
    String HTML_INPUT = "input";

    /**
     * 文本域
     */
    String HTML_TEXTAREA = "textarea";

    /**
     * 下拉框
     */
    String HTML_SELECT = "select";

    /**
     * 单选框
     */
    String HTML_RADIO = "radio";

    /**
     * 复选框
     */
    String HTML_CHECKBOX = "checkbox";

    /**
     * 日期控件
     */
    String HTML_DATETIME = "datetime";

    /**
     * 开关控件
     */
    String HTML_SWITCH = "switch";

    /**
     * 数字输入控件
     */
    String HTML_INPUT_NUMBER = "inputNumber";

    /**
     * 图片上传控件
     */
    String HTML_IMAGE_UPLOAD = "imageUpload";

    /**
     * 文件上传控件
     */
    String HTML_FILE_UPLOAD = "fileUpload";

    /**
     * 富文本控件
     */
    String HTML_EDITOR = "editor";

    /**
     * 字符串类型
     */
    String TYPE_STRING = "String";

    /**
     * 整型
     */
    String TYPE_INTEGER = "Integer";

    /**
     * 长整型
     */
    String TYPE_LONG = "Long";

    /**
     * 浮点型
     */
    String TYPE_DOUBLE = "Double";

    /**
     * 单精度浮点型
     */
    String TYPE_FLOAT = "Float";

    /**
     * 高精度计算类型
     */
    String TYPE_BIGDECIMAL = "BigDecimal";

    /**
     * 布尔类型
     */
    String TYPE_BOOLEAN = "Boolean";

    /**
     * 时间类型
     */
    String TYPE_DATE = "LocalDateTime";

    /**
     * 模糊查询
     */
    String QUERY_LIKE = "LIKE";

    /**
     * 相等查询
     */
    String QUERY_EQ = "EQ";
    /**
     * 范围查询
     */
    String QUERY_BETWEEN = "BETWEEN";

    /**
     * 必填标识，对应前端表单规则中的必填字段配置。
     */
    String REQUIRE = "1";

    // 模板根路径与后缀
    String TEMPLATE_ROOT_PATH = "fm";
    String TEMPLATE_FILE_SUFFIX = ".ftl";
    // 后端源码模板
    String JAVA_TEMPLATE_ROOT_PATH = TEMPLATE_ROOT_PATH + "/java";
    String JAVA_DOMAIN_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/domain.java" + TEMPLATE_FILE_SUFFIX;
    String JAVA_VO_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/vo.java" + TEMPLATE_FILE_SUFFIX;
    String JAVA_BO_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/bo.java" + TEMPLATE_FILE_SUFFIX;
    String JAVA_MAPPER_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/mapper.java" + TEMPLATE_FILE_SUFFIX;
    String JAVA_SERVICE_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/service.java" + TEMPLATE_FILE_SUFFIX;
    String JAVA_SERVICE_IMPL_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/serviceImpl.java" + TEMPLATE_FILE_SUFFIX;
    String JAVA_CONTROLLER_TEMPLATE_PATH = JAVA_TEMPLATE_ROOT_PATH + "/controller.java" + TEMPLATE_FILE_SUFFIX;
    // MyBatis MapperXML 模板
    String XML_TEMPLATE_ROOT_PATH = TEMPLATE_ROOT_PATH + "/xml";
    String XML_MAPPER_TEMPLATE_PATH = XML_TEMPLATE_ROOT_PATH + "/mapper.xml" + TEMPLATE_FILE_SUFFIX;
    // Vben 前端模板（与前端的页面模板约定一致）：接口与中英文文案两个 UI 版本共用，
    // 查询与表单定义、列表页、抽屉按 UI 版本放在 ele、antd 子目录
    String VBEN_TEMPLATE_ROOT_PATH = TEMPLATE_ROOT_PATH + "/" + FRONTEND_ROOT;
    String VBEN_API_TEMPLATE_PATH = VBEN_TEMPLATE_ROOT_PATH + "/api.ts" + TEMPLATE_FILE_SUFFIX;
    String VBEN_LOCALE_ZH_TEMPLATE_PATH = VBEN_TEMPLATE_ROOT_PATH + "/locale-zh-CN.json" + TEMPLATE_FILE_SUFFIX;
    String VBEN_LOCALE_EN_TEMPLATE_PATH = VBEN_TEMPLATE_ROOT_PATH + "/locale-en-US.json" + TEMPLATE_FILE_SUFFIX;
    // 各 UI 版本的页面模板文件名（位于 VBEN_TEMPLATE_ROOT_PATH/<前端模板类型>/ 下）
    String VBEN_DATA_TEMPLATE = "data.ts" + TEMPLATE_FILE_SUFFIX;
    String VBEN_DRAWER_TEMPLATE = "drawer.vue" + TEMPLATE_FILE_SUFFIX;
    String VBEN_INDEX_TEMPLATE = "index.vue" + TEMPLATE_FILE_SUFFIX;
    String VBEN_INDEX_TREE_TEMPLATE = "index-tree.vue" + TEMPLATE_FILE_SUFFIX;
    // 数据库SQL模板
    String SQL_TEMPLATE_ROOT_PATH = TEMPLATE_ROOT_PATH + "/sql";
    String SQL_ORACLE_TEMPLATE_PATH = SQL_TEMPLATE_ROOT_PATH + "/oracle.sql" + TEMPLATE_FILE_SUFFIX;
    String SQL_POSTGRES_TEMPLATE_PATH = SQL_TEMPLATE_ROOT_PATH + "/postgres.sql" + TEMPLATE_FILE_SUFFIX;
    String SQL_SQLSERVER_TEMPLATE_PATH = SQL_TEMPLATE_ROOT_PATH + "/sqlserver.sql" + TEMPLATE_FILE_SUFFIX;
    String SQL_MYSQL_TEMPLATE_PATH = SQL_TEMPLATE_ROOT_PATH + "/mysql.sql" + TEMPLATE_FILE_SUFFIX;

    /**
     * 全部模板路径，启动时一次加载。
     */
    Set<String> TEMPLATE_PATHS = CollUtil.newHashSet(
        JAVA_DOMAIN_TEMPLATE_PATH
        , JAVA_VO_TEMPLATE_PATH
        , JAVA_BO_TEMPLATE_PATH
        , JAVA_MAPPER_TEMPLATE_PATH
        , JAVA_SERVICE_TEMPLATE_PATH
        , JAVA_SERVICE_IMPL_TEMPLATE_PATH
        , JAVA_CONTROLLER_TEMPLATE_PATH
        , XML_MAPPER_TEMPLATE_PATH
        , VBEN_API_TEMPLATE_PATH
        , VBEN_LOCALE_ZH_TEMPLATE_PATH
        , VBEN_LOCALE_EN_TEMPLATE_PATH
        , vbenPageTemplatePath(FRONTEND_TYPE_ELE, VBEN_DATA_TEMPLATE)
        , vbenPageTemplatePath(FRONTEND_TYPE_ELE, VBEN_DRAWER_TEMPLATE)
        , vbenPageTemplatePath(FRONTEND_TYPE_ELE, VBEN_INDEX_TEMPLATE)
        , vbenPageTemplatePath(FRONTEND_TYPE_ELE, VBEN_INDEX_TREE_TEMPLATE)
        , vbenPageTemplatePath(FRONTEND_TYPE_ANTD, VBEN_DATA_TEMPLATE)
        , vbenPageTemplatePath(FRONTEND_TYPE_ANTD, VBEN_DRAWER_TEMPLATE)
        , vbenPageTemplatePath(FRONTEND_TYPE_ANTD, VBEN_INDEX_TEMPLATE)
        , vbenPageTemplatePath(FRONTEND_TYPE_ANTD, VBEN_INDEX_TREE_TEMPLATE)
        , SQL_ORACLE_TEMPLATE_PATH
        , SQL_POSTGRES_TEMPLATE_PATH
        , SQL_SQLSERVER_TEMPLATE_PATH
        , SQL_MYSQL_TEMPLATE_PATH
    );

    /**
     * 某个 UI 版本的页面模板路径，例如 fm/vben/ele/index.vue.ftl
     *
     * @param frontendType 前端模板类型（ele、antd）
     * @param template     页面模板文件名（VBEN_DATA_TEMPLATE 等）
     * @return 模板路径
     */
    static String vbenPageTemplatePath(String frontendType, String template) {
        return VBEN_TEMPLATE_ROOT_PATH + "/" + frontendType + "/" + template;
    }
}
