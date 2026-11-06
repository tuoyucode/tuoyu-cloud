package org.tuoyu.common.encrypt.password;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 密码哈希配置
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "tuoyu.auth.password")
public class PasswordHashProperties {

    /**
     * BCrypt 强度，取值 4 ~ 31，生产环境至少 10（启动自检 P8）。每加 1 计算耗时翻倍，10 约为 60ms
     */
    private int bcryptCost = PasswordHasher.DEFAULT_COST;

}
