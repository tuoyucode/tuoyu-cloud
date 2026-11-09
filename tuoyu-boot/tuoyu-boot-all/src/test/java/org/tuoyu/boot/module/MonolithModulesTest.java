package org.tuoyu.boot.module;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.tuoyu.boot.web.ModulePathPrefixConfiguration;
import org.tuoyu.boot.web.ModulePathPrefixProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("业务模块的单体接入声明")
class MonolithModulesTest {

    private static final String FIXTURE = "classpath*:monolith-module-fixture/*.yml";

    private static StandardEnvironment environmentWithFixture() {
        StandardEnvironment environment = new StandardEnvironment();
        new MonolithModuleEnvironmentPostProcessor(new PathMatchingResourcePatternResolver(), FIXTURE)
            .postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    @Test
    @DisplayName("读取 jar 中的声明：前缀、包、数据源，以及按名称合并的数据源与迁移配置")
    void loadsDeclarations() {
        StandardEnvironment environment = environmentWithFixture();

        Map<String, MonolithModule> modules = MonolithModules.bind(environment);

        assertThat(modules).containsOnlyKeys("demo");
        MonolithModule demo = modules.get("demo");
        assertThat(demo.getPrefix()).isEqualTo("/demo");
        assertThat(demo.getPackages()).containsExactly("org.tuoyu.demo");
        assertThat(demo.getDatasource()).isEqualTo("demo");
        assertThat(environment.getProperty("tuoyu.flyway.additional.demo.url")).isEqualTo("jdbc:demo");
    }

    @Test
    @DisplayName("声明的优先级低于已有配置")
    void declarationsHaveLowerPrecedence() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("deploy",
            Map.of("tuoyu.flyway.additional.demo.url", "jdbc:override")));
        new MonolithModuleEnvironmentPostProcessor(new PathMatchingResourcePatternResolver(), FIXTURE)
            .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("tuoyu.flyway.additional.demo.url")).isEqualTo("jdbc:override");
    }

    @Test
    @DisplayName("前缀不规范、缺少包或包含框架的包时拒绝启动")
    void rejectsInvalidDeclarations() {
        assertThatThrownBy(() -> MonolithModules.validate(Map.of("demo", module("demo", List.of("org.tuoyu.demo")))))
            .hasMessageContaining("prefix");
        assertThatThrownBy(() -> MonolithModules.validate(Map.of("demo", module("/demo", List.of()))))
            .hasMessageContaining("packages");
        assertThatThrownBy(() -> MonolithModules.validate(Map.of("demo", module("/demo", List.of("org.tuoyu.common.core")))))
            .hasMessageContaining("框架");
        assertThatThrownBy(() -> MonolithModules.validate(Map.of("Demo", module("/demo", List.of("org.tuoyu.demo")))))
            .hasMessageContaining("模块名");
    }

    @Test
    @DisplayName("没有业务模块时为空，不影响框架自带的模块")
    void emptyWithoutDeclarations() {
        assertThat(MonolithModules.bind(new StandardEnvironment())).isEmpty();
    }

    @Test
    @DisplayName("业务模块的前缀排在框架模块之后，与已有前缀重复时拒绝启动")
    void combinesWithBuiltInPrefixes() {
        ModulePathPrefixProperties.PathPrefix system = new ModulePathPrefixProperties.PathPrefix();
        system.setPrefix("/system");
        system.setPackages(List.of("org.tuoyu.system"));
        Map<String, MonolithModule> modules = new LinkedHashMap<>();
        modules.put("demo", module("/demo", List.of("org.tuoyu.demo")));

        List<ModulePathPrefixProperties.PathPrefix> combined = ModulePathPrefixConfiguration.combine(List.of(system), modules);

        assertThat(combined).extracting(ModulePathPrefixProperties.PathPrefix::getPrefix).containsExactly("/system", "/demo");
        modules.put("other", module("/system", List.of("org.tuoyu.other")));
        assertThatThrownBy(() -> ModulePathPrefixConfiguration.validate(ModulePathPrefixConfiguration.combine(List.of(system), modules)))
            .hasMessageContaining("重复");
    }

    private static MonolithModule module(String prefix, List<String> packages) {
        MonolithModule module = new MonolithModule();
        module.setPrefix(prefix);
        module.setPackages(packages);
        return module;
    }

}
