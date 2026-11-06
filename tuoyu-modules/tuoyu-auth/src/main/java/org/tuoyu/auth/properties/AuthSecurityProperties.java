package org.tuoyu.auth.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 认证加固配置，设计见 docs/adr/0002-认证加固与可信代理.md。
 * <p>
 * 由 Nacos 刷新时自动重新绑定，修改后无需重启。
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "tuoyu.auth")
public class AuthSecurityProperties {

    /**
     * 登录失败锁定
     */
    private LoginLimit loginLimit = new LoginLimit();

    /**
     * 短信、邮箱验证码
     */
    private VerifyCode verifyCode = new VerifyCode();

    /**
     * 客户端校验
     */
    private Client client = new Client();

    /**
     * 登录失败锁定：按账号与来源 IP 两个维度分别计数
     */
    @Data
    public static class LoginLimit {

        /**
         * 同一账号：10 分钟内失败 5 次，锁定 10 分钟
         */
        private Rule account = new Rule(5, Duration.ofMinutes(10), Duration.ofMinutes(10));

        /**
         * 同一来源 IP（IPv6 按 /64 统计）：10 分钟内失败 20 次，锁定 10 分钟
         */
        private Rule ip = new Rule(20, Duration.ofMinutes(10), Duration.ofMinutes(10));

    }

    /**
     * 一个维度的锁定规则
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rule {

        /**
         * 统计窗口内允许的失败次数，达到即锁定；0 表示不按此维度限制（不推荐）
         */
        private int maxFailures;

        /**
         * 统计窗口：从第一次失败开始计算
         */
        private Duration window;

        /**
         * 锁定时长：从达到次数时开始计算
         */
        private Duration lockDuration;

        public boolean enabled() {
            return maxFailures > 0;
        }

    }

    /**
     * 短信、邮箱验证码配置
     */
    @Data
    public static class VerifyCode {

        /**
         * 同一个验证码最多可尝试的次数，超过后作废，需要重新发送
         */
        private int maxAttempts = 3;

    }

    /**
     * 客户端配置
     */
    @Data
    public static class Client {

        /**
         * 登录时是否校验 client_secret。默认关闭：浏览器端（SPA）客户端无法保管密钥，任何人都能从前端代码中取得，
         * 校验只是形式；仅由服务端发起登录的客户端（例如可信的后端系统）可以开启
         */
        private boolean requireSecret = false;

    }

}
