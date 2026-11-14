package org.tuoyu.common.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.mybatis.TestSpringContext;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.handler.InjectionMetaObjectHandler;
import org.tuoyu.common.mybatis.handler.LogicDeleteTableInfoHandler;
import org.tuoyu.common.mybatis.handler.MybatisExceptionHandler;
import org.tuoyu.common.mybatis.helper.DataPermissionPropagator;
import org.tuoyu.common.mybatis.interceptor.DataPermissionSqlInterceptor;
import org.tuoyu.common.mybatis.interceptor.SqlLogInterceptor;
import org.tuoyu.common.mybatis.service.SysDataScopeService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * MyBatis 配置：拦截器的顺序（租户 → 数据权限 → 分页 → 乐观锁）、每页上限、各个 Bean
 */
@DisplayName("MybatisPlusConfiguration：拦截器顺序与 Bean")
class MybatisPlusConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(MybatisPlusConfiguration.class)
        .withPropertyValues("mybatis-plus.mapperPackage=org.tuoyu.common.mybatis.nomapper");

    @BeforeEach
    void installContext() {
        TestSpringContext.install();
    }

    @AfterEach
    void resetPageSize() {
        PageParam.setMaxPageSize(PageParam.DEFAULT_MAX_PAGE_SIZE);
    }

    private static List<Class<?>> types(MybatisPlusInterceptor interceptor) {
        return interceptor.getInterceptors().stream().<Class<?>>map(InnerInterceptor::getClass).toList();
    }

    @Test
    @DisplayName("没有租户插件时：数据权限、分页、乐观锁；分页统计在数据权限改写之后")
    void interceptorOrderWithoutTenant() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            assertThat(types(interceptor)).containsExactly(DataPermissionSqlInterceptor.class,
                PaginationInnerInterceptor.class, OptimisticLockerInnerInterceptor.class);
            PaginationInnerInterceptor pagination = (PaginationInnerInterceptor) interceptor.getInterceptors().get(1);
            assertThat(pagination.getMaxLimit()).isEqualTo(500L);
            assertThat(pagination.isOverflow()).isTrue();
        });
    }

    @Test
    @DisplayName("有租户插件时放在最前；每页上限取 tuoyu.page.max-size，同时作为 PageParam 的上限")
    void interceptorOrderWithTenant() {
        TenantLineInnerInterceptor tenant = mock(TenantLineInnerInterceptor.class);
        runner.withBean(TenantLineInnerInterceptor.class, () -> tenant)
            .withPropertyValues("tuoyu.page.max-size=200")
            .run(context -> {
                assertThat(context).hasNotFailed();
                MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
                assertThat(interceptor.getInterceptors()).hasSize(4);
                assertThat(interceptor.getInterceptors().get(0)).isSameAs(tenant);
                assertThat(interceptor.getInterceptors().get(1)).isInstanceOf(DataPermissionSqlInterceptor.class);
                assertThat(interceptor.getInterceptors().get(2)).isInstanceOf(PaginationInnerInterceptor.class);
                assertThat(interceptor.getInterceptors().get(3)).isInstanceOf(OptimisticLockerInnerInterceptor.class);
                assertThat(((PaginationInnerInterceptor) interceptor.getInterceptors().get(2)).getMaxLimit()).isEqualTo(200L);
                assertThat(PageParam.getMaxPageSize()).isEqualTo(200);
            });
    }

    @Test
    @DisplayName("注册的 Bean：数据权限切面与透传、sdss、字段填充、主键生成、异常处理、逻辑删除开关；内置配置已加载")
    void beans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataPermissionPointcutAdvisor.class);
            assertThat(context).hasSingleBean(DataPermissionPropagator.class);
            assertThat(context.getBean(DataPermissionPropagator.class)).isInstanceOf(ContextPropagator.class);
            assertThat(context.getBean("sdss")).isInstanceOf(SysDataScopeService.class);
            assertThat(context.getBean(MetaObjectHandler.class)).isInstanceOf(InjectionMetaObjectHandler.class);
            assertThat(context).hasSingleBean(IdentifierGenerator.class);
            assertThat(context.getBean(IdentifierGenerator.class).nextId(null).longValue()).isPositive();
            assertThat(context).hasSingleBean(MybatisExceptionHandler.class);
            assertThat(context.getBean(PostInitTableInfoHandler.class)).isInstanceOf(LogicDeleteTableInfoHandler.class);
            assertThat(context).doesNotHaveBean(SqlLogInterceptor.class);
            assertThat(context.getEnvironment().getProperty("mybatis-plus.global-config.dbConfig.idType")).isEqualTo("ASSIGN_ID");
            assertThat(context.getEnvironment().getProperty("mybatis-plus.configuration.mapUnderscoreToCamelCase")).isEqualTo("true");
        });
    }

    @Test
    @DisplayName("完整 SQL 日志只在 mybatis-plus.sql-log.enabled=true 时注册")
    void sqlLog() {
        runner.withPropertyValues("mybatis-plus.sql-log.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SqlLogInterceptor.class);
        });
        runner.withPropertyValues("mybatis-plus.sql-log.enabled=false").run(context ->
            assertThat(context).doesNotHaveBean(SqlLogInterceptor.class));
    }

}
