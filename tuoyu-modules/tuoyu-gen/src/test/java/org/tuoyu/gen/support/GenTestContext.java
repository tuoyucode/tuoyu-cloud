package org.tuoyu.gen.support;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.tuoyu.gen.config.properties.GenProperties;
import org.tuoyu.gen.domain.GenTable;
import org.tuoyu.gen.domain.GenTableColumn;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 代码生成测试共用的 Spring 容器（经 hutool 的静态容器持有者访问，SpringUtils 经它取 Bean）。
 * <ul>
 *     <li>{@code JsonMapper}：生成选项（gen_table.options）的读写；</li>
 *     <li>动态数据源：数据源名称 → 数据库产品名见 {@link #SOURCES}（master 为 MySQL，pg 为 PostgreSQL，ora 为 Oracle）；</li>
 *     <li>主键生成器：菜单 ID 从 {@link #resetIds(long)} 设置的值之后依次递增；</li>
 *     <li>代码生成配置 {@link #PROPERTIES}（同一个对象，各测试用 {@link #resetProperties()} 恢复默认值）；</li>
 *     <li>登记 GenTable、GenTableColumn 的表信息，使 Lambda 条件能解析出列名。</li>
 * </ul>
 * 每个测试开始前调用 {@link #install()}（已装入时只恢复）。
 */
public final class GenTestContext {

    public static final GenProperties PROPERTIES = new GenProperties();

    public static final Map<String, DataSource> SOURCES = new ConcurrentHashMap<>();

    public static final DynamicRoutingDataSource DATA_SOURCES = mock(DynamicRoutingDataSource.class);

    public static final IdentifierGenerator ID_GENERATOR = mock(IdentifierGenerator.class);

    private static final AtomicLong NEXT_ID = new AtomicLong(1000);

    private static final String SERVICE_BEAN = "genTableServiceImpl";

    private static GenericApplicationContext installed;

    private GenTestContext() {
    }

    public static synchronized void install() {
        if (installed == null) {
            try {
                SOURCES.put("master", dataSource("MySQL"));
                SOURCES.put("pg", dataSource("PostgreSQL"));
                SOURCES.put("ora", dataSource("Oracle"));
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            when(DATA_SOURCES.getDataSource(anyString())).thenAnswer(invocation -> SOURCES.get(invocation.<String>getArgument(0)));
            when(DATA_SOURCES.getDataSources()).thenAnswer(invocation -> new LinkedHashMap<>(SOURCES));
            when(ID_GENERATOR.nextId(any())).thenAnswer(invocation -> NEXT_ID.incrementAndGet());
            resetProperties();
            installed = newContext(PROPERTIES);
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            for (Class<?> entity : new Class<?>[]{GenTable.class, GenTableColumn.class}) {
                if (TableInfoHelper.getTableInfo(entity) == null) {
                    TableInfoHelper.initTableInfo(assistant, entity);
                }
            }
        }
        activate(installed);
    }

    /**
     * 新建一个容器（与共用容器相同，但代码生成配置为 properties），用于检查配置在调用时读取
     */
    public static GenericApplicationContext newContext(GenProperties properties) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        context.getBeanFactory().registerSingleton("dynamicRoutingDataSource", DATA_SOURCES);
        context.getBeanFactory().registerSingleton("identifierGenerator", ID_GENERATOR);
        context.getBeanFactory().registerSingleton("genProperties", properties);
        context.refresh();
        return context;
    }

    public static void activate(GenericApplicationContext context) {
        new SpringUtil().postProcessBeanFactory(context.getBeanFactory());
        new SpringUtil().setApplicationContext(context);
    }

    public static GenericApplicationContext shared() {
        install();
        return installed;
    }

    /**
     * 默认配置：作者 tuoyu，包 org.tuoyu.system，不去除表前缀（前缀 sys_），前端模板 ele
     */
    public static void resetProperties() {
        PROPERTIES.setAuthor("tuoyu");
        PROPERTIES.setPackageName("org.tuoyu.system");
        PROPERTIES.setAutoRemovePre(false);
        PROPERTIES.setTablePrefix("sys_");
        PROPERTIES.setFrontendType("ele");
    }

    /**
     * 之后生成的 ID 依次为 start + 1、start + 2……
     */
    public static void resetIds(long start) {
        NEXT_ID.set(start);
    }

    /**
     * 把服务登记为容器中的 Bean（服务经 SpringUtils.getAopProxy(this) 调用自己的方法时取到它）
     */
    public static void registerService(Object service) {
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) shared().getBeanFactory();
        if (beanFactory.containsSingleton(SERVICE_BEAN)) {
            beanFactory.destroySingleton(SERVICE_BEAN);
        }
        beanFactory.registerSingleton(SERVICE_BEAN, service);
    }

    public static DataSource dataSource(String productName) throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(metadata.getDatabaseProductName()).thenReturn(productName);
        when(connection.getMetaData()).thenReturn(metadata);
        when(source.getConnection()).thenReturn(connection);
        return source;
    }
}
