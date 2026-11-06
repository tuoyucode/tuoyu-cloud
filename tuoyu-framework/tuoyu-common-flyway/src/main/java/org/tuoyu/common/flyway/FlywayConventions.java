package org.tuoyu.common.flyway;

import org.flywaydb.core.api.configuration.FluentConfiguration;

import java.util.regex.Pattern;

/**
 * 数据库迁移约定（见 docs/adr/0004-数据库迁移.md）。
 * <p>
 * 这些约定由框架固定，在 spring.flyway.* 配置之后应用，配置无法改变它们：
 * <ul>
 *     <li>每个库的迁移脚本放在负责该库的服务的 {@code db/migration/<库>/{vendor}} 下（{@code <库>} 由
 *     {@value #DATABASE_PROPERTY} 指定，例如 {@code cloud}；{@code {vendor}} 由 Flyway 按数据库替换为
 *     {@code mysql} 或 {@code postgresql}），命名为 {@code V<版本>__<说明>.sql}。按库分目录是为了单体：
 *     几个库的脚本在同一个类路径下，不能互相混淆；</li>
 *     <li>V1 是基线，与 deploy/sql 下同一数据库的全新安装脚本一致。已有数据库（没有迁移记录表但已有表）
 *     按基线版本 {@value #BASELINE_VERSION} 接入，之后只执行 V2 起的增量脚本；</li>
 *     <li>禁止 clean（清空整个库），防止误操作删除生产数据；</li>
 *     <li>不做占位符替换：种子数据中有 {@code ${initiator}} 这类业务表达式，必须原样写入；</li>
 *     <li>不允许乱序执行，迁移脚本命名不规范时启动失败。</li>
 * </ul>
 */
public final class FlywayConventions {

    /**
     * 本服务负责迁移的库，决定迁移脚本目录（写在负责服务自己的 application.yml 中）
     */
    public static final String DATABASE_PROPERTY = "tuoyu.flyway.database";

    /**
     * 迁移脚本目录的公共前缀
     */
    public static final String ROOT = "db/migration";

    /**
     * 已有数据库接入时的基线版本，对应 V1 基线脚本
     */
    public static final String BASELINE_VERSION = "1";

    /**
     * 基线记录的说明，写入 flyway_schema_history
     */
    public static final String BASELINE_DESCRIPTION = "tuoyu baseline (existing database)";

    /**
     * 库名只能是小写字母开头的小写字母、数字与短横线，避免拼出意外的路径
     */
    private static final Pattern DATABASE_NAME = Pattern.compile("[a-z][a-z0-9-]*");

    private FlywayConventions() {
    }

    /**
     * 某个库的迁移脚本目录。
     *
     * @param database 库名，例如 cloud、workflow、job
     * @return 例如 {@code classpath:db/migration/cloud/{vendor}}
     */
    public static String location(String database) {
        if (database == null || !DATABASE_NAME.matcher(database).matches()) {
            throw new IllegalStateException(DATABASE_PROPERTY + " 应为负责迁移的库名（小写字母、数字与短横线，例如 cloud），当前为 "
                + database + "。迁移脚本放在 " + ROOT + "/<库名>/mysql 与 " + ROOT + "/<库名>/postgresql 下");
        }
        return "classpath:" + ROOT + "/" + database + "/{vendor}";
    }

    /**
     * 把框架约定应用到 Flyway 配置上。
     *
     * @param configuration Flyway 配置
     * @param database      负责迁移的库名，决定迁移脚本目录
     */
    public static void apply(FluentConfiguration configuration, String database) {
        configuration
            .locations(location(database))
            .baselineOnMigrate(true)
            .baselineVersion(BASELINE_VERSION)
            .baselineDescription(BASELINE_DESCRIPTION)
            .cleanDisabled(true)
            .placeholderReplacement(false)
            .outOfOrder(false)
            .validateOnMigrate(true)
            .validateMigrationNaming(true);
    }

}
