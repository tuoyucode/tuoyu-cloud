package org.tuoyu.e2e.support;

/**
 * 端到端测试的参数，来自系统属性（failsafe 从 pom 与 -D 传入）和环境变量。
 * <ul>
 *     <li>{@code tuoyu.e2e.base-url}：被测地址，单体与网关的默认端口都是 8081；</li>
 *     <li>{@code tuoyu.e2e.username}：管理员账号，默认 admin；</li>
 *     <li>管理员口令：环境变量 {@code TUOYU_E2E_PASSWORD}（或系统属性 {@code tuoyu.e2e.password}），必填，不写进仓库；</li>
 *     <li>{@code tuoyu.e2e.client-id}：登录用的客户端，默认种子数据中的 pc 客户端；</li>
 *     <li>{@code tuoyu.e2e.tenant-id}：开启多租户时登录的租户，未开启时留空。</li>
 * </ul>
 *
 * @author tuoyu
 */
public record E2eConfig(String baseUrl, String username, String password, String clientId, String tenantId) {

    public static final String PASSWORD_ENV = "TUOYU_E2E_PASSWORD";

    public static E2eConfig load() {
        String baseUrl = property("tuoyu.e2e.base-url", "http://localhost:8081").replaceAll("/+$", "");
        String password = firstNonBlank(System.getProperty("tuoyu.e2e.password"), System.getenv(PASSWORD_ENV));
        if (password == null) {
            // 缺少口令时失败，不跳过：端到端测试一旦启用就必须真正执行
            throw new IllegalStateException("缺少管理员口令：请设置环境变量 " + PASSWORD_ENV + "（或系统属性 tuoyu.e2e.password）");
        }
        return new E2eConfig(baseUrl,
            property("tuoyu.e2e.username", "admin"),
            password,
            property("tuoyu.e2e.client-id", "e5cd7e4891bf95d1d19206ce24a7b32e"),
            firstNonBlank(System.getProperty("tuoyu.e2e.tenant-id"), null));
    }

    @Override
    public String toString() {
        // 不输出口令
        return "E2eConfig[baseUrl=" + baseUrl + ", username=" + username + ", clientId=" + clientId
            + ", tenantId=" + tenantId + "]";
    }

    private static String property(String name, String defaultValue) {
        String value = firstNonBlank(System.getProperty(name), null);
        return value == null ? defaultValue : value;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
