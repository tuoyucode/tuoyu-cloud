package org.tuoyu.common.observability.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tuoyu.common.core.web.TraceIdSource;
import org.tuoyu.common.observability.trace.TracePropagator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * core 是可选依赖：不依赖 core 的服务（例如 SnailJob 服务端）引入本模块后也要能启动
 */
@DisplayName("日志追踪的自动配置：有无 core 都能加载")
class TraceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TraceAutoConfiguration.class));

    @Test
    @DisplayName("有 core 时注册 traceId 来源与透传")
    void withCore() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TraceIdSource.class);
            assertThat(context).hasSingleBean(TracePropagator.class);
        });
    }

    @Test
    @DisplayName("类路径中没有 core 时照常启动，不注册依赖 core 的组件")
    void withoutCore() {
        runner.withClassLoader(new FilteredClassLoader("org.tuoyu.common.core."))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBeanNamesForType(TracePropagator.class)).isEmpty();
                assertThat(context.containsBean("traceIdSource")).isFalse();
            });
    }

}
