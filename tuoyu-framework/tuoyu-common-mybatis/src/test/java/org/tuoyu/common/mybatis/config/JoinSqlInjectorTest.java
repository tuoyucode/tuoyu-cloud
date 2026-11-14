package org.tuoyu.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.baomidou.mybatisplus.core.injector.AbstractSqlInjector;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.github.yulichang.autoconfigure.MybatisPlusJoinAutoConfiguration;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.common.mybatis.TestSpringContext;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * SQL 注入器：mybatis-plus-join 的联表方法只注入继承了 JoinMapper（MPJBaseMapper）的 Mapper；
 * 其他 Mapper 保持 MyBatis-Plus 自带的方法，不替换为 mybatis-plus-join 的兼容实现。
 * <p>
 * 按应用中的装配方式（MyBatis-Plus、mybatis-plus-join 与本模块的自动配置，按各自声明的顺序）取实际生效的注入器。
 */
@DisplayName("SQL 注入器：联表方法只注入 JoinMapper")
class JoinSqlInjectorTest {

    @TableName("inj_entity")
    static class InjEntity {
        @TableId
        private Long id;
        private String name;
    }

    interface PlainMapper extends BaseMapper<InjEntity> {
    }

    interface JoinedMapper extends BaseMapper<InjEntity>, MPJBaseMapper<InjEntity> {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class, MybatisPlusJoinAutoConfiguration.class,
            MybatisPlusConfiguration.class))
        .withBean(DataSource.class, () -> mock(DataSource.class))
        .withPropertyValues("mybatis-plus.mapperPackage=org.tuoyu.common.mybatis.nomapper");

    @BeforeEach
    void installContext() {
        TestSpringContext.install();
    }

    private static List<String> methods(ISqlInjector injector, Class<?> mapper) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfo tableInfo = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, mapper.getName()), InjEntity.class);
        List<AbstractMethod> list = ((AbstractSqlInjector) injector).getMethodList(configuration, mapper, tableInfo);
        return list.stream().map(m -> m.getClass().getName()).toList();
    }

    @Test
    @DisplayName("普通 Mapper 只有 MyBatis-Plus 的方法；JoinMapper 另有联表方法，并由 mybatis-plus-join 接管条件查询")
    void joinMethodsOnlyForJoinMapper() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
            ISqlInjector injector = GlobalConfigUtils.getGlobalConfig(factory.getConfiguration()).getSqlInjector();
            assertThat(injector).isInstanceOf(AbstractSqlInjector.class);

            List<String> plain = methods(injector, PlainMapper.class);
            assertThat(plain).isNotEmpty().allSatisfy(name -> assertThat(name).startsWith("com.baomidou.mybatisplus.core.injector.methods."));
            assertThat(plain).contains("com.baomidou.mybatisplus.core.injector.methods.SelectList",
                "com.baomidou.mybatisplus.core.injector.methods.Update");

            List<String> joined = methods(injector, JoinedMapper.class);
            assertThat(joined).contains("com.github.yulichang.method.SelectJoinList", "com.github.yulichang.method.SelectJoinPage",
                "com.github.yulichang.method.SelectJoinCount", "com.github.yulichang.method.mp.SelectList");
            assertThat(joined).doesNotContain("com.baomidou.mybatisplus.core.injector.methods.SelectList");
        });
    }

}
