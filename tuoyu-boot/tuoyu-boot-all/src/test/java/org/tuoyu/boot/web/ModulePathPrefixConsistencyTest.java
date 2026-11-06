package org.tuoyu.boot.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.tuoyu.boot.MonolithTestSupport;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单体的 URL 前缀表与网关路由一致：前端在两种部署方式下访问的 URL 完全相同
 */
@DisplayName("单体 URL 前缀与网关路由一致")
class ModulePathPrefixConsistencyTest {

    /**
     * 不在单体中的服务（可选扩展模块）
     */
    private static final Set<String> NOT_IN_MONOLITH = Set.of("tuoyu-ai");

    /**
     * 由某个服务提供、但代码在框架模块中的接口（服务 → 包）
     */
    private static final Map<String, Set<String>> FRAMEWORK_PACKAGES = Map.of(
        "tuoyu-resource", Set.of("org.tuoyu.common.push"));

    /**
     * 网关路由中的服务名与业务模块的根包不同的情况
     */
    private static final Map<String, String> MODULE_PACKAGES = Map.of("tuoyu-gen", "org.tuoyu.gen");

    @Test
    @DisplayName("网关中每个去掉前缀的路由，在单体中都有相同的前缀，且指向同一个模块的包")
    void prefixesShouldMatchGatewayRoutes() throws IOException {
        Map<String, String> gateway = gatewayPrefixes();
        Map<String, List<String>> monolith = new TreeMap<>();
        for (ModulePathPrefixProperties.PathPrefix rule : monolithPrefixes()) {
            monolith.put(rule.getPrefix(), rule.getPackages());
        }

        assertEquals(gateway.keySet(), monolith.keySet(), "单体的前缀表应与网关路由一一对应");
        gateway.forEach((prefix, service) -> {
            String modulePackage = MODULE_PACKAGES.getOrDefault(service, "org.tuoyu." + service.substring("tuoyu-".length()));
            for (String pkg : monolith.get(prefix)) {
                boolean ownModule = pkg.equals(modulePackage) || pkg.startsWith(modulePackage + ".");
                boolean framework = FRAMEWORK_PACKAGES.getOrDefault(service, Set.of()).contains(pkg);
                assertTrue(ownModule || framework, prefix + " 在网关中路由到 " + service + "，单体中却指向包 " + pkg);
            }
        });
    }

    @Test
    @DisplayName("更具体的包写在前面：/monitor（system 的 monitor 包）先于 /system 匹配")
    void specificPackagesShouldComeFirst() throws IOException {
        List<ModulePathPrefixProperties.PathPrefix> rules = monolithPrefixes();
        for (int i = 0; i < rules.size(); i++) {
            for (int j = i + 1; j < rules.size(); j++) {
                for (String later : rules.get(j).getPackages()) {
                    for (String earlier : rules.get(i).getPackages()) {
                        assertFalse(later.startsWith(earlier + "."), rules.get(j).getPrefix() + " 的包 " + later
                            + " 被前面 " + rules.get(i).getPrefix() + " 的包 " + earlier + " 覆盖，永远不会匹配");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("前缀表为空或前缀不规范时拒绝启动")
    void shouldRejectInvalidPrefixes() {
        assertThrows(IllegalStateException.class, () -> ModulePathPrefixConfiguration.validate(List.of()));
        for (String prefix : new String[]{null, "system", "/system/", "/a/b", "/"}) {
            ModulePathPrefixProperties.PathPrefix rule = new ModulePathPrefixProperties.PathPrefix();
            rule.setPrefix(prefix);
            rule.setPackages(List.of("org.tuoyu.system"));
            assertThrows(IllegalStateException.class, () -> ModulePathPrefixConfiguration.validate(List.of(rule)), prefix);
        }
        ModulePathPrefixProperties.PathPrefix noPackage = new ModulePathPrefixProperties.PathPrefix();
        noPackage.setPrefix("/system");
        assertThrows(IllegalStateException.class, () -> ModulePathPrefixConfiguration.validate(List.of(noPackage)));
    }

    /**
     * 网关中去掉一级前缀（StripPrefix=1）的路由：前缀 → 服务名
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> gatewayPrefixes() throws IOException {
        Map<String, Object> node = MonolithTestSupport.yaml("tuoyu-gateway.yml");
        for (String key : new String[]{"spring", "cloud", "gateway", "server", "webmvc"}) {
            node = (Map<String, Object>) node.get(key);
        }
        List<Map<String, Object>> routes = (List<Map<String, Object>>) node.get("routes");
        Map<String, String> prefixes = new TreeMap<>();
        for (Map<String, Object> route : routes) {
            String service = String.valueOf(route.get("uri")).replace("lb://", "");
            List<String> filters = (List<String>) route.getOrDefault("filters", List.of());
            if (NOT_IN_MONOLITH.contains(service) || !filters.contains("StripPrefix=1")) {
                continue;
            }
            for (String predicate : (List<String>) route.get("predicates")) {
                for (String path : predicate.replace("Path=", "").split(",")) {
                    prefixes.put(path.trim().replace("/**", ""), service);
                }
            }
        }
        return prefixes;
    }

    private static List<ModulePathPrefixProperties.PathPrefix> monolithPrefixes() throws IOException {
        StandardEnvironment environment = MonolithTestSupport.load("tuoyu-boot-all.yml");
        return Binder.get(environment)
            .bind("tuoyu.monolith.path-prefixes", Bindable.listOf(ModulePathPrefixProperties.PathPrefix.class))
            .orElseThrow(() -> new AssertionError("tuoyu-boot-all.yml 中没有 URL 前缀表"));
    }

}
