package org.tuoyu.snailjob.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.tuoyu.common.selfcheck.SelfCheckEnvironmentPostProcessor;

import javax.sql.DataSource;

/**
 * SnailJob 服务端的安全检查。
 * <p>
 * 用自动配置注册：服务端启动的是 SnailJob 自己的启动类，不扫描本模块的包。
 * 在应用启动完成后执行（此时数据库迁移已经完成，sj_system_user 一定存在）
 *
 * @author tuoyu
 */
@AutoConfiguration
public class SnailJobSecurityAutoConfiguration {

    @Bean
    public ApplicationRunner snailJobAdminPasswordGuard(DataSource dataSource, ConfigurableEnvironment environment) {
        return args -> new AdminPasswordGuard(new JdbcTemplate(dataSource),
            environment.getProperty(AdminPasswordGuard.PROPERTY, ""),
            SelfCheckEnvironmentPostProcessor.isProduction(environment)).check();
    }

}
