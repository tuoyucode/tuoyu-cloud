package org.tuoyu.common.core.utils;

import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.GlobalConstants;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.web.ip.IpLiteral;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Locale;

/**
 * 登录失败计数的 Redis 键名。认证服务计数、系统服务解锁都按这里的规则生成键名，保证一致。
 *
 * @author tuoyu
 */
public final class LoginFailureKeys {

    private LoginFailureKeys() {
    }

    /**
     * 按账号统计的键名。账号去掉首尾空白并转为小写，避免用大小写变体绕过次数限制。
     * 开启多租户时加上当前租户：用户名在租户内唯一，同名用户在不同租户中分别计数（ADR 0006）。
     *
     * @param account 登录账号（用户名、手机号或邮箱）
     * @return 键名
     */
    public static String account(String account) {
        return CacheNames.PWD_ERR_CNT_KEY + tenantPrefix() + normalizeAccount(account);
    }

    /**
     * @return 开启多租户且有当前租户时为 {@code 租户:}，否则为空
     */
    private static String tenantPrefix() {
        try {
            TenantContext context = SpringUtils.getBeanFactory().getBeanProvider(TenantContext.class).getIfAvailable();
            String tenantId = context == null || !context.enabled() ? null : context.currentTenantId();
            return tenantId == null ? "" : tenantId + ":";
        } catch (RuntimeException e) {
            // 不在 Spring 容器中（例如单元测试）
            return "";
        }
    }

    /**
     * 按来源 IP 统计的键名。IPv6 按 /64 网段统计：一个用户通常分到整个 /64，逐个地址统计形同虚设。
     *
     * @param ip 客户端 IP
     * @return 键名；IP 为空时返回 null，表示不按 IP 统计
     */
    public static String ip(String ip) {
        String bucket = ipBucket(ip);
        return bucket == null ? null : GlobalConstants.LOGIN_FAIL_IP_KEY + bucket;
    }

    /**
     * @param account 登录账号
     * @return 规范化后的账号
     */
    public static String normalizeAccount(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }

    static String ipBucket(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        InetAddress address = IpLiteral.parse(ip);
        if (address == null) {
            return ip.trim();
        }
        if (address instanceof Inet6Address) {
            byte[] prefix = Arrays.copyOf(address.getAddress(), 16);
            Arrays.fill(prefix, 8, 16, (byte) 0);
            return IpLiteral.formatIpv6(prefix) + "/64";
        }
        return IpLiteral.format(address);
    }

}
