package org.tuoyu.common.selfcheck;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SelfChecks 单元测试")
class SelfChecksTest {

    private final Map<String, Object> variables = new HashMap<>();

    private final Map<String, Object> config = new LinkedHashMap<>();

    private StandardEnvironment environment;

    /**
     * 用可控的 Map 代替真实环境变量与系统属性，避免测试受本机环境影响。
     */
    @BeforeEach
    void setUp() {
        environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            new MapPropertySource(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, new HashMap<>()));
        environment.getPropertySources().addLast(new MapPropertySource("config", config));
    }

    private Map<String, Object> raw() {
        return SelfChecks.effectiveRawValues(environment);
    }

    @Nested
    @DisplayName("C1 缺少必填变量")
    class MissingVariables {

        @Test
        @DisplayName("没有默认值且未提供的变量被列出，有默认值的不列出")
        void shouldReportOnlyVariablesWithoutDefault() {
            config.put("spring.data.redis.password", "${TUOYU_REDIS_PASSWORD}");
            config.put("spring.data.redis.host", "${TUOYU_REDIS_HOST:localhost}");

            Optional<SelfCheckProblem> problem = SelfChecks.checkMissingVariables(environment, raw());

            assertTrue(problem.isPresent());
            assertEquals(List.of("TUOYU_REDIS_PASSWORD"), problem.get().details());
        }

        @Test
        @DisplayName("一个值中有多个占位符时逐个判断")
        void shouldCheckEachPlaceholderInValue() {
            config.put("datasource.url", "jdbc:mysql://${TUOYU_DB_HOST:localhost}:${TUOYU_DB_PORT}/db");

            assertEquals(List.of("TUOYU_DB_PORT"), SelfChecks.checkMissingVariables(environment, raw()).orElseThrow().details());
        }

        @Test
        @DisplayName("变量已提供时通过")
        void shouldPassWhenProvided() {
            config.put("spring.data.redis.password", "${TUOYU_REDIS_PASSWORD}");
            variables.put("TUOYU_REDIS_PASSWORD", "provided-value");

            assertTrue(SelfChecks.checkMissingVariables(environment, raw()).isEmpty());
        }

        @Test
        @DisplayName("变量值为空白时视为缺失")
        void shouldTreatBlankAsMissing() {
            config.put("spring.data.redis.password", "${TUOYU_REDIS_PASSWORD}");
            variables.put("TUOYU_REDIS_PASSWORD", "  ");

            assertTrue(SelfChecks.checkMissingVariables(environment, raw()).isPresent());
        }

        @Test
        @DisplayName("被更高优先级的配置覆盖后，不再要求低优先级配置中的变量")
        void shouldIgnoreOverriddenValues() {
            config.put("spring.data.redis.password", "${TUOYU_REDIS_PASSWORD}");
            environment.getPropertySources().addFirst(new MapPropertySource("override",
                Map.of("spring.data.redis.password", "set-directly-by-ops")));

            assertTrue(SelfChecks.checkMissingVariables(environment, raw()).isEmpty());
        }

    }

    @Nested
    @DisplayName("C2 公共配置已加载")
    class CommonConfigLoaded {

        @Test
        @DisplayName("声明引入公共配置但缺少标记时报告问题")
        void shouldReportWhenMarkerMissing() {
            config.put("spring.config.import[0]", "optional:nacos:application-common.yml");

            assertTrue(SelfChecks.checkCommonConfigLoaded(environment, raw()).isPresent());
        }

        @Test
        @DisplayName("标记存在时通过")
        void shouldPassWhenMarkerPresent() {
            config.put("spring.config.import[0]", "optional:nacos:application-common.yml");
            config.put(SelfChecks.COMMON_LOADED_MARKER, "true");

            assertTrue(SelfChecks.checkCommonConfigLoaded(environment, raw()).isEmpty());
        }

        @Test
        @DisplayName("未声明引入公共配置的应用不检查")
        void shouldSkipWhenNotDeclared() {
            assertTrue(SelfChecks.checkCommonConfigLoaded(environment, raw()).isEmpty());
        }

    }

    @Nested
    @DisplayName("P1 弱口令与已公开密钥")
    class WeakSecrets {

        @Test
        @DisplayName("识别过短、纯数字、占位值和已公开的密钥，且报告中不包含具体值")
        void shouldReportWeakValuesWithoutLeakingThem() {
            String alphabet = IntStream.rangeClosed('a', 'z')
                .mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining());
            Map<String, String> weak = Map.of(
                "spring.data.redis.password", "short",
                "datasource.master.password", "123456789012345",
                "store.db.password", "CHANGE_ME_BEFORE_USE",
                "sa-token.jwt-secret-key", alphabet);
            config.putAll(weak);

            SelfCheckProblem problem = SelfChecks.checkWeakSecrets(environment, raw()).orElseThrow();

            assertEquals(weak.keySet().stream().sorted().toList(), problem.details().stream().sorted().toList());
            String report = problem.title() + problem.details() + problem.action();
            weak.values().forEach(value -> assertFalse(report.contains(value), "报告中不应出现口令原文"));
        }

        @Test
        @DisplayName("强口令、空值、未解析的占位符、非口令类配置都不报告")
        void shouldIgnoreAcceptableValues() {
            config.put("spring.data.redis.password", "q8Rk2vNw7ZpL4sTx9GmH3cYb");
            config.put("justauth.type.gitee.client-secret", "");
            config.put("spring.cloud.nacos.password", "${TUOYU_NACOS_PASSWORD}");
            config.put("sa-token.token-name", "Authorization");
            config.put("sa-token.check-same-token", "true");

            assertTrue(SelfChecks.checkWeakSecrets(environment, raw()).isEmpty());
        }

        @Test
        @DisplayName("按配置项名最后一段识别口令类配置")
        void shouldNormalizeLastSegment() {
            assertEquals("jwtsecretkey", SelfChecks.normalizedLastSegment("sa-token.jwt-secret-key"));
            assertEquals("password", SelfChecks.normalizedLastSegment("datasource.list[0].password"));
            assertEquals("userpassword", SelfChecks.normalizedLastSegment("spring.cloud.nacos.discovery.metadata.userpassword"));
        }

    }

    @Nested
    @DisplayName("P2 ~ P5 生产环境配置")
    class ProductionSettings {

        @Test
        @DisplayName("P2：Actuator 暴露全部端点时报告，逗号形式与列表形式均识别")
        void shouldDetectActuatorExposure() {
            config.put("management.endpoints.web.exposure.include", "health,info,prometheus");
            assertTrue(SelfChecks.checkActuatorExposure(environment).isEmpty());

            config.put("management.endpoints.web.exposure.include", "*");
            assertTrue(SelfChecks.checkActuatorExposure(environment).isPresent());

            config.remove("management.endpoints.web.exposure.include");
            config.put("management.endpoints.web.exposure.include[0]", "*");
            assertTrue(SelfChecks.checkActuatorExposure(environment).isPresent());
        }

        @Test
        @DisplayName("P3：健康检查展示全部细节时报告")
        void shouldDetectHealthDetails() {
            config.put("management.endpoint.health.show-details", "never");
            assertTrue(SelfChecks.checkHealthDetails(environment).isEmpty());

            config.put("management.endpoint.health.show-details", "ALWAYS");
            assertTrue(SelfChecks.checkHealthDetails(environment).isPresent());
        }

        @Test
        @DisplayName("P4：接口文档未显式关闭时报告")
        void shouldDetectApiDocs() {
            assertTrue(SelfChecks.checkApiDocs(environment).isPresent());

            config.put("springdoc.api-docs.enabled", "false");
            assertTrue(SelfChecks.checkApiDocs(environment).isEmpty());
        }

        @Test
        @DisplayName("P5：网关开启自动路由时报告")
        void shouldDetectGatewayLocator() {
            assertTrue(SelfChecks.checkGatewayDiscoveryLocator(environment).isEmpty());

            config.put("spring.cloud.gateway.server.webmvc.discovery.locator.enabled", "${TUOYU_GATEWAY_DISCOVERY_LOCATOR:false}");
            assertTrue(SelfChecks.checkGatewayDiscoveryLocator(environment).isEmpty());

            variables.put("TUOYU_GATEWAY_DISCOVERY_LOCATOR", "true");
            assertTrue(SelfChecks.checkGatewayDiscoveryLocator(environment).isPresent());
        }

    }

    @Nested
    @DisplayName("P6 ~ P8")
    class AuthSettings {

        @Test
        @DisplayName("P6：由 server-addr 推出 Nacos 地址（取第一个，补协议、端口与上下文路径）")
        void shouldBuildNacosBaseUrl() {
            assertEquals("http://127.0.0.1:8848/nacos", SelfChecks.nacosBaseUrl("127.0.0.1:8848"));
            assertEquals("http://nacos-1:8848/nacos", SelfChecks.nacosBaseUrl(" nacos-1 , nacos-2:8848"));
            assertEquals("https://nacos.example.com:443/nacos", SelfChecks.nacosBaseUrl("https://nacos.example.com:443"));
            assertEquals("http://10.0.0.5:8848/cfg", SelfChecks.nacosBaseUrl("http://10.0.0.5:8848/cfg/"));
        }

        @Test
        @DisplayName("P6：只有公共配置确实从 Nacos 加载时才探测，分组默认 DEFAULT_GROUP")
        void shouldProbeOnlyWhenLoadedFromNacos() {
            config.put("spring.cloud.nacos.server-addr", "127.0.0.1:8848");
            assertTrue(SelfChecks.nacosProbeTarget(environment, raw()).isEmpty());

            config.put("spring.config.import[0]", "optional:nacos:application-common.yml");
            assertTrue(SelfChecks.nacosProbeTarget(environment, raw()).isEmpty(), "公共配置未加载成功时由 C2 报告，不再探测");

            config.put(SelfChecks.COMMON_LOADED_MARKER, "true");
            config.put("spring.cloud.nacos.config.server-addr", "10.0.0.5:8848");
            assertEquals(Optional.of(new NacosAuthProbe.Target("http://10.0.0.5:8848/nacos", "", "DEFAULT_GROUP",
                "application-common.yml")), SelfChecks.nacosProbeTarget(environment, raw()));
        }

        @Test
        @DisplayName("P6：只有匿名请求读取成功时报告问题")
        void shouldReportOnlyWhenAuthDisabled() {
            assertTrue(SelfChecks.checkNacosAuth(new NacosAuthProbe.Result(NacosAuthProbe.State.ENABLED, "")).isEmpty());
            assertTrue(SelfChecks.checkNacosAuth(new NacosAuthProbe.Result(NacosAuthProbe.State.UNKNOWN, "")).isEmpty());
            assertTrue(SelfChecks.checkNacosAuth(new NacosAuthProbe.Result(NacosAuthProbe.State.DISABLED, "")).isPresent());
        }

        @Test
        @DisplayName("P7：只有显式关闭登录验证码时报告")
        void shouldDetectCaptchaDisabled() {
            assertTrue(SelfChecks.checkCaptchaEnabled(environment).isEmpty());

            config.put(SelfChecks.CAPTCHA_ENABLED_KEY, "true");
            assertTrue(SelfChecks.checkCaptchaEnabled(environment).isEmpty());

            config.put(SelfChecks.CAPTCHA_ENABLED_KEY, "FALSE");
            assertTrue(SelfChecks.checkCaptchaEnabled(environment).isPresent());
        }

        @Test
        @DisplayName("P8：强度低于 10 或不是整数时报告，未配置时按默认值 10 通过")
        void shouldDetectWeakBcryptCost() {
            assertTrue(SelfChecks.checkBcryptCost(environment).isEmpty());

            for (String acceptable : List.of("10", " 12 ")) {
                config.put(SelfChecks.BCRYPT_COST_KEY, acceptable);
                assertTrue(SelfChecks.checkBcryptCost(environment).isEmpty(), acceptable);
            }
            for (String weak : List.of("9", "4", "ten")) {
                config.put(SelfChecks.BCRYPT_COST_KEY, weak);
                assertTrue(SelfChecks.checkBcryptCost(environment).isPresent(), weak);
            }
        }

        @Test
        @DisplayName("P9：只有开启完整 SQL 日志时报告，未配置时通过")
        void shouldDetectSqlLogEnabled() {
            assertTrue(SelfChecks.checkSqlLog(environment).isEmpty());

            config.put(SelfChecks.SQL_LOG_ENABLED_KEY, "false");
            assertTrue(SelfChecks.checkSqlLog(environment).isEmpty());

            config.put(SelfChecks.SQL_LOG_ENABLED_KEY, "TRUE");
            Optional<SelfCheckProblem> problem = SelfChecks.checkSqlLog(environment);
            assertTrue(problem.isPresent());
            assertEquals(SelfChecks.P9, problem.get().id());
        }

    }

}
