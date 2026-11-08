package org.tuoyu.common.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 数据库迁移自动配置：只有负责某个库的服务才依赖本模块。
 * <p>
 * 开关为 spring.flyway.enabled（仓库配置写作 ${TUOYU_FLYWAY_ENABLED:true}）。
 * 单体在一个进程中负责多个库：主库由 spring.flyway.* 连接，其他库写在 tuoyu.flyway.additional 中，
 * 在主库之后依次迁移，规则相同。
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class)
@ConditionalOnClass(Flyway.class)
@ConditionalOnBooleanProperty(name = "spring.flyway.enabled", matchIfMissing = true)
@EnableConfigurationProperties(TuoyuFlywayProperties.class)
public class TuoyuFlywayAutoConfiguration {

    /**
     * 框架迁移约定，最后应用，覆盖 spring.flyway.* 中的同名配置
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public FlywayConfigurationCustomizer tuoyuFlywayConventionsCustomizer(TuoyuFlywayProperties properties) {
        return configuration -> FlywayConventions.apply(configuration, properties.getDatabase());
    }

    /**
     * 只对 MySQL、PostgreSQL 执行迁移，其他数据库跳过；主库之后再迁移 tuoyu.flyway.additional 中的库
     */
    @Bean
    @ConditionalOnMissingBean
    public FlywayMigrationStrategy tuoyuFlywayMigrationStrategy(TuoyuFlywayProperties properties) {
        SupportedDatabaseMigrationStrategy strategy = new SupportedDatabaseMigrationStrategy();
        return flyway -> {
            strategy.migrate(flyway);
            for (Map.Entry<String, TuoyuFlywayProperties.Connection> entry : properties.getAdditional().entrySet()) {
                strategy.migrate(additional(flyway, entry.getKey(), entry.getValue()));
            }
        };
    }

    /**
     * 其他库的 Flyway：与主库使用同一个类加载器查找迁移脚本，约定相同
     *
     * @param primary    主库的 Flyway
     * @param database   库名，决定迁移脚本目录
     * @param connection 连接
     */
    static Flyway additional(Flyway primary, String database, TuoyuFlywayProperties.Connection connection) {
        if (!StringUtils.hasText(connection.getUrl())) {
            throw new IllegalStateException("tuoyu.flyway.additional." + database + ".url 未配置");
        }
        FluentConfiguration configuration = Flyway.configure(primary.getConfiguration().getClassLoader())
            .dataSource(connection.getUrl(), connection.getUser(), connection.getPassword());
        FlywayConventions.apply(configuration, database);
        return configuration.load();
    }

}
