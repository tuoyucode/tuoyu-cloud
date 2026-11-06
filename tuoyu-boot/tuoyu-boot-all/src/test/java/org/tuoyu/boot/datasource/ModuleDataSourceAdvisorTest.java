package org.tuoyu.boot.datasource;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.tuoyu.boot.MonolithTestSupport;
import org.tuoyu.boot.datasource.fixture.system.SystemModule;
import org.tuoyu.boot.datasource.fixture.workflow.WorkflowModule;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("单体中按模块切换数据源")
class ModuleDataSourceAdvisorTest {

    private static final String FIXTURE = "org.tuoyu.boot.datasource.fixture";

    private static final String SELF = ModuleDataSourceAdvisorTest.class.getPackageName();

    @AfterEach
    void clear() {
        DynamicDataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("按包名从长到短匹配，不属于任何包时不切换")
    void shouldRouteByLongestPackage() {
        ModuleDataSourceAdvisor advisor = new ModuleDataSourceAdvisor(Map.of(
            "org.tuoyu.system", "master", "org.tuoyu.system.special", "other", "org.tuoyu.workflow", "workflow"));

        assertEquals("master", advisor.route("org.tuoyu.system.service.SysUserServiceImpl"));
        assertEquals("other", advisor.route("org.tuoyu.system.special.Foo"));
        assertEquals("workflow", advisor.route("org.tuoyu.workflow.service.impl.FlwTaskServiceImpl"));
        assertNull(advisor.route("org.tuoyu.systemx.Foo"), "包名前缀要按整段匹配");
        assertNull(advisor.route("org.tuoyu.common.mybatis.service.SysDataScopeService"));
    }

    @Test
    @DisplayName("调用期间使用模块的数据源，结束后恢复；进入另一个模块的组件时切换到它的库")
    void shouldSwitchAndRestore() {
        ModuleDataSourceAdvisor advisor = new ModuleDataSourceAdvisor(Map.of(
            FIXTURE + ".workflow", "workflow", FIXTURE + ".system", "master"));
        SystemModule system = proxy(advisor, new SystemModule());
        WorkflowModule workflow = proxy(advisor, new WorkflowModule(system));

        List<String> seen = workflow.run();

        assertEquals(List.of("workflow", "master", "workflow"), seen);
        assertNull(DynamicDataSourceContextHolder.peek(), "调用结束后应恢复");
    }

    @Test
    @DisplayName("异常时同样恢复数据源")
    void shouldRestoreOnException() {
        ModuleDataSourceAdvisor advisor = new ModuleDataSourceAdvisor(Map.of(FIXTURE + ".system", "master"));
        SystemModule system = proxy(advisor, new SystemModule());

        assertThrows(IllegalStateException.class, system::fail);
        assertNull(DynamicDataSourceContextHolder.peek());
    }

    @Test
    @DisplayName("只代理登记包中的组件：配置类、final 类、普通类不代理")
    void shouldOnlyProxyComponents() {
        ModuleDataSourceAdvisor advisor = new ModuleDataSourceAdvisor(Map.of(SELF, "master"));

        assertTrue(advisor.isCandidate(SystemModule.class));
        assertFalse(advisor.isCandidate(Config.class));
        assertFalse(advisor.isCandidate(FinalService.class));
        assertFalse(advisor.isCandidate(Plain.class));
        assertFalse(new ModuleDataSourceAdvisor(Map.of("org.other", "master")).isCandidate(SystemModule.class));
    }

    @Test
    @DisplayName("仓库中的单体配置：工作流与 Warm-Flow 使用 workflow 库，其他访问数据库的模块使用主库；同一个包不能指向两个库")
    void monolithConfigShouldRouteModules() throws IOException {
        List<ModuleDataSourceConfiguration.Route> routes = Binder.get(MonolithTestSupport.load("tuoyu-boot-all.yml"))
            .bind(ModuleDataSourceConfiguration.ROUTES, Bindable.listOf(ModuleDataSourceConfiguration.Route.class))
            .orElseThrow(() -> new AssertionError("tuoyu-boot-all.yml 中没有数据源路由"));
        ModuleDataSourceAdvisor advisor = new ModuleDataSourceAdvisor(ModuleDataSourceConfiguration.toMap(routes));

        assertEquals("workflow", advisor.route("org.tuoyu.workflow.service.impl.FlwTaskServiceImpl"));
        assertEquals("workflow", advisor.route("org.dromara.warm.flow.ui.controller.WarmFlowUiController"));
        for (String module : List.of("auth", "system", "resource", "gen")) {
            assertEquals("master", advisor.route("org.tuoyu." + module + ".service.Foo"), module);
        }

        ModuleDataSourceConfiguration.Route a = route("master", "org.tuoyu.system");
        ModuleDataSourceConfiguration.Route b = route("workflow", "org.tuoyu.system");
        assertThrows(IllegalStateException.class, () -> ModuleDataSourceConfiguration.toMap(List.of(a, b)));
        assertThrows(IllegalStateException.class, () -> ModuleDataSourceConfiguration.toMap(List.of(route(" ", "org.tuoyu.x"))));
    }

    private static ModuleDataSourceConfiguration.Route route(String datasource, String pkg) {
        ModuleDataSourceConfiguration.Route route = new ModuleDataSourceConfiguration.Route();
        route.setDatasource(datasource);
        route.setPackages(List.of(pkg));
        return route;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(ModuleDataSourceAdvisor advisor, T target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(advisor);
        return (T) factory.getProxy();
    }

    @Configuration
    static class Config {
    }

    @Service
    static final class FinalService {
    }

    static class Plain {
    }

}
