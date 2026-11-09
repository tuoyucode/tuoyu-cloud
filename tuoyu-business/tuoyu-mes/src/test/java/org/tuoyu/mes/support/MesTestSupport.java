package org.tuoyu.mes.support;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantProperties;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Optional;

/**
 * MES 单元测试的公共准备：不启动 Spring 应用、不连数据库。
 * <ul>
 *     <li>对象转换使用真实的 mapstruct-plus 转换器（编译期生成的映射类）；</li>
 *     <li>登记全部 MES 实体的表信息，Lambda 条件能解析出列名；</li>
 *     <li>Mapper 用 {@link #mapper(Class)} 创建：除 {@code selectPage} 外的方法都由 Mockito 桩，
 *     测试直接桩 {@code selectById}、{@code selectList}、{@code selectOne}、{@code exists}、{@code update} 等；</li>
 *     <li>{@link #loginAs(long)} 设置当前用户，测试结束时用返回的提供者恢复。</li>
 * </ul>
 */
public final class MesTestSupport {

    private static final StaticApplicationContext CONTEXT = new StaticApplicationContext();

    static {
        CONTEXT.getBeanFactory().registerSingleton("converter", new Converter());
        CONTEXT.getBeanFactory().registerSingleton("dataSource", mysqlDataSource());
        CONTEXT.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        CONTEXT.refresh();
        new SpringUtil().setApplicationContext(CONTEXT);
        // 触发 BeanMapper 的静态初始化，使其持有上面的真实转换器
        BeanMapper.map((Object) null, Object.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        for (BeanDefinition definition : scanner.findCandidateComponents("org.tuoyu.mes")) {
            try {
                Class<?> entity = Class.forName(definition.getBeanClassName());
                if (TableInfoHelper.getTableInfo(entity) == null) {
                    TableInfoHelper.initTableInfo(assistant, entity);
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private MesTestSupport() {
    }

    /**
     * 确保静态准备已执行（在测试类的字段初始化或静态块中调用）
     */
    public static void init() {
        // 类加载即完成准备
    }

    /**
     * 创建 Mapper 的模拟对象：本项目接口中的默认方法与 BaseMapper 的 selectPage 执行真实代码，其余方法由 Mockito 桩
     */
    public static <M extends BaseMapper<?>> M mapper(Class<M> type) {
        return Mockito.mock(type, Mockito.withSettings().defaultAnswer(new ProjectDefaultMethods()));
    }

    /**
     * 不开启真实事务的事务管理器（TransactionTemplate 直接执行回调）
     */
    public static PlatformTransactionManager noopTransactionManager() {
        return Mockito.mock(PlatformTransactionManager.class);
    }

    /**
     * 以指定用户登录，返回原来的提供者（测试结束时传给 {@link #restore(UserContextProvider)}）
     */
    public static UserContextProvider loginAs(long userId) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        user.setUserName("user" + userId);
        user.setNickname("用户" + userId);
        user.setTenantId("default");
        return UserContext.setProvider(() -> Optional.of(user));
    }

    public static void restore(UserContextProvider previous) {
        UserContext.setProvider(previous);
    }

    /**
     * 开启或关闭多租户（安装租户上下文，不依赖登录用户）。测试结束后应调用 {@code tenant(false)} 复原
     */
    public static TenantProperties tenant(boolean enabled) {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(enabled);
        new DefaultTenantContext(properties, new StaticListableBeanFactory().getBeanProvider(UserContextProvider.class), id -> null);
        return properties;
    }

    /**
     * 只回答数据库类型（MySQL）的数据源，供按数据库方言拼接 SQL 片段的代码使用，不会真正执行 SQL
     */
    private static DynamicRoutingDataSource mysqlDataSource() {
        try {
            DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
            Mockito.when(metaData.getDatabaseProductName()).thenReturn("MySQL");
            Connection connection = Mockito.mock(Connection.class);
            Mockito.when(connection.getMetaData()).thenReturn(metaData);
            DataSource dataSource = Mockito.mock(DataSource.class);
            Mockito.when(dataSource.getConnection()).thenReturn(connection);
            DynamicRoutingDataSource routing = Mockito.mock(DynamicRoutingDataSource.class);
            Mockito.when(routing.determineDataSource()).thenReturn(dataSource);
            Mockito.when(routing.getDataSource(Mockito.anyString())).thenReturn(dataSource);
            return routing;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class ProjectDefaultMethods implements Answer<Object> {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Method method = invocation.getMethod();
            boolean projectDefault = method.isDefault()
                && method.getDeclaringClass().getName().startsWith("org.tuoyu.");
            boolean selectPage = method.isDefault()
                && method.getDeclaringClass() == BaseMapper.class
                && "selectPage".equals(method.getName())
                && method.getParameterCount() == 2;
            if (projectDefault || selectPage) {
                return invocation.callRealMethod();
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        }
    }

}
