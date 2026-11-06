import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 把 deploy/config/nacos 下的配置批量导入 Nacos 2.x。
 * <p>
 * 用法（在仓库根目录执行，需要 JDK 21）：
 * <pre>
 *   java tools/nacos/ImportNacosConfig.java                       # 预览：只列出将要执行的操作，不写入
 *   java tools/nacos/ImportNacosConfig.java --apply               # 导入：新建不存在的配置，内容相同的跳过
 *   java tools/nacos/ImportNacosConfig.java --apply --overwrite   # 导入，并覆盖内容不同的已有配置
 *   java tools/nacos/ImportNacosConfig.java --init-admin --apply  # 导入前先初始化管理员口令（只用于新部署的 Nacos，见下文）
 * </pre>
 * 连接参数读取顺序：环境变量 &gt; 仓库根目录的 .env。使用的变量：
 * {@code TUOYU_NACOS_SERVER}、{@code TUOYU_NACOS_NAMESPACE}、{@code TUOYU_NACOS_USERNAME}、{@code TUOYU_NACOS_PASSWORD}。
 * <p>
 * 使用 Nacos v1 开放接口（2.x 全版本可用），Data ID 为文件名，Group 固定为 DEFAULT_GROUP。
 * <p>
 * {@code --init-admin}：Nacos 2.4 起开启鉴权后没有默认口令，管理员 nacos 的口令要在首次使用时初始化。
 * 加上这个参数（并且是 --apply）时，导入前先用 {@code TUOYU_NACOS_PASSWORD} 初始化管理员口令；已经初始化过的 Nacos 会拒绝，
 * 此时跳过，不影响导入。docker-compose 中的一次性任务 nacos-init 使用它；与其他项目共用的 Nacos 不要加这个参数。
 */
public class ImportNacosConfig {

    private static final String GROUP = "DEFAULT_GROUP";

    private static final Path CONFIG_DIR = Path.of("deploy", "config", "nacos");

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final String baseUrl;

    /**
     * Nacos 2.x 中 public 命名空间的 ID 为空字符串
     */
    private final String tenant;

    private String accessToken;

    private ImportNacosConfig(String server, String namespace) {
        String address = server.startsWith("http://") || server.startsWith("https://") ? server : "http://" + server;
        this.baseUrl = address.replaceAll("/+$", "") + "/nacos";
        this.tenant = namespace == null || namespace.isBlank() || "public".equalsIgnoreCase(namespace) ? "" : namespace;
    }

    public static void main(String[] args) throws Exception {
        try {
            run(List.of(args));
        } catch (IllegalStateException | IOException e) {
            exit("导入中止：" + e.getMessage());
        }
    }

    private static void run(List<String> options) throws IOException, InterruptedException {
        boolean apply = options.contains("--apply");
        boolean overwrite = options.contains("--overwrite");
        boolean initAdmin = options.contains("--init-admin");
        if (!Files.isDirectory(CONFIG_DIR)) {
            exit("请在仓库根目录执行：java tools/nacos/ImportNacosConfig.java");
        }

        Map<String, String> env = loadEnv(Path.of(".env"));
        String server = env.getOrDefault("TUOYU_NACOS_SERVER", "127.0.0.1:8848");
        String namespace = env.getOrDefault("TUOYU_NACOS_NAMESPACE", "public");
        ImportNacosConfig importer = new ImportNacosConfig(server, namespace);

        System.out.printf("Nacos：%s，命名空间：%s，模式：%s%n%n", importer.baseUrl, namespace,
            apply ? (overwrite ? "导入（覆盖内容不同的配置）" : "导入") : "预览（不写入，确认无误后加 --apply）");

        if (initAdmin) {
            importer.initAdmin(env.get("TUOYU_NACOS_USERNAME"), env.get("TUOYU_NACOS_PASSWORD"), apply);
        }
        importer.login(env.get("TUOYU_NACOS_USERNAME"), env.get("TUOYU_NACOS_PASSWORD"));

        List<String> conflicts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (Path file : configFiles()) {
            String dataId = file.getFileName().toString();
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String existing = importer.get(dataId);
            String action;
            if (existing == null) {
                action = "新建";
                if (apply) {
                    importer.publish(dataId, content, typeOf(dataId));
                }
                created++;
            } else if (normalize(existing).equals(normalize(content))) {
                action = "相同，跳过";
                unchanged++;
            } else if (overwrite) {
                action = "覆盖";
                if (apply) {
                    importer.publish(dataId, content, typeOf(dataId));
                }
                updated++;
            } else {
                action = "已存在且内容不同，跳过（加 --overwrite 才会覆盖）";
                conflicts.add(dataId);
            }
            System.out.printf("  %-32s %s%n", dataId, action);
        }

        System.out.printf("%n%s：新建 %d，覆盖 %d，相同 %d，冲突 %d%n",
            apply ? "完成" : "预览结果", created, updated, unchanged, conflicts.size());
        if (!conflicts.isEmpty() && importer.tenant.isEmpty()) {
            System.out.println("""

                提示：目标是 public 命名空间，且已有同名配置。如果这个 Nacos 与其他项目共用，
                建议在 Nacos 控制台新建独立的命名空间，并在 .env 中设置 TUOYU_NACOS_NAMESPACE=<命名空间 ID>，
                不要覆盖其他项目的配置。""");
        }
    }

    /**
     * 初始化管理员 nacos 的口令（Nacos 2.4 起开启鉴权后没有默认口令）。已有管理员时 Nacos 拒绝，跳过即可
     */
    private void initAdmin(String username, String password, boolean apply) throws IOException, InterruptedException {
        if (!apply) {
            System.out.println("预览模式，不初始化管理员口令\n");
            return;
        }
        if (password == null || password.isBlank()) {
            System.out.println("未配置 TUOYU_NACOS_PASSWORD，不初始化管理员口令\n");
            return;
        }
        if (username != null && !username.isBlank() && !"nacos".equals(username)) {
            System.out.printf("--init-admin 只能初始化管理员 nacos，当前 TUOYU_NACOS_USERNAME 为 %s，跳过%n%n", username);
            return;
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/auth/users/admin"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(form(Map.of("password", password))));
        if (response.statusCode() == 200) {
            System.out.println("已用 TUOYU_NACOS_PASSWORD 初始化管理员 nacos 的口令\n");
        } else {
            System.out.printf("管理员口令已初始化过或 Nacos 不支持初始化接口（HTTP %d），跳过%n%n", response.statusCode());
        }
    }

    /**
     * 开启鉴权时登录获取 accessToken；未开启鉴权或未配置口令时直接继续
     */
    private void login(String username, String password) throws IOException, InterruptedException {
        if (password == null || password.isBlank()) {
            System.out.println("未配置 TUOYU_NACOS_PASSWORD，按未开启鉴权处理\n");
            return;
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/auth/login"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(form(Map.of("username", username == null ? "nacos" : username, "password", password))));
        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        if (response.statusCode() == 200 && matcher.find()) {
            accessToken = matcher.group(1);
            System.out.println("登录成功\n");
        } else {
            System.out.printf("登录未成功（HTTP %d），按未开启鉴权继续。如果 Nacos 开启了鉴权，请检查账号口令%n%n",
                response.statusCode());
        }
    }

    /**
     * 查询配置，不存在时返回 null
     */
    private String get(String dataId) throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("dataId", dataId);
        params.put("group", GROUP);
        params.put("tenant", tenant);
        if (accessToken != null) {
            params.put("accessToken", accessToken);
        }
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/cs/configs?" + query(params))).GET());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IllegalStateException("没有访问权限（HTTP " + response.statusCode()
                + "）：Nacos 已开启鉴权，请检查 TUOYU_NACOS_USERNAME / TUOYU_NACOS_PASSWORD");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("查询配置 " + dataId + " 失败：HTTP " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }

    private void publish(String dataId, String content, String type) throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("dataId", dataId);
        params.put("group", GROUP);
        params.put("tenant", tenant);
        params.put("type", type);
        params.put("content", content);
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/cs/configs" + tokenQuery()))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(form(params)));
        if (response.statusCode() != 200 || !"true".equals(response.body().trim())) {
            throw new IllegalStateException("发布配置 " + dataId + " 失败：HTTP " + response.statusCode() + " " + response.body());
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        try {
            return http.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IOException("无法连接 Nacos：" + baseUrl + "，请检查 TUOYU_NACOS_SERVER 与 Nacos 是否已启动", e);
        }
    }

    private String tokenQuery() {
        return accessToken == null ? "" : "?accessToken=" + encode(accessToken);
    }

    private static List<Path> configFiles() throws IOException {
        try (Stream<Path> files = Files.list(CONFIG_DIR)) {
            return files.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml") || p.toString().endsWith(".properties"))
                .sorted()
                .toList();
        }
    }

    private static String typeOf(String dataId) {
        return dataId.endsWith(".properties") ? "properties" : "yaml";
    }

    /**
     * 比较时忽略换行符差异
     */
    private static String normalize(String content) {
        return content.replace("\r\n", "\n").strip();
    }

    /**
     * 读取 .env，再用环境变量覆盖；空值视为未设置
     */
    private static Map<String, String> loadEnv(Path file) throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            properties.forEach((key, value) -> {
                if (!value.toString().isBlank()) {
                    env.put(key.toString(), value.toString().trim());
                }
            });
        }
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("TUOYU_") && !value.isBlank()) {
                env.put(key, value);
            }
        });
        return env;
    }

    private static HttpRequest.BodyPublisher form(Map<String, String> params) {
        return HttpRequest.BodyPublishers.ofString(query(params), StandardCharsets.UTF_8);
    }

    private static String query(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(encode(key)).append('=').append(encode(value));
        });
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void exit(String message) {
        System.err.println(message);
        System.exit(1);
    }

}
