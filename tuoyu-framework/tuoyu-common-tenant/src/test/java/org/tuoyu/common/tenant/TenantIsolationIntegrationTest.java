package org.tuoyu.common.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.domain.TenantEntity;
import org.tuoyu.common.mybatis.handler.InjectionMetaObjectHandler;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.common.tenant.mybatis.TuoyuTenantLineHandler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 跨租户越权测试（CI 必跑）：在真实的 MySQL 上，通过 MyBatis-Plus 与本模块的租户规则读写，
 * 租户 A 的查询、按 ID 查询、更新、删除都碰不到租户 B 的数据。
 * <p>
 * 需要 Docker：本机没有 Docker 时跳过；CI（CI=true）中必须执行。
 */
@DisplayName("多租户：真实数据库上的跨租户越权测试")
@EnabledIf(value = "dockerAvailableOrRequired", disabledReason = "本机没有 Docker，跳过（CI 中必须执行）")
class TenantIsolationIntegrationTest {

    private static MySQLContainer mysql;

    private static SqlSessionFactory sessions;

    static boolean dockerAvailableOrRequired() {
        return "true".equalsIgnoreCase(System.getenv("CI")) || DockerClientFactory.instance().isDockerAvailable();
    }

    @BeforeAll
    static void start() throws Exception {
        mysql = new MySQLContainer("mysql:8.4.9");
        mysql.start();
        try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("create table tenant_item (id bigint primary key, name varchar(50), "
                + "tenant_id varchar(20) not null default 'default', create_dept bigint, create_by bigint, "
                + "create_time datetime, update_by bigint, update_time datetime)");
        }

        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        DefaultTenantContext tenantContext = new DefaultTenantContext(properties,
            new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), id -> null);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("tenantContext", tenantContext);

        MybatisConfiguration configuration = new MybatisConfiguration(new Environment("test", new JdbcTransactionFactory(),
            new UnpooledDataSource(mysql.getDriverClassName(), mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())));
        // 与服务中相同：框架的字段填充（插入时改写为当前租户、更新时不改动租户）+ 租户插件
        GlobalConfigUtils.getGlobalConfig(configuration).setMetaObjectHandler(new InjectionMetaObjectHandler(
            new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), beans.getBeanProvider(TenantContext.class)));
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TuoyuTenantLineHandler()));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(ItemMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);

        inTenant("tenant-a", () -> run(mapper -> mapper.insert(item(1, "a-1")) + mapper.insert(item(2, "a-2"))));
        inTenant("tenant-b", () -> run(mapper -> mapper.insert(item(3, "b-1"))));
    }

    @AfterAll
    static void stop() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    /**
     * 删除各用例自己插入的数据（ID 大于 3），只保留 start() 中的 3 行；用例中途失败时也不影响其他用例的计数
     */
    @AfterEach
    void removeRowsAddedByTest() throws Exception {
        try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from tenant_item where id > 3");
        }
    }

    @Test
    @DisplayName("列表与统计只包含当前租户的数据")
    void listIsScoped() {
        int listed = inTenant("tenant-a", () -> run(mapper -> mapper.selectList(null).size()));
        long counted = inTenant("tenant-b", () -> run(mapper -> mapper.selectCount(null)));
        assertEquals(2, listed);
        assertEquals(1L, counted);
    }

    @Test
    @DisplayName("按 ID 读取、更新、删除其他租户的数据：读不到，影响 0 行")
    void cannotTouchOtherTenantById() {
        assertNull(inTenant("tenant-a", () -> run(mapper -> mapper.selectById(3L))));
        int updated = inTenant("tenant-a", () -> run(mapper -> mapper.updateById(item(3, "hacked"))));
        int deleted = inTenant("tenant-a", () -> run(mapper -> mapper.deleteById(3L)));
        assertEquals(0, updated);
        assertEquals(0, deleted);
        assertEquals("b-1", inTenant("tenant-b", () -> run(mapper -> mapper.selectById(3L).getName())));
    }

    @Test
    @DisplayName("插入的数据属于当前租户，即使实体中填了其他租户")
    void insertBelongsToCurrentTenant() {
        inTenant("tenant-a", () -> run(mapper -> {
            Item item = item(4, "a-forged");
            item.setTenantId("tenant-b");
            return mapper.insert(item);
        }));
        assertNull(inTenant("tenant-b", () -> run(mapper -> mapper.selectById(4L))), "不能借插入把数据写进其他租户");
    }

    @Test
    @DisplayName("按实体更新时不能把本租户的数据改到其他租户")
    void updateCannotMoveRowToOtherTenant() {
        inTenant("tenant-a", () -> run(mapper -> mapper.insert(item(5, "a-5"))));
        // 先查出再修改（业务代码的常见写法）：实体中带着租户，这里再故意改成其他租户
        int updated = inTenant("tenant-a", () -> run(mapper -> {
            Item item = mapper.selectById(5L);
            item.setName("a-5-renamed");
            item.setTenantId("tenant-b");
            return mapper.updateById(item);
        }));
        assertEquals(1, updated);
        assertEquals("a-5-renamed", inTenant("tenant-a", () -> run(mapper -> mapper.selectById(5L).getName())));
        assertNull(inTenant("tenant-b", () -> run(mapper -> mapper.selectById(5L))));
    }

    @Test
    @DisplayName("没有租户时拒绝执行；明确忽略租户时才能看到全部数据")
    void missingTenantRejectedAndIgnoreSeesAll() {
        assertThrows(RuntimeException.class, () -> run(mapper -> mapper.selectList(null)));
        int all = ignoring(() -> run(mapper -> mapper.selectList(null).size()));
        assertEquals(3, all);
    }

    private static <T> T inTenant(String tenantId, Supplier<T> action) {
        return TenantHelper.dynamic(tenantId, action);
    }

    private static <T> T ignoring(Supplier<T> action) {
        return TenantHelper.ignore(action);
    }

    private static <T> T run(Function<ItemMapper, T> action) {
        try (SqlSession session = sessions.openSession(true)) {
            return action.apply(session.getMapper(ItemMapper.class));
        } catch (ServiceException e) {
            throw e;
        }
    }

    private static Item item(long id, String name) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        return item;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    @TableName("tenant_item")
    public static class Item extends TenantEntity {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String name;
    }

    public interface ItemMapper extends BaseMapper<Item> {
    }

}
