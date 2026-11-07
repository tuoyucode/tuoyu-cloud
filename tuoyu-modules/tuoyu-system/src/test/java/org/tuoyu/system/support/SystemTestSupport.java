package org.tuoyu.system.support;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.tenant.core.DefaultTenantContext;
import org.tuoyu.common.tenant.core.TenantProperties;
import org.tuoyu.common.core.utils.BeanMapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.system.domain.SysClient;
import org.tuoyu.system.domain.SysConfig;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysDictData;
import org.tuoyu.system.domain.SysDictType;
import org.tuoyu.system.domain.SysLoginInfo;
import org.tuoyu.system.domain.SysMenu;
import org.tuoyu.system.domain.SysNotice;
import org.tuoyu.system.domain.SysOperLog;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysRoleDept;
import org.tuoyu.system.domain.SysRoleMenu;
import org.tuoyu.system.domain.SysSocial;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.domain.SysTenantPackage;
import org.tuoyu.system.domain.SysTenantPackageMenu;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.SysUserRole;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;

import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;

/**
 * 系统模块单元测试的公共准备：不启动 Spring 应用、不连数据库。
 * <ul>
 *     <li>对象转换使用真实的 mapstruct-plus 转换器（编译期生成的映射类），转换结果与运行时一致；</li>
 *     <li>登记实体的表信息，使 Lambda 条件能解析出列名，测试可以检查生成的 SQL 片段；</li>
 *     <li>Mapper 用 {@link #mapper(Class)} 创建：抽象方法由 Mockito 桩，
 *     本项目编写的默认方法（Vo 转换、自定义查询）与 BaseMapper 中只委托给其他方法的默认方法执行真实代码。</li>
 * </ul>
 */
public final class SystemTestSupport {

    private static final StaticApplicationContext CONTEXT = new StaticApplicationContext();

    /**
     * MyBatis-Plus BaseMapper 中只依赖其他 Mapper 方法的默认方法，测试中执行真实代码；
     * 其余默认方法（批量写入、按 ID 删除等）依赖 SqlSession，一律桩掉
     */
    private static final Set<String> DELEGATING_METHODS = Set.of("selectOne", "selectPage");

    /** RedisUtils 持有的客户端（静态字段只初始化一次），各测试自行桩、用后 {@code Mockito.reset} */
    private static final RedissonClient REDISSON = Mockito.mock(RedissonClient.class);

    private static final List<Object> EVENTS = new CopyOnWriteArrayList<>();

    private static final List<Class<?>> ENTITIES = List.of(SysClient.class, SysConfig.class, SysDept.class,
        SysDictData.class, SysDictType.class, SysLoginInfo.class, SysMenu.class, SysNotice.class, SysOperLog.class,
        SysPost.class, SysRole.class, SysRoleDept.class, SysRoleMenu.class, SysSocial.class, SysTenant.class,
        SysTenantPackage.class, SysTenantPackageMenu.class, SysUser.class, SysUserPost.class, SysUserRole.class);

    static {
        CONTEXT.getBeanFactory().registerSingleton("converter", new Converter());
        CONTEXT.getBeanFactory().registerSingleton("dataSource", mysqlDataSource());
        CONTEXT.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        CONTEXT.getBeanFactory().registerSingleton("redissonClient", REDISSON);
        CONTEXT.refresh();
        CONTEXT.addApplicationListener((ApplicationListener<ApplicationEvent>) EVENTS::add);
        new SpringUtil().setApplicationContext(CONTEXT);
        // 触发 BeanMapper 的静态初始化，使其持有上面的真实转换器
        BeanMapper.map((Object) null, Object.class);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : ENTITIES) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
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

    private SystemTestSupport() {
    }

    /**
     * 确保静态准备已执行（在测试类的字段初始化或静态块中调用）
     */
    public static void init() {
        // 类加载即完成准备
    }

    /**
     * 创建 Mapper 的模拟对象：本项目接口中的默认方法执行真实代码，其余方法由 Mockito 桩
     */
    public static <M extends BaseMapper<?>> M mapper(Class<M> type) {
        return Mockito.mock(type, Mockito.withSettings().defaultAnswer(new ProjectDefaultMethods()));
    }

    /**
     * 把对象登记为容器中的 Bean（供 {@code SpringUtils.getAopProxy}、{@code SpringUtils.getBean} 使用），
     * 同类型的旧 Bean 先移除
     */
    public static <T> T bean(T bean) {
        DefaultListableBeanFactory factory = (DefaultListableBeanFactory) CONTEXT.getBeanFactory();
        String name = bean.getClass().getName();
        if (factory.containsSingleton(name)) {
            factory.destroySingleton(name);
        }
        factory.registerSingleton(name, bean);
        return bean;
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
     * RedisUtils 使用的 Redisson 客户端（模拟对象）
     */
    public static RedissonClient redisson() {
        return REDISSON;
    }

    /**
     * 清空并返回之后通过 {@code SpringUtils.context().publishEvent} 发布的事件
     */
    public static List<Object> events() {
        return EVENTS;
    }

    /**
     * 取出发布的某类事件（{@code PayloadApplicationEvent} 会被拆开）
     */
    public static <E> List<E> events(Class<E> type) {
        List<E> found = new ArrayList<>();
        for (Object event : EVENTS) {
            Object payload = event instanceof PayloadApplicationEvent<?> p ? p.getPayload() : event;
            if (type.isInstance(payload)) {
                found.add(type.cast(payload));
            }
        }
        return found;
    }

    private static final class ProjectDefaultMethods implements Answer<Object> {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Method method = invocation.getMethod();
            boolean projectDefault = method.isDefault()
                && method.getDeclaringClass().getName().startsWith("org.tuoyu.");
            boolean delegating = method.isDefault()
                && method.getDeclaringClass() == BaseMapper.class
                && DELEGATING_METHODS.contains(method.getName())
                && method.getParameterCount() == ("selectPage".equals(method.getName()) ? 2 : 1);
            if (projectDefault || delegating) {
                return invocation.callRealMethod();
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        }
    }
}
