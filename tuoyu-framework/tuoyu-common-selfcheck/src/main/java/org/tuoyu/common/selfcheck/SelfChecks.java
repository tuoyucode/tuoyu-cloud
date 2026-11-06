package org.tuoyu.common.selfcheck;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 启动自检的各项检查。全部为无状态的纯函数，便于单元测试，也供 CI 校验仓库内的配置文件。
 * <ul>
 *     <li>C1、C2：任何环境都会执行，未通过即拒绝启动；</li>
 *     <li>P1 ~ P9：生产环境检查，生产环境未通过即拒绝启动，开发环境只输出警告。</li>
 * </ul>
 * 除 P6 需要请求一次 Nacos 外，其余检查只读取配置。
 *
 * @author tuoyu
 */
public final class SelfChecks {

    public static final String C1 = "C1";
    public static final String C2 = "C2";
    public static final String P1 = "P1";
    public static final String P2 = "P2";
    public static final String P3 = "P3";
    public static final String P4 = "P4";
    public static final String P5 = "P5";
    public static final String P6 = "P6";
    public static final String P7 = "P7";
    public static final String P8 = "P8";
    public static final String P9 = "P9";

    /**
     * 全部检查项及其通过时的说明，按展示顺序排列
     */
    static final Map<String, String> PASSED_TITLES = passedTitles();

    /**
     * 任何环境都执行的检查
     */
    static final Set<String> RUNTIME_IDS = Set.of(C1, C2);

    private static Map<String, String> passedTitles() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put(C1, "必填的环境变量都已提供");
        titles.put(C2, "已从 Nacos 加载公共配置 " + COMMON_CONFIG);
        titles.put(P1, "口令和密钥不是弱口令或已公开的默认值");
        titles.put(P2, "Actuator 只暴露必要的端点");
        titles.put(P3, "健康检查不对外展示细节");
        titles.put(P4, "接口文档已关闭");
        titles.put(P5, "网关未开启服务发现自动路由");
        titles.put(P6, "Nacos 已开启鉴权（拒绝匿名请求）");
        titles.put(P7, "登录图形验证码已开启");
        titles.put(P8, "密码哈希（BCrypt）强度不低于 " + MIN_BCRYPT_COST);
        titles.put(P9, "未开启完整 SQL 日志（不输出参数值）");
        return Collections.unmodifiableMap(titles);
    }

    /**
     * 服务通过 spring.config.import 引入的公共配置
     */
    static final String COMMON_CONFIG = "application-common.yml";

    /**
     * 公共配置中的标记项：存在即说明公共配置已从 Nacos 成功加载
     */
    public static final String COMMON_LOADED_MARKER = "tuoyu.config.common-loaded";

    static final int MIN_SECRET_LENGTH = 12;

    /**
     * 生产环境要求的最低 BCrypt 强度（与 tuoyu-common-encrypt 的 PasswordHasher 一致）
     */
    static final int MIN_BCRYPT_COST = 10;

    static final String BCRYPT_COST_KEY = "tuoyu.auth.password.bcrypt-cost";

    /**
     * 登录图形验证码开关，只有 tuoyu-auth 配置了它
     */
    static final String CAPTCHA_ENABLED_KEY = "security.captcha.enabled";

    /**
     * 完整 SQL 日志开关（tuoyu-common-mybatis），开启后会输出每条 SQL 的参数值
     */
    static final String SQL_LOG_ENABLED_KEY = "mybatis-plus.sql-log.enabled";

    /**
     * 只扫描配置类属性源；系统属性、环境变量、.env 中保存的是变量的实际值，不需要检查占位符
     */
    private static final Set<String> NON_CONFIG_SOURCES = Set.of(
        StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
        "tuoyuDotEnv", "random", "configurationProperties");

    private static final Pattern TUOYU_PLACEHOLDER = Pattern.compile("\\$\\{(TUOYU_[A-Z0-9_]+)(:[^}]*)?}");

    /**
     * 按配置项名的最后一段判断是否为口令或密钥（已转小写并去掉 - 和 _）
     */
    private static final Pattern SECRET_KEY = Pattern.compile("(password|passwd|secret|secretkey|privatekey|token|accesskeysecret)$");

    private static final List<String> PLACEHOLDER_MARKERS = List.of("CHANGE_ME", "CHANGEME", "REPLACE_WITH");

    /**
     * 已公开的默认密钥的 SHA-256（只保存哈希，不在代码中保留原文）：
     * 上游 JWT 密钥、上游接口加密私钥、上游 SnailJob / Snail AI 令牌、上游与 Nacos 官方的默认令牌密钥、上游 AI 加密 key / iv 默认值，
     * 以及本仓库接口加密测试向量中的私钥（tuoyu-common-encrypt 测试资源 api-crypto/test-vectors.json）
     */
    private static final Set<String> KNOWN_LEAKED_SHA256 = Set.of(
        "71c480df93d6ae2f1efad1447c66c9525e316218cf51fc8d9ed832f2daf18b73",
        "7012deebd3eacaef8c851e94410cd48d2c63bcada69a40936a2c871c67a48e5d",
        "649fc35427162c05d240181cffb262d37b3048b2aaf067aea4610e5278e4f727",
        "7aca917aab4a0413c843a8f2ae706625cef1d5072f44e95e45f4c428c8103c5f",
        "5ec2a2b86ad6af26f7f1df9675d62d3dce8cc1fe284805d2b31fd3a07e119224",
        "4de7be2d26fa1e2e608014c657f699698236694b2d6ef11d888828ba3e81f9bf",
        "3eb1bd439947eb762998e566ccc2e099c791118b2f40579cc4f7da2b5061b7f9",
        "4ba68aa8767bde72e8c798ee82d1275291cea73e72ad74d35ecf48e41386eb82",
        "0225d3abc4fa80459fd02d1719995b51fe3e7ffe026be6e19a98bcbed6d569a9");

    private SelfChecks() {
    }

    /**
     * 任何环境都执行的检查：C1、C2
     */
    public static List<SelfCheckProblem> runtimeChecks(ConfigurableEnvironment environment) {
        Map<String, Object> raw = effectiveRawValues(environment);
        List<SelfCheckProblem> problems = new ArrayList<>();
        checkMissingVariables(environment, raw).ifPresent(problems::add);
        checkCommonConfigLoaded(environment, raw).ifPresent(problems::add);
        return problems;
    }

    /**
     * 只读取配置的生产环境检查：P1 ~ P5、P7 ~ P9（P6 需要请求 Nacos，见 {@link #nacosProbeTarget} 与 {@link #checkNacosAuth}）
     *
     * @param springDocPresent 类路径中是否存在 springdoc（不存在时 P4 无意义）
     */
    public static List<SelfCheckProblem> productionChecks(ConfigurableEnvironment environment, boolean springDocPresent) {
        Map<String, Object> raw = effectiveRawValues(environment);
        List<SelfCheckProblem> problems = new ArrayList<>();
        checkWeakSecrets(environment, raw).ifPresent(problems::add);
        checkActuatorExposure(environment).ifPresent(problems::add);
        checkHealthDetails(environment).ifPresent(problems::add);
        if (springDocPresent) {
            checkApiDocs(environment).ifPresent(problems::add);
        }
        checkGatewayDiscoveryLocator(environment).ifPresent(problems::add);
        checkCaptchaEnabled(environment).ifPresent(problems::add);
        checkBcryptCost(environment).ifPresent(problems::add);
        checkSqlLog(environment).ifPresent(problems::add);
        return problems;
    }

    /**
     * C1：配置中引用的 ${TUOYU_XXX} 没有默认值，且未提供
     */
    static Optional<SelfCheckProblem> checkMissingVariables(PropertyResolver environment, Map<String, Object> raw) {
        Set<String> missing = new TreeSet<>();
        raw.values().forEach(value -> {
            Matcher matcher = TUOYU_PLACEHOLDER.matcher(String.valueOf(value));
            while (matcher.find()) {
                if (matcher.group(2) == null && !hasText(environment, matcher.group(1))) {
                    missing.add(matcher.group(1));
                }
            }
        });
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(C1, "缺少必填的环境变量", List.copyOf(missing),
            "通过环境变量或仓库根目录的 .env 提供上述变量（本地开发可执行 java tools/secrets/GenerateEnv.java 生成），变量说明见 .env.example"));
    }

    /**
     * C2：服务声明引入了公共配置，但公共配置没有加载成功（Nacos 不可达、命名空间或账号错误、配置未导入等）
     */
    static Optional<SelfCheckProblem> checkCommonConfigLoaded(PropertyResolver environment, Map<String, Object> raw) {
        boolean declared = raw.entrySet().stream()
            .anyMatch(e -> e.getKey().startsWith("spring.config.import") && String.valueOf(e.getValue()).contains(COMMON_CONFIG));
        if (!declared || "true".equalsIgnoreCase(environment.getProperty(COMMON_LOADED_MARKER))) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(C2, "未能从 Nacos 加载公共配置 " + COMMON_CONFIG,
            List.of("Nacos 地址：" + environment.getProperty("spring.cloud.nacos.server-addr", "（未配置）"),
                "命名空间：" + environment.getProperty("spring.cloud.nacos.config.namespace", "（未配置）")),
            "依次检查：Nacos 是否已启动且地址正确（TUOYU_NACOS_SERVER）；命名空间是否正确（TUOYU_NACOS_NAMESPACE）；"
                + "账号口令是否正确；配置是否已导入（java tools/nacos/ImportNacosConfig.java --apply），"
                + "且导入的是包含 " + COMMON_LOADED_MARKER + " 的最新版本"));
    }

    /**
     * P1：口令、密钥过于简单，或使用了已公开的默认值。只报告配置项名，不输出具体的值
     */
    static Optional<SelfCheckProblem> checkWeakSecrets(PropertyResolver environment, Map<String, Object> raw) {
        List<String> weak = new ArrayList<>();
        for (String name : raw.keySet()) {
            if (!SECRET_KEY.matcher(normalizedLastSegment(name)).find()) {
                continue;
            }
            String value;
            try {
                value = environment.getProperty(name);
            } catch (IllegalArgumentException e) {
                // 无法解析的占位符由 C1 负责
                continue;
            }
            // 未使用的可选组件允许留空；布尔值是开关（如 sa-token.check-same-token），不是口令
            if (StringUtils.hasText(value) && !isBoolean(value) && isWeak(value)) {
                weak.add(name);
            }
        }
        if (weak.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(P1, "口令或密钥过于简单，或使用了已公开的默认值（不显示具体值）", weak,
            "改为至少 " + MIN_SECRET_LENGTH + " 位的随机值（可参考 java tools/secrets/GenerateEnv.java 的生成方式），并通过环境变量注入"));
    }

    /**
     * P2：Actuator 暴露了全部端点
     */
    static Optional<SelfCheckProblem> checkActuatorExposure(ConfigurableEnvironment environment) {
        List<String> include = bindList(environment, "management.endpoints.web.exposure.include");
        if (include.stream().map(String::trim).noneMatch("*"::equals)) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(P2, "Actuator 暴露了全部端点",
            List.of("management.endpoints.web.exposure.include=*"),
            "只暴露必要端点：删除 TUOYU_ACTUATOR_EXPOSURE，或设置为 health,info,prometheus"));
    }

    /**
     * P3：健康检查对外展示全部细节
     */
    static Optional<SelfCheckProblem> checkHealthDetails(PropertyResolver environment) {
        if (!"always".equalsIgnoreCase(safeGet(environment, "management.endpoint.health.show-details"))) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(P3, "健康检查对外展示全部细节",
            List.of("management.endpoint.health.show-details=always"),
            "删除 TUOYU_HEALTH_SHOW_DETAILS，或设置为 never"));
    }

    /**
     * P4：接口文档（springdoc）处于开启状态，springdoc 未显式关闭时默认开启
     */
    static Optional<SelfCheckProblem> checkApiDocs(PropertyResolver environment) {
        if ("false".equalsIgnoreCase(safeGet(environment, "springdoc.api-docs.enabled"))) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(P4, "接口文档处于开启状态",
            List.of("springdoc.api-docs.enabled 不为 false"),
            "删除 TUOYU_API_DOCS_ENABLED，或设置为 false"));
    }

    /**
     * P5：网关开启了服务发现自动路由，已登录用户可经 /{服务名}/** 访问所有注册服务
     */
    static Optional<SelfCheckProblem> checkGatewayDiscoveryLocator(PropertyResolver environment) {
        if (!"true".equalsIgnoreCase(safeGet(environment, "spring.cloud.gateway.server.webmvc.discovery.locator.enabled"))) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(P5, "网关开启了服务发现自动路由",
            List.of("spring.cloud.gateway.server.webmvc.discovery.locator.enabled=true"),
            "删除 TUOYU_GATEWAY_DISCOVERY_LOCATOR，或设置为 false；需要对外的服务请在网关中显式配置路由"));
    }

    /**
     * P6 的探测目标：只有公共配置确实是从 Nacos 加载的（C2 通过）才探测，否则本服务不涉及 Nacos 配置中心，P6 不适用
     */
    static Optional<NacosAuthProbe.Target> nacosProbeTarget(PropertyResolver environment, Map<String, Object> raw) {
        boolean fromNacos = raw.entrySet().stream()
            .anyMatch(e -> e.getKey().startsWith("spring.config.import")
                && String.valueOf(e.getValue()).contains("nacos:" + COMMON_CONFIG));
        if (!fromNacos || !"true".equalsIgnoreCase(safeGet(environment, COMMON_LOADED_MARKER))) {
            return Optional.empty();
        }
        String serverAddr = safeGet(environment, "spring.cloud.nacos.config.server-addr");
        if (!StringUtils.hasText(serverAddr)) {
            serverAddr = safeGet(environment, "spring.cloud.nacos.server-addr");
        }
        if (!StringUtils.hasText(serverAddr)) {
            return Optional.empty();
        }
        String namespace = safeGet(environment, "spring.cloud.nacos.config.namespace");
        String group = safeGet(environment, "spring.cloud.nacos.config.group");
        return Optional.of(new NacosAuthProbe.Target(nacosBaseUrl(serverAddr),
            namespace == null ? "" : namespace.trim(), StringUtils.hasText(group) ? group.trim() : "DEFAULT_GROUP", COMMON_CONFIG));
    }

    /**
     * 由 server-addr 得到 Nacos 的 HTTP 地址：多个地址取第一个；未写协议时为 http；未写端口时为 8848；未写路径时为 /nacos
     */
    static String nacosBaseUrl(String serverAddr) {
        String first = serverAddr.split(",")[0].trim();
        String scheme = "http://";
        int schemeEnd = first.indexOf("://");
        if (schemeEnd >= 0) {
            scheme = first.substring(0, schemeEnd + 3);
            first = first.substring(schemeEnd + 3);
        }
        int slash = first.indexOf('/');
        String hostPort = slash >= 0 ? first.substring(0, slash) : first;
        String path = slash >= 0 ? first.substring(slash).replaceAll("/+$", "") : "";
        if (!hostPort.contains(":")) {
            hostPort += ":8848";
        }
        return scheme + hostPort + (path.isEmpty() ? "/nacos" : path);
    }

    /**
     * P6：Nacos 未开启鉴权，任何能访问 Nacos 的人都能读取配置（数据库口令等）并修改配置、注册假服务
     */
    static Optional<SelfCheckProblem> checkNacosAuth(NacosAuthProbe.Result result) {
        if (result.state() != NacosAuthProbe.State.DISABLED) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(P6, "Nacos 未开启鉴权", List.of(result.detail()),
            "在 Nacos 服务端开启鉴权（nacos.core.auth.enabled=true，并设置 token.secret.key 与服务身份密钥），"
                + "再通过 TUOYU_NACOS_USERNAME / TUOYU_NACOS_PASSWORD 提供账号口令"));
    }

    /**
     * P7：关闭了登录图形验证码，密码登录只剩失败锁定一道防线。只有配置了该开关的服务（tuoyu-auth）才检查
     */
    static Optional<SelfCheckProblem> checkCaptchaEnabled(PropertyResolver environment) {
        if (!"false".equalsIgnoreCase(safeGet(environment, CAPTCHA_ENABLED_KEY))) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(P7, "登录图形验证码已关闭",
            List.of(CAPTCHA_ENABLED_KEY + "=false"),
            "在 tuoyu-auth.yml 中把 " + CAPTCHA_ENABLED_KEY + " 改回 true"));
    }

    /**
     * P8：密码哈希强度过低（未配置时为默认值 10，通过）
     */
    static Optional<SelfCheckProblem> checkBcryptCost(PropertyResolver environment) {
        String value = safeGet(environment, BCRYPT_COST_KEY);
        if (value == null) {
            return Optional.empty();
        }
        try {
            if (Integer.parseInt(value.trim()) >= MIN_BCRYPT_COST) {
                return Optional.empty();
            }
        } catch (NumberFormatException e) {
            // 不是整数：按未通过处理，服务创建 PasswordHasher 时同样会失败
        }
        return Optional.of(new SelfCheckProblem(P8, "密码哈希（BCrypt）强度过低",
            List.of(BCRYPT_COST_KEY + "=" + value.trim()),
            "设置为 " + MIN_BCRYPT_COST + " ~ 31 之间的整数（建议 10 ~ 12），修改后用户下次修改密码时按新强度保存"));
    }

    /**
     * P9：开启了完整 SQL 日志，参数值（密码哈希、手机号等）会原样写进日志
     */
    static Optional<SelfCheckProblem> checkSqlLog(PropertyResolver environment) {
        if (!"true".equalsIgnoreCase(safeGet(environment, SQL_LOG_ENABLED_KEY))) {
            return Optional.empty();
        }
        return Optional.of(new SelfCheckProblem(P9, "开启了完整 SQL 日志，参数值（含密码哈希、手机号等）会写进日志",
            List.of(SQL_LOG_ENABLED_KEY + "=true"),
            "删除 TUOYU_SQL_LOG_ENABLED，或设置为 false"));
    }

    /**
     * 每个配置项取优先级最高的属性源中的原始值（未解析占位符），用于判断最终生效的配置引用了哪些变量
     */
    static Map<String, Object> effectiveRawValues(ConfigurableEnvironment environment) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (NON_CONFIG_SOURCES.contains(source.getName()) || !(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String name : enumerable.getPropertyNames()) {
                result.putIfAbsent(name, enumerable.getProperty(name));
            }
        }
        return result;
    }

    static boolean isWeak(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < MIN_SECRET_LENGTH || trimmed.chars().allMatch(Character::isDigit)) {
            return true;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (PLACEHOLDER_MARKERS.stream().anyMatch(upper::contains)) {
            return true;
        }
        return KNOWN_LEAKED_SHA256.contains(sha256(trimmed));
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value.trim()) || "false".equalsIgnoreCase(value.trim());
    }

    static String normalizedLastSegment(String name) {
        String withoutIndex = name.replaceAll("\\[\\d+]$", "");
        String last = withoutIndex.substring(withoutIndex.lastIndexOf('.') + 1);
        return last.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static boolean hasText(PropertyResolver environment, String key) {
        return StringUtils.hasText(safeGet(environment, key));
    }

    private static String safeGet(PropertyResolver environment, String key) {
        try {
            return environment.getProperty(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> bindList(ConfigurableEnvironment environment, String key) {
        try {
            return Binder.get(environment).bind(key, Bindable.listOf(String.class)).orElse(List.of());
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
