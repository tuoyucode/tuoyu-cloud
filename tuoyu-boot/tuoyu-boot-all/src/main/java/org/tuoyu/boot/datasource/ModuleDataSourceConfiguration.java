package org.tuoyu.boot.datasource;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单体中按模块切换数据源的配置。
 * <pre>
 * tuoyu:
 *   monolith:
 *     datasource-routes:
 *       - datasource: workflow
 *         packages: [org.tuoyu.workflow, org.dromara.warm]
 * </pre>
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
public class ModuleDataSourceConfiguration {

    static final String ROUTES = "tuoyu.monolith.datasource-routes";

    /**
     * 切面要在其他 Bean 创建前就绪，所以直接从 Environment 读取配置（static，不依赖本配置类的实例）
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static ModuleDataSourceAdvisor moduleDataSourceAdvisor(Environment environment) {
        List<Route> routes = Binder.get(environment).bind(ROUTES, Bindable.listOf(Route.class)).orElse(List.of());
        return new ModuleDataSourceAdvisor(toMap(routes));
    }

    /**
     * 启动时检查：路由中的数据源都已配置。否则要到第一次访问该模块时才报错
     */
    @Bean
    SmartInitializingSingleton moduleDataSourceCheck(ModuleDataSourceAdvisor advisor, ObjectProvider<DataSource> dataSource) {
        return () -> {
            if (!(dataSource.getIfAvailable() instanceof DynamicRoutingDataSource routing)) {
                return;
            }
            Set<String> configured = routing.getDataSources().keySet();
            List<String> missing = advisor.dataSources().stream().filter(name -> !configured.contains(name)).toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException(ROUTES + " 中的数据源 " + missing + " 未在 spring.datasource.dynamic.datasource 中配置，"
                    + "已配置的数据源：" + configured);
            }
        };
    }

    static Map<String, String> toMap(List<Route> routes) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Route route : routes) {
            if (route.getDatasource() == null || route.getDatasource().isBlank()) {
                throw new IllegalStateException(ROUTES + " 中有未指定 datasource 的规则");
            }
            for (String pkg : route.getPackages()) {
                String previous = map.put(pkg, route.getDatasource());
                if (previous != null && !previous.equals(route.getDatasource())) {
                    throw new IllegalStateException(ROUTES + " 中包 " + pkg + " 同时指向 " + previous + " 与 " + route.getDatasource());
                }
            }
        }
        return map;
    }

    /**
     * 一条路由规则：这些包中的组件使用指定的数据源
     */
    public static class Route {

        private String datasource;

        private List<String> packages = List.of();

        public String getDatasource() {
            return datasource;
        }

        public void setDatasource(String datasource) {
            this.datasource = datasource;
        }

        public List<String> getPackages() {
            return packages;
        }

        public void setPackages(List<String> packages) {
            this.packages = packages;
        }

    }

}
