package org.tuoyu.snailjob.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SnailJob 管理员口令的启动检查（默认安全）。
 * <p>
 * SnailJob 的初始数据中，管理员 admin 的口令是公开的 admin。启动时如果它仍是默认口令：
 * <ul>
 *     <li>配置了 {@value #PROPERTY}（TUOYU_SNAILJOB_ADMIN_PASSWORD）：改为配置的口令；</li>
 *     <li>没有配置：生产模式（prod 或 tuoyu.selfcheck.strict=true）拒绝启动，开发模式只给警告。</li>
 * </ul>
 * 口令已经改过（不是默认值）时不做任何改动，配置的口令也不会覆盖在管理界面中改过的口令。
 * SnailJob 保存口令的方式是 sha256(md5(口令))（前端先做 md5），这里按同样的方式计算。
 *
 * @author tuoyu
 */
public class AdminPasswordGuard {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordGuard.class);

    public static final String PROPERTY = "tuoyu.snailjob.admin-password";

    static final String ADMIN = "admin";

    /**
     * 默认口令 admin 的哈希，与 SnailJob 初始数据（V1 基线）中的值相同
     */
    static final String DEFAULT_HASH = hash(ADMIN);

    /**
     * 口令的最小长度
     */
    static final int MIN_LENGTH = 8;

    private final JdbcTemplate jdbc;

    private final String configured;

    private final boolean production;

    public AdminPasswordGuard(JdbcTemplate jdbc, String configured, boolean production) {
        this.jdbc = jdbc;
        this.configured = configured;
        this.production = production;
    }

    public void check() {
        Integer count = jdbc.queryForObject("select count(*) from sj_system_user where username = ? and password = ?",
            Integer.class, ADMIN, DEFAULT_HASH);
        if (count == null || count == 0) {
            return;
        }
        if (StringUtils.hasText(configured)) {
            if (configured.length() < MIN_LENGTH || ADMIN.equalsIgnoreCase(configured)) {
                throw new IllegalStateException(PROPERTY + "（TUOYU_SNAILJOB_ADMIN_PASSWORD）过于简单，至少 " + MIN_LENGTH
                    + " 位且不能是 admin");
            }
            jdbc.update("update sj_system_user set password = ? where username = ? and password = ?",
                hash(configured), ADMIN, DEFAULT_HASH);
            log.info("SnailJob 管理员 admin 的默认口令已按 TUOYU_SNAILJOB_ADMIN_PASSWORD 修改");
            return;
        }
        String message = "SnailJob 管理员 admin 仍使用公开的默认口令 admin，请设置 TUOYU_SNAILJOB_ADMIN_PASSWORD"
            + "（java tools/secrets/GenerateEnv.java 生成的 .env 中已包含），或登录管理界面修改口令";
        if (production) {
            throw new IllegalStateException(message);
        }
        log.warn("{}（开发模式只提示，生产模式会拒绝启动）", message);
    }

    /**
     * SnailJob 保存口令的方式：sha256(md5(口令))，均为小写十六进制
     */
    static String hash(String password) {
        return digest("SHA-256", digest("MD5", password));
    }

    private static String digest(String algorithm, String text) {
        try {
            byte[] bytes = MessageDigest.getInstance(algorithm).digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
