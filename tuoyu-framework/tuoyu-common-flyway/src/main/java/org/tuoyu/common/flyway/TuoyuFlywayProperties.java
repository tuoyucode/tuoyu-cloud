package org.tuoyu.common.flyway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据库迁移配置。
 * <pre>
 * tuoyu:
 *   flyway:
 *     database: cloud          # 本服务负责迁移的库，连接为 spring.flyway.url 等
 *     additional:              # 只用于单体：同一进程中还要迁移的其他库，按顺序在主库之后执行
 *       workflow:
 *         url: ...
 *         user: ...
 *         password: ...
 * </pre>
 */
@ConfigurationProperties(prefix = "tuoyu.flyway")
public class TuoyuFlywayProperties {

    /**
     * 本服务负责迁移的库，决定迁移脚本目录 db/migration/&lt;库名&gt;/{vendor}
     */
    private String database;

    /**
     * 同一进程中还要迁移的其他库：库名 → 连接。各库有自己的迁移记录表，互不影响
     */
    private Map<String, Connection> additional = new LinkedHashMap<>();

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public Map<String, Connection> getAdditional() {
        return additional;
    }

    public void setAdditional(Map<String, Connection> additional) {
        this.additional = additional;
    }

    /**
     * 迁移用的数据库连接
     */
    public static class Connection {

        private String url;

        private String user;

        private String password;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

    }

}
