package org.tuoyu.common.tenant;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.common.tenant.mybatis.TuoyuTenantLineHandler;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨租户越权（SQL 层）：开启多租户时，隔离表的查询、更新、删除都只作用于当前租户，插入补上当前租户；
 * 共享表不变；缺少租户时拒绝执行（ADR 0006 第 3、8 节）
 */
@DisplayName("多租户：SQL 改写与失败闭合")
class TenantSqlTest {

    private final TenantLineInnerInterceptor interceptor =
        new TenantLineInnerInterceptor(new TuoyuTenantLineHandler(() -> Set.of("sys_user", "sys_dept")));

    @BeforeEach
    void enableTenant() {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        new DefaultTenantContext(properties, new StaticListableBeanFactory().getBeanProvider(UserContext.class), id -> null);
    }

    private String rewrite(String sql) {
        return interceptor.parserSingle(sql, null).replaceAll("\\s+", " ").toLowerCase();
    }

    @Test
    @DisplayName("查询隔离表时只查当前租户，联表时每张隔离表都加条件")
    void selectIsScoped() {
        String sql = TenantHelper.dynamic("acme", () -> rewrite("select * from sys_user where user_name = 'admin'"));
        assertTrue(sql.contains("tenant_id = 'acme'"), sql);

        String join = TenantHelper.dynamic("acme",
            () -> rewrite("select u.* from sys_user u left join sys_dept d on u.dept_id = d.dept_id"));
        assertTrue(join.contains("u.tenant_id = 'acme'") && join.contains("d.tenant_id = 'acme'"), join);
    }

    @Test
    @DisplayName("按 ID 更新、删除也只作用于当前租户，不能改动其他租户的数据")
    void updateAndDeleteAreScoped() {
        String update = TenantHelper.dynamic("acme", () -> rewrite("update sys_user set nick_name = 'x' where user_id = 1"));
        assertTrue(update.contains("tenant_id = 'acme'"), update);
        String delete = TenantHelper.dynamic("acme", () -> rewrite("delete from sys_user where user_id = 1"));
        assertTrue(delete.contains("tenant_id = 'acme'"), delete);
    }

    @Test
    @DisplayName("插入时补上当前租户")
    void insertAddsTenant() {
        String insert = TenantHelper.dynamic("acme", () -> rewrite("insert into sys_user (user_id, user_name) values (1, 'a')"));
        assertTrue(insert.contains("tenant_id") && insert.contains("'acme'"), insert);
    }

    @Test
    @DisplayName("共享表（菜单、字典等）不改写")
    void globalTablesUnchanged() {
        String sql = TenantHelper.dynamic("acme", () -> rewrite("select * from sys_menu where menu_id = 1"));
        assertEquals("select * from sys_menu where menu_id = 1", sql);
    }

    @Test
    @DisplayName("忽略租户的代码块中不改写")
    void ignoreSkipsRewrite() {
        String sql = TenantHelper.ignore(() -> rewrite("select * from sys_user"));
        assertEquals("select * from sys_user", sql);
    }

    @Test
    @DisplayName("缺少租户时拒绝执行，而不是查出所有租户的数据")
    void missingTenantIsRejected() {
        assertThrows(ServiceException.class, () -> rewrite("select * from sys_user"));
    }

}
