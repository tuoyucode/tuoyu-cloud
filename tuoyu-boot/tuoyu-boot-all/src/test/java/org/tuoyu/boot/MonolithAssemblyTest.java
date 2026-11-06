package org.tuoyu.boot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单体的组装检查：不连库、不启动容器
 */
@DisplayName("单体组装")
class MonolithAssemblyTest {

    @Test
    @DisplayName("类路径中没有 Dubbo、Nacos、Seata：跨模块调用都是进程内调用，配置从本地目录读取")
    void shouldNotContainRemotingOrRegistry() {
        for (String type : List.of(
            "org.apache.dubbo.config.ServiceConfig",
            "org.tuoyu.common.dubbo.config.DubboConfiguration",
            "com.alibaba.cloud.nacos.NacosConfigProperties",
            "com.alibaba.cloud.nacos.discovery.NacosDiscoveryClient",
            "org.apache.seata.spring.annotation.GlobalTransactional")) {
            assertFalse(ClassUtils.isPresent(type, getClass().getClassLoader()), "单体的类路径中不应有 " + type
                + "，请在 tuoyu-boot-all 的 pom 中排除（见其中的说明）");
        }
        // 读取 .env 的处理器在 tuoyu-common-nacos 中，单体同样需要
        assertTrue(ClassUtils.isPresent("org.tuoyu.common.nacos.env.DotEnvEnvironmentPostProcessor", getClass().getClassLoader()));
    }

    @Test
    @DisplayName("每个跨模块接口（@RemoteApi）在单体扫描的包中有且只有一个实现，否则单体拒绝启动")
    void everyRemoteApiShouldHaveOneLocalImplementation() throws ClassNotFoundException {
        String[] modules = TuoYuBootAllApplication.class.getAnnotation(ComponentScan.class).basePackages();

        ClassPathScanningCandidateComponentProvider apiScanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        apiScanner.addIncludeFilter(new AnnotationTypeFilter(RemoteApi.class));
        Set<String> apis = new TreeSet<>();
        for (BeanDefinition definition : apiScanner.findCandidateComponents("org.tuoyu")) {
            apis.add(definition.getBeanClassName());
        }
        assertFalse(apis.isEmpty(), "没有找到 @RemoteApi 接口");

        List<String> problems = new ArrayList<>();
        for (String api : apis) {
            ClassPathScanningCandidateComponentProvider implScanner = new ClassPathScanningCandidateComponentProvider(false);
            implScanner.addIncludeFilter(new AssignableTypeFilter(Class.forName(api)));
            Set<String> implementations = new TreeSet<>();
            for (String module : modules) {
                implScanner.findCandidateComponents(module).stream()
                    .filter(d -> d instanceof AnnotatedBeanDefinition annotated
                        && annotated.getMetadata().isAnnotated(Component.class.getName()))
                    .forEach(d -> implementations.add(d.getBeanClassName()));
            }
            if (implementations.size() != 1) {
                problems.add(api + " 的实现：" + implementations);
            }
        }
        assertTrue(problems.isEmpty(), "单体中跨模块接口的实现不唯一：\n" + String.join("\n", problems));
    }

    @Test
    @DisplayName("导入的配置文件都在 deploy/config/nacos 中，单体专用配置最后导入")
    @SuppressWarnings("unchecked")
    void importedConfigsShouldExist() throws IOException {
        Map<String, Object> yaml;
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            yaml = new Yaml().load(in);
        }
        Map<String, Object> config = (Map<String, Object>) ((Map<String, Object>) yaml.get("spring")).get("config");
        List<String> imports = (List<String>) config.get("import");
        String prefix = "file:${TUOYU_CONFIG_DIR:deploy/config/nacos}/";
        List<String> files = new ArrayList<>();
        for (String location : imports) {
            assertTrue(location.startsWith(prefix), "单体的配置应从 TUOYU_CONFIG_DIR 读取：" + location);
            String file = location.substring(prefix.length());
            assertTrue(Files.isRegularFile(MonolithTestSupport.configDir().resolve(file)), "配置文件不存在：" + file);
            files.add(file);
        }
        assertEquals("application-common.yml", files.getFirst());
        assertEquals("tuoyu-boot-all.yml", files.getLast());
        for (String module : List.of("auth", "system", "resource", "workflow", "gen", "job")) {
            assertTrue(files.contains("tuoyu-" + module + ".yml"), "单体应导入 tuoyu-" + module + ".yml");
        }
    }

}
