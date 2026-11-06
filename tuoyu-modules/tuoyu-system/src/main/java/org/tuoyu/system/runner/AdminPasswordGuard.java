package org.tuoyu.system.runner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.selfcheck.SelfCheckEnvironmentPostProcessor;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.mapper.SysUserMapper;

/**
 * 管理员初始口令的启动检查（默认安全）。
 * <p>
 * 种子数据（V1 基线与各数据库的全新安装脚本，已冻结）中管理员 admin 的口令是公开的。启动时如果它仍是种子口令：
 * <ul>
 *     <li>配置了 {@value #PROPERTY}（TUOYU_ADMIN_INITIAL_PASSWORD）：改为配置的口令；</li>
 *     <li>没有配置：生产模式（prod 或 tuoyu.selfcheck.strict=true）拒绝启动，开发模式只给警告。</li>
 * </ul>
 * 口令已经改过时不做任何改动，配置的口令也不会覆盖管理员自己改过的口令。单体与微服务中都由负责 tuoyu-cloud 库的系统模块执行。
 *
 * @author tuoyu
 */
@Slf4j
@Component
public class AdminPasswordGuard implements ApplicationRunner {

    public static final String PROPERTY = "tuoyu.system.admin-initial-password";

    /**
     * 种子数据中 admin 的口令哈希（各数据库的全新安装脚本与 V1 基线相同）
     */
    static final String SEED_HASH = "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    /**
     * 口令的最小长度
     */
    static final int MIN_LENGTH = 8;

    private final SysUserMapper userMapper;

    private final PasswordHasher passwordHasher;

    private final ConfigurableEnvironment environment;

    public AdminPasswordGuard(SysUserMapper userMapper, PasswordHasher passwordHasher, ConfigurableEnvironment environment) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        check(environment.getProperty(PROPERTY, ""), SelfCheckEnvironmentPostProcessor.isProduction(environment));
    }

    /**
     * @param configured 配置的初始口令
     * @param production 是否为生产模式
     */
    void check(String configured, boolean production) {
        // admin 属于默认租户；开启多租户时启动线程中没有租户，按 ID 直接读写
        SysUser admin = TenantHelper.ignore(() -> userMapper.selectById(SystemConstants.SUPER_ADMIN_USER_ID));
        if (admin == null || !SEED_HASH.equals(admin.getPassword())) {
            return;
        }
        if (StringUtils.isNotBlank(configured)) {
            if (configured.length() < MIN_LENGTH) {
                throw new IllegalStateException(PROPERTY + "（TUOYU_ADMIN_INITIAL_PASSWORD）过于简单，至少 " + MIN_LENGTH + " 位");
            }
            SysUser update = new SysUser();
            update.setUserId(admin.getUserId());
            update.setPassword(passwordHasher.hash(configured));
            // 启动线程中没有登录用户：updateById 带有数据权限，需要跳过，否则因缺少当前用户而失败
            TenantHelper.ignore(() -> DataPermissionHelper.ignore(() -> userMapper.updateById(update)));
            log.info("管理员 admin 的初始口令已按 TUOYU_ADMIN_INITIAL_PASSWORD 设置");
            return;
        }
        String message = "管理员 admin 仍使用种子数据中公开的口令，请设置 TUOYU_ADMIN_INITIAL_PASSWORD"
            + "（java tools/secrets/GenerateEnv.java 生成的 .env 中已包含）后重启，或登录后修改口令";
        if (production) {
            throw new IllegalStateException(message);
        }
        log.warn("{}（开发模式只提示，生产模式会拒绝启动）", message);
    }

}
