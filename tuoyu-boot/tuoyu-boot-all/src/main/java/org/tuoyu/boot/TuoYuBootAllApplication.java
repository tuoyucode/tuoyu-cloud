package org.tuoyu.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.tuoyu.boot.module.MonolithModuleScanRegistrar;

/**
 * 单体启动器：一个进程包含全部业务模块。
 * <p>
 * 扫描各业务模块的包；各模块自己的启动类（微服务方式的入口）不参与，避免重复处理它们的配置。
 * 业务代码与微服务方式完全相同：跨模块调用由 tuoyu-common-rpc 注入本地调用代理（不参与调用方事务），
 * URL 前缀与网关路由一致（{@link org.tuoyu.boot.web.ModulePathPrefixConfiguration}），
 * 各模块使用自己的库（{@link org.tuoyu.boot.datasource.ModuleDataSourceAdvisor}）。
 * <p>
 * 框架自带的模块在下面的 {@code @ComponentScan} 中列出；业务模块（例如 tuoyu-business 下的模块）在自己的 jar 中声明
 * 接入方式（{@link org.tuoyu.boot.module.MonolithModule}），加入依赖即可，由 {@link MonolithModuleScanRegistrar} 扫描。
 *
 * @author tuoyu
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {
        "org.tuoyu.boot",
        "org.tuoyu.auth",
        "org.tuoyu.system",
        "org.tuoyu.resource",
        "org.tuoyu.workflow",
        "org.tuoyu.gen",
        "org.tuoyu.job"
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = SpringBootApplication.class)
    })
@Import(MonolithModuleScanRegistrar.class)
public class TuoYuBootAllApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TuoYuBootAllApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  单体启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }

}
