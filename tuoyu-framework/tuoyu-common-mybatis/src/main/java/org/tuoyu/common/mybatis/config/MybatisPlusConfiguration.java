package org.tuoyu.common.mybatis.config;

import cn.hutool.core.net.NetUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.factory.YmlPropertySourceFactory;
import org.tuoyu.common.core.service.DataScopeService;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.config.properties.SqlLogProperties;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.common.mybatis.handler.InjectionMetaObjectHandler;
import org.tuoyu.common.mybatis.handler.MybatisExceptionHandler;
import org.tuoyu.common.mybatis.handler.DataPermissionSqlHandler;
import org.tuoyu.common.mybatis.handler.LogicDeleteTableInfoHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionPropagator;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.common.mybatis.interceptor.SqlLogInterceptor;
import org.tuoyu.common.mybatis.service.SysDataScopeService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * mybatis-plus配置类(下方注释有插件介绍)
 *
 * @author Lion Li
 */
@AutoConfiguration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan("${mybatis-plus.mapperPackage}")
@PropertySource(value = "classpath:common-mybatis.yml", factory = YmlPropertySourceFactory.class)
@EnableConfigurationProperties(SqlLogProperties.class)
public class MybatisPlusConfiguration {

    /**
     * 每页记录数上限的配置项
     */
    public static final String MAX_PAGE_SIZE_KEY = "tuoyu.page.max-size";

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(Environment environment,
                                                         ObjectProvider<UserContext> userContext,
                                                         ObjectProvider<PermissionContext> permissionContext,
                                                         ObjectProvider<TenantLineInnerInterceptor> tenantInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户插件 必须放到第一位（由 tuoyu-common-tenant 提供，开启多租户时才有）
        tenantInterceptor.ifAvailable(interceptor::addInnerInterceptor);
        // 数据权限处理
        interceptor.addInnerInterceptor(new DataPermissionSqlInterceptor(new DataPermissionSqlHandler(userContext, permissionContext)));
        // 分页插件
        interceptor.addInnerInterceptor(paginationInnerInterceptor(
            environment.getProperty(MAX_PAGE_SIZE_KEY, Integer.class, PageQuery.DEFAULT_MAX_PAGE_SIZE)));
        // 乐观锁插件
        interceptor.addInnerInterceptor(optimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 跨服务、跨线程透传数据权限状态（是否忽略数据权限、接口的访问要求）
     */
    @Bean
    public DataPermissionPropagator dataPermissionPropagator(ObjectProvider<PermissionContext> permissionContext) {
        return new DataPermissionPropagator(permissionContext.getIfAvailable());
    }

    /**
     * 数据权限切面处理器
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public DataPermissionPointcutAdvisor dataPermissionPointcutAdvisor() {
        return new DataPermissionPointcutAdvisor();
    }

    /**
     * 分页插件，自动识别数据库类型
     */
    public PaginationInnerInterceptor paginationInnerInterceptor(int maxPageSize) {
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        // 分页合理化
        paginationInnerInterceptor.setOverflow(true);
        // 每页记录数上限：PageQuery 先按上限处理，此处兜底绕过 PageQuery 直接构造分页对象的代码
        PageQuery.setMaxPageSize(maxPageSize);
        paginationInnerInterceptor.setMaxLimit((long) maxPageSize);
        return paginationInnerInterceptor;
    }

    /**
     * 乐观锁插件
     */
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        return new OptimisticLockerInnerInterceptor();
    }

    /**
     * 完整 SQL 日志拦截器
     */
    @Bean
    @ConditionalOnProperty(prefix = "mybatis-plus.sql-log", name = "enabled", havingValue = "true")
    public SqlLogInterceptor sqlLogInterceptor(SqlLogProperties sqlLogProperties) {
        return new SqlLogInterceptor(sqlLogProperties);
    }

    /**
     * 元对象字段填充控制器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler(ObjectProvider<UserContext> userContext, ObjectProvider<TenantContext> tenantContext) {
        return new InjectionMetaObjectHandler(userContext, tenantContext);
    }

    /**
     * 使用网卡信息绑定雪花生成器
     * 防止集群雪花ID重复
     */
    @Bean
    public IdentifierGenerator idGenerator() {
        return new DefaultIdentifierGenerator(NetUtil.getLocalhost());
    }

    /**
     * 异常处理器
     */
    @Bean
    public MybatisExceptionHandler mybatisExceptionHandler() {
        return new MybatisExceptionHandler();
    }

    /**
     * 数据权限 SpEL 中使用的服务（{@code @sdss}），数据来自 {@link DataScopeService}
     */
    @Bean("sdss")
    public SysDataScopeService sysDataScopeService(ObjectProvider<DataScopeService> dataScopeService) {
        return new SysDataScopeService(dataScopeService);
    }

    /**
     * 初始化表对象处理器
     */
    @Bean
    public PostInitTableInfoHandler postInitTableInfoHandler() {
        return new LogicDeleteTableInfoHandler();
    }

    /**
     * PaginationInnerInterceptor 分页插件，自动识别数据库类型
     * https://baomidou.com/pages/97710a/
     * OptimisticLockerInnerInterceptor 乐观锁插件
     * https://baomidou.com/pages/0d93c0/
     * MetaObjectHandler 元对象字段填充控制器
     * https://baomidou.com/pages/4c6bcf/
     * ISqlInjector sql注入器
     * https://baomidou.com/pages/42ea4a/
     * BlockAttackInnerInterceptor 如果是对全表的删除或更新操作，就会终止该操作
     * https://baomidou.com/pages/f9a237/
     * IllegalSQLInnerInterceptor sql性能规范插件(垃圾SQL拦截)
     * IdentifierGenerator 自定义主键策略
     * https://baomidou.com/pages/568eb2/
     * TenantLineInnerInterceptor 多租户插件
     * https://baomidou.com/pages/aef2f2/
     * DynamicTableNameInnerInterceptor 动态表名插件
     * https://baomidou.com/pages/2a45ff/
     */

}
