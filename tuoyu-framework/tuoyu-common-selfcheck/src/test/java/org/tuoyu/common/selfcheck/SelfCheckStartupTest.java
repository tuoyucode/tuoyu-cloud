package org.tuoyu.common.selfcheck;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端：启动真实的 Spring Boot 应用，验证自检通过 spring.factories 生效、失败时生成完整的失败报告。
 */
@DisplayName("启动自检端到端测试")
class SelfCheckStartupTest {

    @Configuration(proxyBeanMethods = false)
    static class EmptyApplication {
    }

    private static SpringApplication application(Map<String, Object> properties) {
        SpringApplication application = new SpringApplication(EmptyApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        Map<String, Object> defaults = new HashMap<>(properties);
        defaults.put("spring.main.banner-mode", "off");
        application.setDefaultProperties(defaults);
        return application;
    }

    @Test
    @DisplayName("缺少必填变量时拒绝启动，失败报告列出变量名与修复方式")
    void shouldRefuseToStartWhenVariableMissing() {
        SpringApplication application = application(Map.of("demo.secret", "${TUOYU_SELFCHECK_TEST_MISSING}"));

        SelfCheckFailedException e = assertThrows(SelfCheckFailedException.class, application::run);
        FailureAnalysis analysis = new SelfCheckFailureAnalyzer().analyze(e);

        assertTrue(analysis.getDescription().contains("[C1]"));
        assertTrue(analysis.getDescription().contains("TUOYU_SELFCHECK_TEST_MISSING"));
        assertTrue(analysis.getAction().contains(".env.example"));
        assertTrue(analysis.getAction().contains(SelfCheckEnvironmentPostProcessor.SKIP_KEY + "=C1"));
    }

    @Test
    @DisplayName("生产环境配置不安全时拒绝启动")
    void shouldRefuseUnsafeProductionConfig() {
        SpringApplication application = application(Map.of(
            "spring.profiles.active", "prod",
            "management.endpoints.web.exposure.include", "*"));

        SelfCheckFailedException e = assertThrows(SelfCheckFailedException.class, application::run);

        assertTrue(e.isProduction());
        assertTrue(new SelfCheckFailureAnalyzer().analyze(e).getDescription().contains("[P2]"));
    }

    @Test
    @DisplayName("配置齐全时正常启动")
    void shouldStartWhenConfigurationIsComplete() {
        SpringApplication application = application(Map.of(
            "demo.secret", "${TUOYU_SELFCHECK_TEST_PROVIDED}",
            "TUOYU_SELFCHECK_TEST_PROVIDED", "provided"));

        try (ConfigurableApplicationContext context = application.run()) {
            assertTrue(context.isActive());
        }
    }

}
