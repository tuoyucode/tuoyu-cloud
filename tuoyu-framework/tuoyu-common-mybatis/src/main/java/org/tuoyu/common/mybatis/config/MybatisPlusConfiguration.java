package org.tuoyu.common.mybatis.config;

import cn.hutool.core.net.NetUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.factory.YmlPropertySourceFactory;
import org.tuoyu.common.core.service.DataScopeService;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.config.properties.SqlLogProperties;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.handler.DataPermissionSqlHandler;
import org.tuoyu.common.mybatis.handler.InjectionMetaObjectHandler;
import org.tuoyu.common.mybatis.handler.LogicDeleteTableInfoHandler;
import org.tuoyu.common.mybatis.handler.MybatisExceptionHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionPropagator;
import org.tuoyu.common.mybatis.injector.JoinMapperSqlInjector;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.common.mybatis.interceptor.SqlLogInterceptor;
import org.tuoyu.common.mybatis.service.SysDataScopeService;

/**
 * MyBatis-Plus 的装配：SQL 拦截器链、数据权限、字段填充、主键生成、SQL 注入器与数据访问异常的响应。
 * <p>
 * Mapper 包由配置 {@code mybatis-plus.mapperPackage} 给出；内置的 MyBatis-Plus 配置（驼峰映射、主键策略、
 * 逻辑删除值、插入与更新策略等）在 {@code common-mybatis.yml} 中。排在 mybatis-plus-join 的自动配置之前，
 * 使它看到本类的 {@code @Primary} 注入器而不另外创建注入器。
 *
 * @author tuoyu
 */
@AutoConfiguration(before = MybatisPlusJoinAutoConfiguration.class)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan("${mybatis-plus.mapperPackage}")
@PropertySource(value = "classpath:common-mybatis.yml", factory = YmlPropertySourceFactory.class)
@EnableConfigurationProperties(SqlLogProperties.class)
public class MybatisPlusConfiguration {

    /**
     * 每页条数上限的配置键，分页插件与 {@link PageParam} 用同一个值
     */
    private static final String MAX_PAGE_SIZE_KEY = "tuoyu.page.max-size";

    public MybatisPlusConfiguration() {
    }

    /**
     * SQL 拦截器链，顺序为：租户（开启多租户时）→ 数据权限 → 分页 → 乐观锁。
     * <p>
     * MyBatis-Plus 对查询按顺序对每个内部拦截器先执行 {@code willDoQuery} 再执行 {@code beforeQuery}，
     * 分页插件在 {@code willDoQuery} 中用当时的 SQL 执行统计语句，所以租户与数据权限必须排在分页之前，
     * 统计语句才带有租户与数据权限条件；修改与删除在 {@code beforePrepare} 中按同样的顺序改写。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(Environment environment,
                                                         ObjectProvider<UserContextProvider> userContext,
                                                         ObjectProvider<PermissionContext> permissionContext,
                                                         ObjectProvider<TenantLineInnerInterceptor> tenantInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 租户插件由 tuoyu-common-tenant 在开启多租户时提供
        TenantLineInnerInterceptor tenant = tenantInterceptor.getIfAvailable();
        if (tenant != null) {
            interceptor.addInnerInterceptor(tenant);
        }
        interceptor.addInnerInterceptor(new DataPermissionSqlInterceptor(new DataPermissionSqlHandler(userContext, permissionContext)));
        interceptor.addInnerInterceptor(paginationInterceptor(environment));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 分页插件：不指定数据库类型（按连接自动识别）；页码超出时回到第一页；每页上限同时设置为 {@link PageParam} 的上限
     */
    private static PaginationInnerInterceptor paginationInterceptor(Environment environment) {
        int maxPageSize = environment.getProperty(MAX_PAGE_SIZE_KEY, Integer.class, PageParam.DEFAULT_MAX_PAGE_SIZE);
        PageParam.setMaxPageSize(maxPageSize);
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setOverflow(true);
        pagination.setMaxLimit((long) maxPageSize);
        return pagination;
    }

    /**
     * 跨线程、跨服务透传数据权限状态
     */
    @Bean
    public DataPermissionPropagator dataPermissionPropagator(ObjectProvider<PermissionContext> permissionContext) {
        return new DataPermissionPropagator(permissionContext.getIfAvailable());
    }

    /**
     * 数据权限切面：在调用标注了数据权限注解的 Mapper 方法时设置数据权限
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DataPermissionPointcutAdvisor dataPermissionPointcutAdvisor() {
        return new DataPermissionPointcutAdvisor();
    }

    /**
     * 完整 SQL 日志，只在开启时注册（会输出全部参数值）
     */
    @Bean
    @ConditionalOnProperty(prefix = "mybatis-plus.sql-log", name = "enabled", havingValue = "true")
    public SqlLogInterceptor sqlLogInterceptor(SqlLogProperties properties) {
        return new SqlLogInterceptor(properties);
    }

    /**
     * 审计字段与租户字段的填充
     */
    @Bean
    public MetaObjectHandler metaObjectHandler(ObjectProvider<UserContextProvider> userContext,
                                               ObjectProvider<TenantContext> tenantContext) {
        return new InjectionMetaObjectHandler(userContext, tenantContext);
    }

    /**
     * 雪花主键生成器，按本机网卡地址区分机器，避免集群中生成重复的主键
     */
    @Bean
    public IdentifierGenerator idGenerator() {
        return new DefaultIdentifierGenerator(NetUtil.getLocalhost());
    }

    /**
     * 数据访问异常的响应
     */
    @Bean
    public MybatisExceptionHandler mybatisExceptionHandler() {
        return new MybatisExceptionHandler();
    }

    /**
     * 数据权限表达式按名称 {@code sdss} 取部门范围
     */
    @Bean("sdss")
    public SysDataScopeService sysDataScopeService(ObjectProvider<DataScopeService> dataScopeService) {
        return new SysDataScopeService(dataScopeService);
    }

    /**
     * 按配置全局开关逻辑删除
     */
    @Bean
    public PostInitTableInfoHandler postInitTableInfoHandler() {
        return new LogicDeleteTableInfoHandler();
    }

    /**
     * SQL 注入器：联表方法只注入 JoinMapper
     */
    @Bean
    @Primary
    public ISqlInjector sqlInjector() {
        return new JoinMapperSqlInjector();
    }

}
