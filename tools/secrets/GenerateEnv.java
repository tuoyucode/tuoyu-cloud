import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成本地 .env：为 tuoyu-cloud 所需的全部密钥与口令生成随机强值。
 * <p>
 * 用法（在仓库根目录执行，需要 JDK 21）：
 * <pre>
 *   java tools/secrets/GenerateEnv.java            # 生成 .env，已存在时不覆盖
 *   java tools/secrets/GenerateEnv.java --force    # 覆盖已有的 .env
 *   java tools/secrets/GenerateEnv.java --api-crypto-key  # 只输出一把新的接口加密私钥，不修改任何文件（用于补充或轮换）
 *   java tools/secrets/GenerateEnv.java --field-key       # 只输出一把新的字段加密密钥（AES，32 字节），不修改任何文件
 *   java tools/secrets/GenerateEnv.java --field-key sm4   # 同上，SM4（16 字节）
 * </pre>
 * 产出文件（均已被 .gitignore 忽略，禁止提交）：
 * <ul>
 *     <li>{@code .env}：后端服务与 docker-compose 共用的环境变量；</li>
 *     <li>{@code deploy/docker/prometheus/monitor.password}：Prometheus 抓取监控数据用的口令文件。</li>
 * </ul>
 * 变量清单与说明见仓库根目录的 {@code .env.example}。
 */
public class GenerateEnv {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String ALNUM = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    public static void main(String[] args) throws Exception {
        if (List.of(args).contains("--api-crypto-key")) {
            System.out.println("TUOYU_API_CRYPTO_PRIVATE_KEY=" + encode(rsa().getPrivate().getEncoded()));
            return;
        }
        // 字段加密默认关闭，密钥只在需要时生成：一旦有数据用它加密，丢失密钥就无法恢复，因此不随 .env 自动生成
        if (List.of(args).contains("--field-key")) {
            boolean sm4 = List.of(args).contains("sm4");
            System.out.println("TUOYU_FIELD_ENCRYPT_KEY=" + base64(sm4 ? 16 : 32));
            System.err.println("请妥善备份这把密钥：密钥丢失后，已加密的数据无法恢复。" + (sm4 ? "（算法需配置为 SM4）" : ""));
            return;
        }
        boolean force = List.of(args).contains("--force");
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isRegularFile(root.resolve("pom.xml")) || !Files.isDirectory(root.resolve("deploy"))) {
            exit("请在仓库根目录执行：java tools/secrets/GenerateEnv.java");
        }
        Path envFile = root.resolve(".env");
        if (Files.exists(envFile) && !force) {
            exit(".env 已存在，未做任何修改。如需重新生成，请加 --force（会覆盖现有口令）");
        }

        // 接口加密只需要后端的 RSA 私钥：公钥由它推导，前端运行时获取，前端不持有任何密钥
        KeyPair apiCrypto = rsa();
        String monitorPassword = alnum(24);
        String snailJobToken = "SJ_" + alnum(32);
        String snailAiToken = "SAI_" + hex(16);

        Map<String, String> env = new LinkedHashMap<>();
        env.put("# ---------- 运行环境 ----------", null);
        env.put("TUOYU_PROFILES_ACTIVE", "dev");
        env.put("# ---------- 开发环境便利项（仅限本地，生产环境不要设置） ----------", null);
        env.put("TUOYU_ACTUATOR_EXPOSURE", "*");
        env.put("TUOYU_HEALTH_SHOW_DETAILS", "always");
        env.put("TUOYU_API_DOCS_ENABLED", "true");
        env.put("TUOYU_GATEWAY_DISCOVERY_LOCATOR", "true");
        env.put("TUOYU_SQL_LOG_ENABLED", "true");
        // 停机时不等待下线，本地重启更快；TUOYU_SHUTDOWN_TIMEOUT、TUOYU_DUBBO_SHUTDOWN_WAIT、TUOYU_DUBBO_TIMEOUT 使用默认值
        env.put("TUOYU_SHUTDOWN_OFFLINE_DELAY", "0s");
        env.put("# ---------- Nacos ----------", null);
        env.put("TUOYU_NACOS_SERVER", "127.0.0.1:8848");
        env.put("TUOYU_NACOS_NAMESPACE", "public");
        env.put("TUOYU_NACOS_USERNAME", "nacos");
        env.put("TUOYU_NACOS_PASSWORD", alnum(24));
        env.put("TUOYU_NACOS_AUTH_TOKEN", base64(32));
        env.put("TUOYU_NACOS_IDENTITY_KEY", "tuoyu-" + alnum(12));
        env.put("TUOYU_NACOS_IDENTITY_VALUE", alnum(32));
        env.put("# ---------- 数据库 / Redis / MinIO ----------", null);
        env.put("TUOYU_DB_TYPE", "mysql");
        env.put("TUOYU_DB_HOST", "localhost");
        env.put("TUOYU_DB_PORT", "3306");
        env.put("TUOYU_DB_USERNAME", "root");
        env.put("TUOYU_DB_PASSWORD", alnum(24));
        env.put("TUOYU_REDIS_HOST", "localhost");
        env.put("TUOYU_REDIS_PORT", "6379");
        env.put("TUOYU_REDIS_DATABASE", "0");
        env.put("TUOYU_REDIS_PASSWORD", alnum(24));
        env.put("TUOYU_MINIO_ENDPOINT", "http://localhost:9000");
        env.put("TUOYU_MINIO_ACCESS_KEY", "tuoyu" + alnum(10));
        env.put("TUOYU_MINIO_SECRET_KEY", alnum(32));
        env.put("# ---------- 认证与接口加密 ----------", null);
        env.put("TUOYU_ADMIN_INITIAL_PASSWORD", alnum(16));
        env.put("TUOYU_JWT_SECRET", alnum(64));
        env.put("TUOYU_API_CRYPTO_PRIVATE_KEY", encode(apiCrypto.getPrivate().getEncoded()));
        env.put("# ---------- 多租户（默认关闭） ----------", null);
        env.put("TUOYU_TENANT_CONTEXT_SECRET", alnum(48));
        env.put("# ---------- 监控 ----------", null);
        env.put("TUOYU_MONITOR_USERNAME", "tuoyu");
        env.put("TUOYU_MONITOR_PASSWORD", monitorPassword);
        env.put("TUOYU_GRAFANA_ADMIN_PASSWORD", alnum(24));
        env.put("# ---------- 任务调度 / AI ----------", null);
        env.put("TUOYU_SNAILJOB_TOKEN", snailJobToken);
        env.put("TUOYU_SNAILJOB_ADMIN_PASSWORD", alnum(24));
        env.put("TUOYU_SNAILAI_TOKEN", snailAiToken);
        env.put("TUOYU_SNAILAI_CRYPTO_KEY", hex(16));
        env.put("TUOYU_SNAILAI_CRYPTO_IV", hex(16));
        env.put("# ---------- 可选组件（使用时再配置） ----------", null);
        env.put("TUOYU_RABBITMQ_HOST", "localhost");
        env.put("TUOYU_RABBITMQ_PORT", "5672");
        env.put("TUOYU_RABBITMQ_USERNAME", "tuoyu");
        env.put("TUOYU_RABBITMQ_PASSWORD", alnum(24));

        StringBuilder content = new StringBuilder()
            .append("# 由 tools/secrets/GenerateEnv.java 生成。禁止提交到仓库。\n")
            .append("# 如果使用已有的 MySQL / Redis / Nacos 等中间件，请把对应的地址与口令改成实际值。\n")
            .append("# 第三方登录、短信等可选变量见 .env.example。\n");
        env.forEach((key, value) -> {
            if (value == null) {
                content.append('\n').append(key).append('\n');
            } else {
                content.append(key).append('=').append(value).append('\n');
            }
        });
        write(envFile, content.toString());

        write(root.resolve("deploy/docker/prometheus/monitor.password"), monitorPassword);

        System.out.println("""
            已生成：
              .env                                      后端服务与 docker-compose 使用
              deploy/docker/prometheus/monitor.password Prometheus 监控口令

            初始化数据库后，还需执行以下语句，使任务调度与 AI 的令牌和 .env 一致
            （用 deploy/docker 下的 compose 部署时，tuoyu-job 库的语句由一次性任务 snailjob-token-init 自动执行）：
              -- tuoyu-job 库
              UPDATE sj_group_config SET token = '%s';
              -- tuoyu-ai 库（仅使用 AI 扩展时需要）
              UPDATE sai_app SET token = '%s' WHERE id = 1;
            """.formatted(snailJobToken, snailAiToken));
    }

    private static KeyPair rsa() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, RANDOM);
        return generator.generateKeyPair();
    }

    private static String alnum(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALNUM.charAt(RANDOM.nextInt(ALNUM.length())));
        }
        return sb.toString();
    }

    private static String hex(int bytes) {
        return HexFormat.of().formatHex(randomBytes(bytes));
    }

    private static String base64(int bytes) {
        return encode(randomBytes(bytes));
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void exit(String message) {
        System.err.println(message);
        System.exit(1);
    }

}
