package org.tuoyu.common.mybatis;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.tuoyu.common.core.service.DataScopeService;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.service.SysDataScopeService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测试共用的 Spring 容器（经 hutool 的静态容器持有者访问）。全部测试共用一个容器，避免各测试类互相覆盖：
 * <ul>
 *     <li>名为 {@code sdss} 的 {@link SysDataScopeService}，部门范围取自 {@link #ROLE_CUSTOM}、{@link #DEPT_AND_CHILD}；</li>
 *     <li>动态数据源 {@link #DATA_SOURCES}：按名称取 {@link #SOURCES} 中的数据源，当前数据源取线程上压入的名称；</li>
 *     <li>主键生成器 {@link #ID_GENERATOR}；</li>
 *     <li>可修改的配置 {@link #PROPERTIES}。</li>
 * </ul>
 */
public final class TestSpringContext {

    /**
     * 部门范围中表示“返回 null”的值（ConcurrentHashMap 不能存 null）
     */
    public static final String NULL = "<null>";

    public static final Map<Long, String> ROLE_CUSTOM = new ConcurrentHashMap<>();

    public static final Map<Long, String> DEPT_AND_CHILD = new ConcurrentHashMap<>();

    /**
     * 每次调用 DataScopeService 时是否处于忽略数据权限的代码块中
     */
    public static final List<Boolean> IGNORING_DURING_LOOKUP = new CopyOnWriteArrayList<>();

    public static final Map<String, DataSource> SOURCES = new ConcurrentHashMap<>();

    public static final Map<String, Object> PROPERTIES = new ConcurrentHashMap<>();

    public static final DynamicRoutingDataSource DATA_SOURCES = mock(DynamicRoutingDataSource.class);

    public static final IdentifierGenerator ID_GENERATOR = mock(IdentifierGenerator.class);

    private static final DataScopeService DATA_SCOPE = new DataScopeService() {
        @Override
        public String getRoleCustom(Long roleId) {
            IGNORING_DURING_LOOKUP.add(DataPermissionHelper.isIgnoring());
            return value(ROLE_CUSTOM.getOrDefault(roleId, "-1"));
        }

        @Override
        public String getDeptAndChild(Long deptId) {
            IGNORING_DURING_LOOKUP.add(DataPermissionHelper.isIgnoring());
            return value(DEPT_AND_CHILD.getOrDefault(deptId, "-1"));
        }
    };

    private static GenericApplicationContext installed;

    private static String value(String stored) {
        return NULL.equals(stored) ? null : stored;
    }

    private TestSpringContext() {
    }

    /**
     * 装入共用容器。其他测试可能换掉 hutool 中保存的容器，所以每个测试开始前调用（已装入时只恢复）
     */
    public static synchronized void install() {
        if (installed != null) {
            if (SpringUtil.getApplicationContext() != installed) {
                new SpringUtil().setApplicationContext(installed);
            }
            return;
        }
        when(DATA_SOURCES.getDataSource(anyString())).thenAnswer(invocation -> SOURCES.get(invocation.<String>getArgument(0)));
        when(DATA_SOURCES.determineDataSource()).thenAnswer(invocation -> {
            String key = com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.peek();
            return SOURCES.get(key == null ? "primary" : key);
        });
        when(DATA_SOURCES.getDataSources()).thenAnswer(invocation -> new LinkedHashMap<>(SOURCES));

        StaticListableBeanFactory scopeBeans = new StaticListableBeanFactory();
        scopeBeans.addBean("dataScopeService", DATA_SCOPE);
        GenericApplicationContext context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", PROPERTIES));
        context.getBeanFactory().registerSingleton("sdss", new SysDataScopeService(scopeBeans.getBeanProvider(DataScopeService.class)));
        context.getBeanFactory().registerSingleton("dynamicRoutingDataSource", DATA_SOURCES);
        context.getBeanFactory().registerSingleton("identifierGenerator", ID_GENERATOR);
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        installed = context;
    }

    /**
     * 返回指定数据库产品名的数据源，记录打开连接的次数
     */
    public static DataSource dataSource(String productName, List<String> opened) throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(metadata.getDatabaseProductName()).thenReturn(productName);
        when(connection.getMetaData()).thenReturn(metadata);
        when(source.getConnection()).thenAnswer(invocation -> {
            opened.add(productName);
            return connection;
        });
        return source;
    }

    public static DataSource dataSource(String productName) throws SQLException {
        return dataSource(productName, new ArrayList<>());
    }

}
