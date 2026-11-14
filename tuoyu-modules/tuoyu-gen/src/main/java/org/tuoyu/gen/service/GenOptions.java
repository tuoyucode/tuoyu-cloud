package org.tuoyu.gen.service;

import cn.hutool.core.convert.Convert;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.gen.domain.GenTableColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成选项（{@code gen_table.options} 的 JSON，保存时为请求的 {@code params}）的读取。
 * <p>
 * 字段类的选项填列名或 Java 属性名，按“列名相等或 Java 属性名相等”（区分大小写）查找字段。
 *
 * @author tuoyu
 */
final class GenOptions {

    static final String TREE_CODE = "treeCode";

    static final String TREE_PARENT_CODE = "treeParentCode";

    static final String TREE_NAME = "treeName";

    static final String TREE_ANCESTORS = "treeAncestors";

    static final String TREE_ORDER_FIELD = "treeOrderField";

    static final String TREE_ROOT_VALUE = "treeRootValue";

    static final String PARENT_MENU_ID = "parentMenuId";

    static final String PARENT_MENU_NAME = "parentMenuName";

    static final String ENABLE_EXPORT = "enableExport";

    static final String ENABLE_STATUS = "enableStatus";

    static final String STATUS_FIELD = "statusField";

    static final String ENABLE_UNIQUE = "enableUnique";

    static final String UNIQUE_FIELDS = "uniqueFields";

    static final String ENABLE_SORT = "enableSort";

    static final String SORT_FIELD = "sortField";

    /**
     * 没有指定上级菜单时挂在“系统工具”下
     */
    static final long DEFAULT_PARENT_MENU_ID = 1761400000000000003L;

    private final Map<String, Object> values;

    GenOptions(Map<String, Object> values) {
        this.values = values == null ? new LinkedHashMap<>() : values;
    }

    /**
     * @param json 保存的选项；空白或 null 时视为空对象
     */
    static GenOptions parse(String json) {
        Map<String, Object> map = JsonUtils.parseMap(json);
        return new GenOptions(map == null ? null : new LinkedHashMap<>(map));
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    boolean contains(String key) {
        return values.containsKey(key);
    }

    Object get(String key) {
        return values.get(key);
    }

    /**
     * @return 键的字符串值，没有时为 null
     */
    String string(String key) {
        return Convert.toStr(values.get(key), null);
    }

    /**
     * @return 键的布尔值（按 hutool 的转换规则），键不存在或不能转换时为默认值
     */
    boolean bool(String key, boolean defaultValue) {
        Boolean value = Convert.toBool(values.get(key), defaultValue);
        return value == null ? defaultValue : value;
    }

    /**
     * @return 键的值转为字符串列表（数组或逗号分隔的字符串），没有时为空列表
     */
    List<String> stringList(String key) {
        Object value = values.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        List<String> list = Convert.toList(String.class, value);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /**
     * 按名称找字段：列名相等或 Java 属性名相等（区分大小写）
     *
     * @return 第一个匹配的字段；名称为空或找不到时为 null
     */
    static GenTableColumn findColumn(Collection<GenTableColumn> columns, String name) {
        if (columns == null || name == null || name.isEmpty()) {
            return null;
        }
        for (GenTableColumn column : columns) {
            if (name.equals(column.getColumnName()) || name.equals(column.getJavaField())) {
                return column;
            }
        }
        return null;
    }
}
