package org.tuoyu.common.tenant.mybatis;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.tenant.core.TenantHelper;

import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 租户 SQL 改写的规则，SQL 改写本身由 MyBatis-Plus 的租户插件完成：
 * <ul>
 *     <li>表中有 {@code tenant_id} 字段（实体有 tenantId 属性）的表按租户隔离，不需要手工维护清单；</li>
 *     <li>处于忽略租户的代码块中（{@code TenantHelper.ignore}、{@code @TenantIgnore}）时不改写；</li>
 *     <li><b>缺少租户时拒绝执行</b>（失败闭合），而不是查出所有租户的数据。</li>
 * </ul>
 *
 * @author tuoyu
 */
public class TuoyuTenantLineHandler implements TenantLineHandler {

    public static final String TENANT_COLUMN = "tenant_id";

    private final Supplier<Set<String>> tenantTables;

    private volatile Set<String> cached;

    /**
     * 默认从 MyBatis-Plus 已解析的实体中找出有 tenant_id 字段的表
     */
    public TuoyuTenantLineHandler() {
        this(TuoyuTenantLineHandler::tablesWithTenantColumn);
    }

    /**
     * @param tenantTables 按租户隔离的表（小写），测试时可以直接指定
     */
    public TuoyuTenantLineHandler(Supplier<Set<String>> tenantTables) {
        this.tenantTables = tenantTables;
    }

    @Override
    public Expression getTenantId() {
        String tenantId = TenantHelper.currentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ServiceException("缺少租户上下文，拒绝访问按租户隔离的数据。后台任务请使用 @TenantJob 或 TenantHelper 指定租户");
        }
        return new StringValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return TENANT_COLUMN;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        if (TenantHelper.isIgnoring()) {
            return true;
        }
        return !tables().contains(normalize(tableName));
    }

    private Set<String> tables() {
        Set<String> tables = cached;
        if (tables == null || tables.isEmpty()) {
            tables = tenantTables.get();
            cached = tables;
        }
        return tables;
    }

    static String normalize(String tableName) {
        String name = tableName.replace("`", "").replace("\"", "");
        int dot = name.lastIndexOf('.');
        return (dot >= 0 ? name.substring(dot + 1) : name).toLowerCase(Locale.ROOT);
    }

    private static Set<String> tablesWithTenantColumn() {
        return TableInfoHelper.getTableInfos().stream()
            .filter(TuoyuTenantLineHandler::hasTenantColumn)
            .map(info -> normalize(info.getTableName()))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasTenantColumn(TableInfo info) {
        for (TableFieldInfo field : info.getFieldList()) {
            if (TENANT_COLUMN.equalsIgnoreCase(field.getColumn())) {
                return true;
            }
        }
        return false;
    }

}
