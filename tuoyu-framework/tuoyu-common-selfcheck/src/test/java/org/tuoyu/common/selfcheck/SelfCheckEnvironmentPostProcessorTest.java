package org.tuoyu.common.selfcheck;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SelfCheckEnvironmentPostProcessor 单元测试")
class SelfCheckEnvironmentPostProcessorTest {

    private final SelfCheckEnvironmentPostProcessor processor =
        new SelfCheckEnvironmentPostProcessor((DeferredLogFactory) Supplier::get);

    private final Map<String, Object> config = new HashMap<>();

    private StandardEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new HashMap<>()));
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, new HashMap<>()));
        environment.getPropertySources().addLast(new MapPropertySource("config", config));
        // 一份会触发 P2 的不安全配置
        config.put("management.endpoints.web.exposure.include", "*");
        config.put("springdoc.api-docs.enabled", "false");
    }

    @Test
    @DisplayName("开发模式下生产检查只警告，不阻止启动")
    void shouldOnlyWarnInDevelopment() {
        assertDoesNotThrow(() -> processor.apply(environment, true));
    }

    @Test
    @DisplayName("激活 prod 时生产检查未通过即拒绝启动")
    void shouldFailInProduction() {
        environment.setActiveProfiles("prod");

        SelfCheckFailedException e = assertThrows(SelfCheckFailedException.class, () -> processor.apply(environment, true));

        assertTrue(e.isProduction());
        assertEquals(List.of(SelfChecks.P2), e.getProblems().stream().map(SelfCheckProblem::id).toList());
    }

    @Test
    @DisplayName("strict=true 时在非 prod 环境也执行生产检查")
    void shouldFailWhenStrict() {
        config.put(SelfCheckEnvironmentPostProcessor.STRICT_KEY, "true");

        assertThrows(SelfCheckFailedException.class, () -> processor.apply(environment, true));
    }

    @Test
    @DisplayName("C1 在开发模式下同样拒绝启动")
    void shouldFailOnMissingVariablesInDevelopment() {
        config.put("spring.data.redis.password", "${TUOYU_REDIS_PASSWORD}");

        SelfCheckFailedException e = assertThrows(SelfCheckFailedException.class, () -> processor.apply(environment, true));

        assertTrue(!e.isProduction());
        assertEquals(SelfChecks.C1, e.getProblems().getFirst().id());
    }

    @Test
    @DisplayName("按编号跳过指定检查，编号大小写与空格不敏感")
    void shouldSkipById() {
        environment.setActiveProfiles("prod");
        config.put(SelfCheckEnvironmentPostProcessor.SKIP_KEY, " p2 ");

        assertDoesNotThrow(() -> processor.apply(environment, true));
    }

    @Test
    @DisplayName("一次报告全部问题")
    void shouldReportAllProblemsAtOnce() {
        environment.setActiveProfiles("prod");
        config.put("spring.data.redis.password", "${TUOYU_REDIS_PASSWORD}");
        config.put("springdoc.api-docs.enabled", "true");

        SelfCheckFailedException e = assertThrows(SelfCheckFailedException.class, () -> processor.apply(environment, true));

        assertEquals(List.of(SelfChecks.C1, SelfChecks.P2, SelfChecks.P4),
            e.getProblems().stream().map(SelfCheckProblem::id).toList());
    }

    @Test
    @DisplayName("自检结果完整保存：通过、未通过、跳过、不适用的项都在报告中")
    void shouldRecordFullReport() {
        config.put(SelfCheckEnvironmentPostProcessor.SKIP_KEY, "P3");

        processor.apply(environment, false);

        SelfCheckReport report = SelfCheckReport.latest().orElseThrow();
        assertEquals(List.of("C1", "C2", "P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8", "P9"),
            report.results().stream().map(SelfCheckReport.Result::id).toList());
        Map<String, SelfCheckReport.Result> byId = report.results().stream()
            .collect(Collectors.toMap(SelfCheckReport.Result::id, r -> r));
        assertEquals(SelfCheckReport.Status.PASSED, byId.get("C1").status());
        assertEquals(SelfCheckReport.Scope.RUNTIME, byId.get("C1").scope());
        assertEquals(SelfCheckReport.Status.FAILED, byId.get("P2").status());
        assertEquals(List.of("management.endpoints.web.exposure.include=*"), byId.get("P2").details());
        assertEquals(SelfCheckReport.Status.SKIPPED, byId.get("P3").status());
        assertEquals(SelfCheckReport.Status.NOT_APPLICABLE, byId.get("P4").status());
        // 不从 Nacos 加载配置、没有验证码开关的服务，P6、P7 不适用；P8 未配置时按默认强度 10 通过；P9 未配置时通过
        assertEquals(SelfCheckReport.Status.NOT_APPLICABLE, byId.get("P6").status());
        assertEquals(SelfCheckReport.Status.NOT_APPLICABLE, byId.get("P7").status());
        assertEquals(SelfCheckReport.Status.PASSED, byId.get("P8").status());
        assertEquals(SelfCheckReport.Status.PASSED, byId.get("P9").status());
        assertEquals(1, report.productionProblemCount());
        assertFalse(report.production());
    }

    @Nested
    @DisplayName("P6 Nacos 鉴权探测")
    class NacosAuth {

        private final List<NacosAuthProbe.Target> probed = new ArrayList<>();

        private NacosAuthProbe.Result result = new NacosAuthProbe.Result(NacosAuthProbe.State.ENABLED, "403");

        private final SelfCheckEnvironmentPostProcessor probing = new SelfCheckEnvironmentPostProcessor(
            (DeferredLogFactory) Supplier::get, target -> {
                probed.add(target);
                return result;
            });

        @BeforeEach
        void loadedFromNacos() {
            config.remove("management.endpoints.web.exposure.include");
            config.put("spring.config.import[0]", "optional:nacos:application-common.yml");
            config.put(SelfChecks.COMMON_LOADED_MARKER, "true");
            config.put("spring.cloud.nacos.server-addr", "10.0.0.5:8848");
            config.put("spring.cloud.nacos.config.namespace", "tuoyu");
            environment.setActiveProfiles("prod");
        }

        @Test
        @DisplayName("Nacos 拒绝匿名请求时通过，探测的是本服务加载的公共配置")
        void shouldPassWhenAuthEnabled() {
            assertDoesNotThrow(() -> probing.apply(environment, true));

            assertEquals(List.of(new NacosAuthProbe.Target("http://10.0.0.5:8848/nacos", "tuoyu", "DEFAULT_GROUP",
                "application-common.yml")), probed);
            assertEquals(SelfCheckReport.Status.PASSED, statusOf("P6"));
        }

        @Test
        @DisplayName("匿名请求能读取配置时，生产模式拒绝启动")
        void shouldFailWhenAuthDisabled() {
            result = new NacosAuthProbe.Result(NacosAuthProbe.State.DISABLED, "不带令牌即可读取配置");

            SelfCheckFailedException e = assertThrows(SelfCheckFailedException.class, () -> probing.apply(environment, true));

            assertEquals(List.of(SelfChecks.P6), e.getProblems().stream().map(SelfCheckProblem::id).toList());
        }

        @Test
        @DisplayName("无法判断时只警告，记为不适用并说明原因")
        void shouldNotBlockWhenUnknown() {
            result = new NacosAuthProbe.Result(NacosAuthProbe.State.UNKNOWN, "/nacos/v1/cs/configs 返回 404");

            assertDoesNotThrow(() -> probing.apply(environment, true));

            SelfCheckReport.Result p6 = resultOf("P6");
            assertEquals(SelfCheckReport.Status.NOT_APPLICABLE, p6.status());
            assertTrue(p6.title().contains("返回 404"));
        }

        @Test
        @DisplayName("跳过 P6 时不请求 Nacos")
        void shouldNotProbeWhenSkipped() {
            result = new NacosAuthProbe.Result(NacosAuthProbe.State.DISABLED, "不带令牌即可读取配置");
            config.put(SelfCheckEnvironmentPostProcessor.SKIP_KEY, "P6");

            assertDoesNotThrow(() -> probing.apply(environment, true));

            assertTrue(probed.isEmpty());
            assertEquals(SelfCheckReport.Status.SKIPPED, statusOf("P6"));
        }

        @Test
        @DisplayName("公共配置未从 Nacos 加载时不请求 Nacos")
        void shouldNotProbeWithoutNacosConfig() {
            config.remove("spring.config.import[0]");

            assertDoesNotThrow(() -> probing.apply(environment, true));

            assertTrue(probed.isEmpty());
            assertEquals(SelfCheckReport.Status.NOT_APPLICABLE, statusOf("P6"));
        }

        private SelfCheckReport.Status statusOf(String id) {
            return resultOf(id).status();
        }

        private SelfCheckReport.Result resultOf(String id) {
            return SelfCheckReport.latest().orElseThrow().results().stream()
                .filter(r -> r.id().equals(id)).findFirst().orElseThrow();
        }

    }

    @Test
    @DisplayName("P7、P8：生产模式关闭验证码或强度过低时拒绝启动，问题按编号顺序报告")
    void shouldFailOnCaptchaAndBcrypt() {
        environment.setActiveProfiles("prod");
        config.put(SelfChecks.BCRYPT_COST_KEY, "8");
        config.put(SelfChecks.CAPTCHA_ENABLED_KEY, "false");

        SelfCheckFailedException e = assertThrows(SelfCheckFailedException.class, () -> processor.apply(environment, true));

        assertEquals(List.of(SelfChecks.P2, SelfChecks.P7, SelfChecks.P8),
            e.getProblems().stream().map(SelfCheckProblem::id).toList());
    }

}
