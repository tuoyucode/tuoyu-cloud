package org.tuoyu.common.mybatis.core.page;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按实体推导可排序的属性（属性名 → 列名）。
 * <p>
 * 排序结果会暴露列值的相对大小：客户端反复按某列排序并观察自己能看到的行的位置，可以逐位推出看不到的值
 * （例如他人的手机号）。所以只开放不敏感的列，以下属性一律不能排序：
 * <ul>
 *     <li>表信息中 {@code select = false} 的字段（默认不查询，本来就不该被客户端感知）；</li>
 *     <li>实体或对应 Vo 中标注了 {@code @JsonIgnore}、{@code @EncryptField}、{@code @Sensitive} 的字段
 *     （接口中不下发、加密保存或需要脱敏的值）；</li>
 *     <li>名称（不区分大小写）含 {@link #SECRET_NAME_PARTS} 中任一词的字段（口令、密钥、令牌类）。</li>
 * </ul>
 * 对应 Vo 按约定查找：实体所在包下的 {@code vo} 子包中名为“实体名 + Vo”的类，例如
 * {@code org.tuoyu.system.domain.SysUser} 对应 {@code org.tuoyu.system.domain.vo.SysUserVo}；不存在时只看实体。
 * 需要更严格的范围时（例如只要求登录的接口）用 {@link PageParam#toPage(Map)} 给出显式白名单。
 *
 * @author tuoyu
 */
final class SortableColumns {

    /**
     * 名称中含这些词（小写比较）的属性视为口令、密钥类，不能排序
     */
    static final List<String> SECRET_NAME_PARTS = List.of(
        "password", "passwd", "pwd", "secret", "token", "accesskey", "privatekey", "apikey", "credential", "salt");

    /**
     * 标注了这些注解的字段不能排序。按全限定名比较，持久层不需要依赖加密、脱敏模块
     */
    private static final Set<String> HIDDEN_ANNOTATIONS = Set.of(
        "com.fasterxml.jackson.annotation.JsonIgnore",
        "org.tuoyu.common.encrypt.annotation.EncryptField",
        "org.tuoyu.common.sensitive.annotation.Sensitive");

    private static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";

    private static final Map<Class<?>, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private SortableColumns() {
    }

    /**
     * @param entityClass 已由 MyBatis-Plus 登记表信息的实体
     * @return 可排序的属性名 → 列名（只读）
     */
    static Map<String, String> of(Class<?> entityClass) {
        TableInfo tableInfo = entityClass == null ? null : TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo == null) {
            throw new IllegalStateException("没有 " + entityClass + " 的表信息，不能按实体属性排序");
        }
        return CACHE.computeIfAbsent(entityClass, type -> build(type, tableInfo));
    }

    private static Map<String, String> build(Class<?> entityClass, TableInfo tableInfo) {
        Class<?> viewClass = findViewClass(entityClass);
        Map<String, String> columns = new LinkedHashMap<>();
        String keyProperty = tableInfo.getKeyProperty();
        if (keyProperty != null && isSortable(keyProperty, findField(entityClass, keyProperty), viewClass)) {
            columns.put(keyProperty, tableInfo.getKeyColumn());
        }
        for (TableFieldInfo field : tableInfo.getFieldList()) {
            if (field.isSelect() && isSortable(field.getProperty(), field.getField(), viewClass)) {
                columns.put(field.getProperty(), field.getColumn());
            }
        }
        return Collections.unmodifiableMap(columns);
    }

    private static boolean isSortable(String property, Field entityField, Class<?> viewClass) {
        if (hasSecretName(property)) {
            return false;
        }
        if (entityField != null && isHidden(entityField)) {
            return false;
        }
        Field viewField = viewClass == null ? null : findField(viewClass, property);
        return viewField == null || !isHidden(viewField);
    }

    static boolean hasSecretName(String property) {
        String lower = property.toLowerCase(Locale.ROOT);
        for (String part : SECRET_NAME_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHidden(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (HIDDEN_ANNOTATIONS.contains(name) && !(JSON_IGNORE.equals(name) && !jsonIgnoreEnabled(annotation))) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code @JsonIgnore(false)} 表示不忽略，按普通字段处理；读取失败时按忽略处理
     */
    private static boolean jsonIgnoreEnabled(Annotation annotation) {
        try {
            Method value = annotation.annotationType().getMethod("value");
            return !Boolean.FALSE.equals(value.invoke(annotation));
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    private static Class<?> findViewClass(Class<?> entityClass) {
        String name = entityClass.getPackageName() + ".vo." + entityClass.getSimpleName() + "Vo";
        try {
            return Class.forName(name, false, entityClass.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String property) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(property);
            } catch (NoSuchFieldException ignored) {
                // 继续在父类中查找
            }
        }
        return null;
    }

}
