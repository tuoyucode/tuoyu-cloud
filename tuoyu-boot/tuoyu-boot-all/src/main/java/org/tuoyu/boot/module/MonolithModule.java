package org.tuoyu.boot.module;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务模块在单体中的接入方式，由模块自己在 {@code META-INF/tuoyu/monolith-module.yml} 中声明：
 * <pre>
 * tuoyu:
 *   monolith:
 *     modules:
 *       demo:
 *         prefix: /demo                # URL 前缀，与网关路由相同
 *         packages: [ org.tuoyu.demo ] # 模块的包：扫描组件、加 URL 前缀、切换数据源
 *         datasource: demo             # 使用的数据源（spring.datasource.dynamic.datasource 中的名称）
 * </pre>
 * 单体（tuoyu-boot-all）只要把模块加入依赖，就会按声明扫描它的组件、挂上 URL 前缀并切换到它的库，
 * 不需要改单体的配置与启动类。微服务方式不读取这个文件。
 *
 * @author tuoyu
 */
public class MonolithModule {

    /**
     * URL 前缀，以 / 开头的一级路径，例如 /demo
     */
    private String prefix;

    /**
     * 模块的包（含子包）
     */
    private List<String> packages = new ArrayList<>();

    /**
     * 模块使用的数据源，为空时沿用调用方的数据源
     */
    private String datasource;

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public List<String> getPackages() {
        return packages;
    }

    public void setPackages(List<String> packages) {
        this.packages = packages;
    }

    public String getDatasource() {
        return datasource;
    }

    public void setDatasource(String datasource) {
        this.datasource = datasource;
    }

}
