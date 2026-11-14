package org.tuoyu.gen.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务字段（gen_table_column）：业务表中的一列与它的代码生成配置。
 * <p>
 * “是否”类的配置（主键、自增、必填、新增、修改、列表、查询）以字符串保存，{@code "1"} 表示是。
 * 派生属性（无参的 {@code get*}、{@code is*} 方法）供模板与前端编辑页读取，没有 setter，
 * 前端原样提交时反序列化会忽略它们。
 *
 * @author tuoyu
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("gen_table_column")
public class GenTableColumn extends BaseEntity {

    private static final String YES = "1";

    private static final String LABEL_OPEN = "（";

    private static final String LABEL_CLOSE = "）";

    private static final String HTML_DATETIME = "datetime";

    private static final String QUERY_BETWEEN = "BETWEEN";

    private static final String[] NUMBER_TYPES = {"Integer", "Long", "Double", "Float", "BigDecimal"};

    private static final String[] TS_NUMBER_TYPES = {"Long", "Integer", "Double", "Float", "BigDecimal"};

    private static final String[] DICT_HTML_TYPES = {"select", "radio", "checkbox", "switch"};

    /**
     * 生成页面时不作为普通表单项处理的字段：审计、租户与树表的上级字段
     */
    private static final String[] SUPER_FIELDS = {"createDept", "createBy", "createTime", "updateBy", "updateTime",
        "tenantId", "parentName", "parentId"};

    /**
     * 属于 {@link #SUPER_FIELDS} 但页面仍需保留的字段
     */
    private static final String[] USABLE_FIELDS = {"parentId", "orderNum", "remark"};

    /**
     * 编号
     */
    @TableId(value = "column_id")
    private Long columnId;

    /**
     * 归属表编号
     */
    private Long tableId;

    /**
     * 列名称
     */
    private String columnName;

    /**
     * 列描述
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String columnComment;

    /**
     * 列类型（小写，带长度，例如 varchar(64)）
     */
    private String columnType;

    /**
     * Java 类型
     */
    private String javaType;

    /**
     * Java 属性名
     */
    @NotBlank(message = "Java属性不能为空")
    private String javaField;

    /**
     * 是否主键
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isPk;

    /**
     * 是否自增
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isIncrement;

    /**
     * 是否必填
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isRequired;

    /**
     * 是否为新增字段
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isInsert;

    /**
     * 是否为修改字段
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isEdit;

    /**
     * 是否为列表字段
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isList;

    /**
     * 是否为查询字段
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String isQuery;

    /**
     * 查询方式：EQ、NE、GT、GE、LT、LE、LIKE、BETWEEN
     */
    private String queryType;

    /**
     * 显示类型：input、textarea、inputNumber、select、radio、checkbox、switch、datetime、imageUpload、fileUpload、editor
     */
    private String htmlType;

    /**
     * 字典类型
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, jdbcType = JdbcType.VARCHAR)
    private String dictType;

    /**
     * 排序号
     */
    private Integer sort;

    /**
     * @return 首字母大写的 Java 属性名（用于 getter、setter 的名称）
     */
    public String getCapJavaField() {
        return StringUtils.capitalize(javaField);
    }

    /**
     * @return 显示用的名称：注释中全角左括号之前的部分（{@code 状态（0正常 1停用）} → {@code 状态}）
     */
    public String getColumnLabel() {
        if (columnComment == null) {
            return null;
        }
        int open = columnComment.indexOf(LABEL_OPEN);
        return open < 0 ? columnComment : columnComment.substring(0, open);
    }

    /**
     * 注释中全角括号内的取值说明转换为 Excel 的读取表达式：{@code 状态（0正常 1停用）} → {@code 0=正常,1=停用}
     *
     * @return 读取表达式；括号之间为空或没有括号时为注释本身
     */
    public String readConverterExp() {
        if (columnComment == null) {
            return null;
        }
        int open = columnComment.indexOf(LABEL_OPEN);
        int close = open < 0 ? -1 : columnComment.indexOf(LABEL_CLOSE, open + 1);
        if (close < 0) {
            return columnComment;
        }
        String inner = columnComment.substring(open + 1, close);
        List<String> items = new ArrayList<>();
        for (String item : inner.split(" ")) {
            if (!item.isEmpty()) {
                items.add(item.charAt(0) + "=" + item.substring(1));
            }
        }
        return items.isEmpty() ? columnComment : String.join(",", items);
    }

    /**
     * @return 前端的 TypeScript 类型：编号类字段可能是超出 JavaScript 精度的长整数，按字符串或数字处理
     */
    public String getTsType() {
        if (StringUtils.contains(javaField, "id") || StringUtils.contains(javaField, "Id")) {
            return "string | number";
        }
        if (StringUtils.equalsAny(javaType, TS_NUMBER_TYPES)) {
            return "number";
        }
        if ("Boolean".equals(javaType)) {
            return "boolean";
        }
        return "string";
    }

    /**
     * @return 开关打开时的值（前端字面量）
     */
    public String getSwitchActiveValue() {
        if ("Boolean".equals(javaType)) {
            return "true";
        }
        return isNumberType() ? "0" : "'0'";
    }

    /**
     * @return 开关关闭时的值（前端字面量）
     */
    public String getSwitchInactiveValue() {
        if ("Boolean".equals(javaType)) {
            return "false";
        }
        return isNumberType() ? "1" : "'1'";
    }

    /**
     * @return Java 类型是否为数值
     */
    public boolean isNumberType() {
        return StringUtils.equalsAny(javaType, NUMBER_TYPES);
    }

    /**
     * @return 是否为日期区间查询（勾选查询、显示类型为日期时间、查询方式为 BETWEEN）
     */
    public boolean isDateRangeQuery() {
        return isQuery() && HTML_DATETIME.equals(htmlType) && QUERY_BETWEEN.equals(queryType);
    }

    public boolean isPk() {
        return YES.equals(isPk);
    }

    public boolean isIncrement() {
        return YES.equals(isIncrement);
    }

    public boolean isRequired() {
        return YES.equals(isRequired);
    }

    public boolean isInsert() {
        return YES.equals(isInsert);
    }

    public boolean isEdit() {
        return YES.equals(isEdit);
    }

    public boolean isList() {
        return YES.equals(isList);
    }

    public boolean isQuery() {
        return YES.equals(isQuery);
    }

    /**
     * @return 是否为页面中不作为普通表单项处理的字段（审计、租户与树表的上级字段，忽略大小写）
     */
    public boolean isSuperColumn() {
        return StringUtils.equalsAnyIgnoreCase(javaField, SUPER_FIELDS);
    }

    /**
     * @return 是否为页面需要保留的字段（上级编号、显示顺序、备注，忽略大小写）
     */
    public boolean isUsableColumn() {
        return StringUtils.equalsAnyIgnoreCase(javaField, USABLE_FIELDS);
    }

    /**
     * @return 列名与 Java 属性名的下划线形式不对应（忽略大小写），实体中需要用 {@code @TableField} 写明列名
     */
    public boolean isNeedTableField() {
        return StringUtils.isNotBlank(columnName) && StringUtils.isNotBlank(javaField)
            && !columnName.equalsIgnoreCase(StringUtils.toUnderScoreCase(javaField));
    }

    /**
     * @return 是否使用字典：设置了字典类型，且显示类型可以选择字典（下拉、单选、多选、开关）
     */
    public boolean isDictColumn() {
        return StringUtils.isNotBlank(dictType) && StringUtils.equalsAny(htmlType, DICT_HTML_TYPES);
    }
}
