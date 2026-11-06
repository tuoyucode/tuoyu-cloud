package org.tuoyu.boot.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 单体的 URL 前缀表：Controller 所在的包 → 前缀，与网关路由的路径前缀一一对应，
 * 前端在两种部署方式下访问的 URL 完全相同（ADR 0005 第 5 节第 2 条）。
 * <p>
 * 按顺序匹配，先写的优先，所以更具体的包要写在前面（例如 system 的 monitor 包写在 system 之前）。
 * 网关中没有去掉前缀的路由（例如 Warm-Flow 设计器的 /warm-flow/**）不需要登记。
 */
@ConfigurationProperties(prefix = "tuoyu.monolith")
public class ModulePathPrefixProperties {

    private List<PathPrefix> pathPrefixes = new ArrayList<>();

    public List<PathPrefix> getPathPrefixes() {
        return pathPrefixes;
    }

    public void setPathPrefixes(List<PathPrefix> pathPrefixes) {
        this.pathPrefixes = pathPrefixes;
    }

    /**
     * 一条前缀规则
     */
    public static class PathPrefix {

        /**
         * URL 前缀，以 / 开头、不以 / 结尾，例如 /system
         */
        private String prefix;

        /**
         * Controller 所在的包（含子包）
         */
        private List<String> packages = new ArrayList<>();

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

    }

}
