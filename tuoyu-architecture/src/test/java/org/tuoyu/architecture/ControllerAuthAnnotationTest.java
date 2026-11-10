package org.tuoyu.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接口的权限注解（防止新接口漏标注）。
 * <p>
 * 登录校验已下沉到各服务（{@code RequestAuthenticator}），没有注解的接口只要登录就能访问。所以每个 Controller 的
 * handler 方法（或所在的类）必须明确写出访问要求：
 * <ul>
 *     <li>管理类接口：{@code @SaCheckPermission}（权限码必须在菜单种子数据中，缺少时写迁移脚本补充按钮菜单）或 {@code @SaCheckRole}；</li>
 *     <li>只要求登录的接口（个人中心、首页、字典下拉等）：{@code @SaCheckLogin}，并在注释中写明原因；</li>
 *     <li>免登录接口：{@code @SaIgnore}，且完整路径必须在公共配置的免登录白名单 {@code security.ignore.whites} 中。
 *     反过来，白名单中的接口必须标注 {@code @SaIgnore}，否则未登录访问时会被方法上的注解拒绝。</li>
 * </ul>
 * 确有理由不使用 Sa-Token 注解的类列在 {@link #EXEMPTIONS} 中并写明原因。
 */
@DisplayName("接口的权限注解")
class ControllerAuthAnnotationTest {

    private static final String SA_IGNORE = "cn.dev33.satoken.annotation.SaIgnore";

    private static final String SA_CHECK_PERMISSION = "cn.dev33.satoken.annotation.SaCheckPermission";

    /**
     * 认可的访问要求注解：权限、角色、登录、忽略
     */
    private static final List<String> AUTH_ANNOTATIONS = List.of(
        SA_CHECK_PERMISSION,
        "cn.dev33.satoken.annotation.SaCheckRole",
        "cn.dev33.satoken.annotation.SaCheckOr",
        "cn.dev33.satoken.annotation.SaCheckLogin",
        SA_IGNORE);

    private static final List<String> CONTROLLER_ANNOTATIONS = List.of(
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller");

    private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

    private static final List<String> MAPPING_ANNOTATIONS = List.of(
        REQUEST_MAPPING,
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping");

    /**
     * 不检查的 Controller（类名 → 原因）
     */
    private static final Map<String, String> EXEMPTIONS = Map.of(
        "org.tuoyu.modules.monitor.controller.PrometheusController",
        "tuoyu-monitor（监控中心）不使用 Sa-Token，全部请求由 Spring Security 校验登录（WebSecurityConfigurer）",
        "org.tuoyu.common.web.handler.ApiErrorController",
        "错误页面只由容器的错误分派访问，各服务的登录过滤器排除了 /error；tuoyu-common-web 不依赖 Sa-Token",
        "org.tuoyu.gateway.handler.GatewayErrorController",
        "网关的错误页面只由容器的错误分派访问，网关的登录过滤器排除了 /error");

    /**
     * 框架自带模块的根包 → 网关路由前缀（tuoyu-gateway.yml，StripPrefix=1），用于还原前端访问的完整路径。
     * 业务模块的前缀取自它的单体接入声明（{@link #MONOLITH_MODULE}），与网关路由一致由
     * {@code ModulePathPrefixConsistencyTest} 保证
     */
    private static final Map<String, String> FRAMEWORK_PREFIXES = Map.of(
        "org.tuoyu.auth.", "/auth",
        "org.tuoyu.system.", "/system",
        "org.tuoyu.resource.", "/resource",
        "org.tuoyu.workflow.", "/workflow",
        "org.tuoyu.gen.", "/tool");

    /**
     * 业务模块的单体接入声明（相对模块目录）
     */
    private static final String MONOLITH_MODULE = "src/main/resources/META-INF/tuoyu/monolith-module.yml";

    /**
     * 路径中的 Spring 占位符 {@code ${key:默认值}}：框架中的 Controller 用它配置完整路径
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^:}]+:([^}]*)}");

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]+}");

    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*-\\s*['\"]?([^'\"\\s#]+)['\"]?\\s*(#.*)?$");

    /**
     * 迁移脚本中形如 'system:user:list' 的权限码
     */
    private static final Pattern PERMISSION_CODE = Pattern.compile("'([a-zA-Z]+:[a-zA-Z]+:[a-zA-Z]+)'");

    /**
     * 菜单声明（JSON）中的权限码
     */
    private static final Pattern MENU_PERMISSION = Pattern.compile("\"perms\"\\s*:\\s*\"([a-zA-Z]+:[a-zA-Z]+:[a-zA-Z]+)\"");

    private static Path repositoryRoot;

    private static JavaClasses classes;

    private static List<String> whitelist;

    /**
     * 根包（以点结尾）→ 路由前缀：框架自带的模块加上业务模块声明的
     */
    private static Map<String, String> modulePrefixes;

    @BeforeAll
    static void importClasses() throws IOException {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("tuoyu-framework"))) {
            root = root.getParent();
        }
        assertNotNull(root, "未找到仓库根目录");
        repositoryRoot = root;
        // 只有业务分支上才有 tuoyu-business/pom.xml；从业务分支切回框架分支后残留的编译产物不读取
        boolean businessBranch = Files.isRegularFile(root.resolve("tuoyu-business/pom.xml"));
        List<Path> outputs;
        try (Stream<Path> dirs = Files.walk(root, 4)) {
            outputs = dirs.filter(p -> p.endsWith(Path.of("target", "classes")) && Files.isDirectory(p))
                .filter(p -> !p.toString().contains("tuoyu-extensions") && !p.toString().contains("tuoyu-architecture"))
                .filter(p -> businessBranch || !p.toString().contains("tuoyu-business"))
                .toList();
        }
        assertFalse(outputs.isEmpty(), "没有找到各模块编译后的类，请先完整构建（mvn clean verify）");
        modulePrefixes = new HashMap<>(FRAMEWORK_PREFIXES);
        if (businessBranch) {
            modulePrefixes.putAll(businessModulePrefixes());
        }
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPaths(outputs);
        whitelist = readWhitelist(root.resolve(Path.of("deploy", "config", "nacos", "application-common.yml")));
    }

    @Test
    @DisplayName("每个接口都有权限、角色、登录或忽略注解之一")
    void everyHandlerDeclaresAccessRule() {
        List<String> violations = new ArrayList<>();
        List<JavaClass> controllers = controllers();
        assertFalse(controllers.isEmpty(), "没有找到 Controller");
        for (JavaClass controller : controllers) {
            boolean classLevel = AUTH_ANNOTATIONS.stream().anyMatch(controller::isAnnotatedWith);
            for (JavaMethod method : handlers(controller)) {
                if (!classLevel && AUTH_ANNOTATIONS.stream().noneMatch(method::isAnnotatedWith)) {
                    violations.add(describe(method));
                }
            }
        }
        assertTrue(violations.isEmpty(), "以下接口缺少 @SaCheckPermission / @SaCheckRole / @SaCheckLogin / @SaIgnore"
            + "（只要求登录的接口标注 @SaCheckLogin 并写明原因，免登录接口标注 @SaIgnore 并加入免登录白名单）：\n  "
            + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("@SaIgnore 的接口都在免登录白名单中，白名单中的接口都标注了 @SaIgnore")
    void ignoredHandlersMatchWhitelist() {
        assertFalse(whitelist.isEmpty(), "没有读取到免登录白名单 security.ignore.whites");
        List<String> violations = new ArrayList<>();
        for (JavaClass controller : controllers()) {
            boolean classIgnored = controller.isAnnotatedWith(SA_IGNORE);
            for (JavaMethod method : handlers(controller)) {
                boolean ignored = classIgnored || method.isAnnotatedWith(SA_IGNORE);
                Optional<List<String>> paths = fullPaths(controller, method);
                if (paths.isEmpty()) {
                    if (ignored) {
                        violations.add(describe(method) + "：无法确定完整路径，框架模块请在本测试的 FRAMEWORK_PREFIXES 中登记路由前缀，业务模块请检查单体接入声明中的 prefix 与 packages");
                    }
                    continue;
                }
                for (String path : paths.get()) {
                    boolean whitelisted = whitelist.stream().anyMatch(pattern -> antMatch(pattern, path));
                    if (ignored && !whitelisted) {
                        violations.add(describe(method) + "（" + path + "）标注了 @SaIgnore，但不在免登录白名单中，服务仍会要求登录");
                    } else if (!ignored && whitelisted) {
                        violations.add(describe(method) + "（" + path + "）在免登录白名单中，但没有标注 @SaIgnore，未登录访问会被注解拒绝");
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "免登录白名单（application-common.yml 的 security.ignore.whites）与 @SaIgnore 不一致：\n  "
            + String.join("\n  ", violations));
    }

    @Test
    @DisplayName("@SaCheckPermission 的权限码都在菜单种子数据中（MySQL、PostgreSQL 迁移脚本，业务模块的菜单声明）")
    void permissionsExistInMenuSeeds() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String database : List.of("mysql", "postgresql")) {
            Set<String> seeded = seededPermissions(repositoryRoot.resolve(Path.of(
                "tuoyu-modules", "tuoyu-system", "src", "main", "resources", "db", "migration", "cloud", database)));
            // 业务模块的权限码在模块自己的菜单声明中，启动时登记到菜单表
            seeded.addAll(moduleMenuPermissions());
            assertFalse(seeded.isEmpty(), "没有读取到 " + database + " 菜单种子数据中的权限码");
            for (JavaClass controller : controllers()) {
                List<JavaAnnotation<?>> annotations = new ArrayList<>(controller.getAnnotations());
                handlers(controller).forEach(m -> annotations.addAll(m.getAnnotations()));
                for (JavaAnnotation<?> annotation : annotations) {
                    if (!SA_CHECK_PERMISSION.equals(annotation.getRawType().getName())) {
                        continue;
                    }
                    for (String code : stringValues(annotation)) {
                        if (!code.isEmpty() && !seeded.contains(code)) {
                            violations.add(database + "：" + controller.getSimpleName() + " 使用的权限码 " + code
                                + " 不在菜单种子数据中，请写迁移脚本补充按钮菜单");
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n  ", violations));
    }

    @Test
    @DisplayName("检查确实读到了接口、注解的取值与白名单（防止上面的检查因为读不到而空转）")
    void checksAreNotVacuous() {
        JavaClass userController = classes.get("org.tuoyu.system.controller.system.SysUserController");
        assertTrue(controllers().contains(userController), "没有识别出 SysUserController");
        JavaMethod list = handlers(userController).stream().filter(m -> m.getName().equals("list")).findFirst().orElseThrow();
        assertTrue(stringValues(list.getAnnotationOfType(SA_CHECK_PERMISSION)).contains("system:user:list"),
            "没有读取到 @SaCheckPermission 的权限码");
        assertTrue(fullPaths(userController, list).orElseThrow().contains("/system/user/list"), "完整路径还原不正确");
        long ignored = controllers().stream().flatMap(c -> handlers(c).stream())
            .filter(m -> m.isAnnotatedWith(SA_IGNORE)).count();
        assertTrue(ignored >= 10, "免登录接口只识别出 " + ignored + " 个");
        assertTrue(whitelist.contains("/auth/login") && whitelist.contains("/resource/email/code"), "免登录白名单读取不完整：" + whitelist);
    }

    @Test
    @DisplayName("例外名单中的类都存在（避免名单过期）")
    void exemptionsExist() {
        for (String name : EXEMPTIONS.keySet()) {
            assertTrue(classes.contain(name), "例外名单中的类不存在，请从 EXEMPTIONS 中删除：" + name);
        }
    }

    @Test
    @DisplayName("路径匹配规则与 Ant 风格一致")
    void antMatchFollowsAntStyle() {
        assertTrue(antMatch("/auth/binding/*", "/auth/binding/x"));
        assertFalse(antMatch("/auth/binding/*", "/auth/binding/x/y"));
        assertTrue(antMatch("/warm-flow-ui/**", "/warm-flow-ui/js/app.js"));
        assertTrue(antMatch("/warm-flow-ui/**", "/warm-flow-ui"));
        assertTrue(antMatch("/*/v3/api-docs", "/system/v3/api-docs"));
        assertFalse(antMatch("/resource/sms/code", "/resource/sms/codes"));
    }

    private static List<JavaClass> controllers() {
        return classes.stream()
            .filter(c -> c.getPackageName().startsWith("org.tuoyu."))
            .filter(c -> CONTROLLER_ANNOTATIONS.stream().anyMatch(c::isAnnotatedWith))
            .filter(c -> !EXEMPTIONS.containsKey(c.getName()))
            .toList();
    }

    private static List<JavaMethod> handlers(JavaClass controller) {
        return controller.getMethods().stream()
            .filter(m -> MAPPING_ANNOTATIONS.stream().anyMatch(m::isAnnotatedWith))
            .sorted((a, b) -> a.getFullName().compareTo(b.getFullName()))
            .toList();
    }

    /**
     * 前端访问的完整路径（类与方法上的路径组合，路径变量替换为任意值）
     *
     * @return 无法确定路由前缀时为空
     */
    private static Optional<List<String>> fullPaths(JavaClass controller, JavaMethod method) {
        List<String> classPaths = controller.isAnnotatedWith(REQUEST_MAPPING)
            ? stringValues(controller.getAnnotationOfType(REQUEST_MAPPING)) : List.of("");
        List<String> methodPaths = method.getAnnotations().stream()
            .filter(a -> MAPPING_ANNOTATIONS.contains(a.getRawType().getName()))
            .findFirst().map(ControllerAuthAnnotationTest::stringValues).orElse(List.of(""));
        String prefix = modulePrefixes.entrySet().stream()
            .filter(e -> controller.getPackageName().concat(".").startsWith(e.getKey()))
            .map(Map.Entry::getValue).findFirst().orElse(null);
        List<String> result = new ArrayList<>();
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                String path = join(classPath, methodPath);
                Matcher placeholder = PLACEHOLDER.matcher(path);
                if (placeholder.find()) {
                    // 占位符的默认值已是完整路径
                    path = join(placeholder.replaceAll(Matcher.quoteReplacement(placeholder.group(1))), "");
                } else if (prefix == null) {
                    return Optional.empty();
                } else {
                    path = join(prefix, path);
                }
                result.add(PATH_VARIABLE.matcher(path).replaceAll("x"));
            }
        }
        return Optional.of(result);
    }

    private static List<String> stringValues(JavaAnnotation<?> annotation) {
        for (String property : List.of("value", "path")) {
            Optional<Object> value = annotation.get(property);
            if (value.isPresent() && value.get() instanceof String[] array && array.length > 0) {
                return List.of(array);
            }
        }
        return List.of("");
    }

    private static String join(String left, String right) {
        String path = (left.startsWith("/") || left.isEmpty() ? left : "/" + left)
            + (right.isEmpty() || right.startsWith("/") ? right : "/" + right);
        path = path.replaceAll("/{2,}", "/");
        return path.isEmpty() ? "/" : path;
    }

    /**
     * Ant 风格匹配：{@code **} 匹配任意多级（包括零级），{@code *} 匹配一级中的任意字符
     */
    static boolean antMatch(String pattern, String path) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                // "/**" 也匹配没有后续路径的情况
                if (regex.length() > 0 && regex.charAt(regex.length() - 1) == '/') {
                    regex.setLength(regex.length() - 1);
                    regex.append("(/.*)?");
                } else {
                    regex.append(".*");
                }
                i++;
            } else if (ch == '*') {
                regex.append("[^/]*");
            } else if (ch == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
                regex.append('\\').append(ch);
            } else {
                regex.append(ch);
            }
        }
        return path.matches(regex.toString());
    }

    /**
     * 读取公共配置中的 security.ignore.whites（只取 whites 下的列表项，不引入 YAML 解析依赖）
     */
    private static List<String> readWhitelist(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "未找到公共配置：" + file);
        List<String> result = new ArrayList<>();
        boolean inWhites = false;
        for (String line : Files.readAllLines(file)) {
            if (line.matches("^\\s*whites:\\s*(#.*)?$")) {
                inWhites = true;
                continue;
            }
            if (!inWhites || line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            Matcher item = LIST_ITEM.matcher(line);
            if (!item.matches()) {
                break;
            }
            result.add(item.group(1));
        }
        return result;
    }

    private static String describe(JavaMethod method) {
        return method.getOwner().getSimpleName() + "#" + method.getName();
    }

    /**
     * 迁移脚本（V1 基线及之后的版本）中出现过的权限码。按文本提取，已被后续脚本删除的权限码也会计入，足以发现漏写的按钮菜单
     */
    /**
     * 业务模块（tuoyu-business 下）在单体接入声明中写的路由前缀：{@code tuoyu.monolith.modules.<名称>} 的
     * {@code packages} 中每个包 → {@code prefix}
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> businessModulePrefixes() throws IOException {
        Map<String, String> result = new HashMap<>();
        List<Path> declarations;
        try (Stream<Path> modules = Files.list(repositoryRoot.resolve("tuoyu-business"))) {
            declarations = modules.map(module -> module.resolve(MONOLITH_MODULE)).filter(Files::isRegularFile).sorted().toList();
        }
        for (Path file : declarations) {
            Map<String, Object> root;
            try (InputStream in = Files.newInputStream(file)) {
                root = new Yaml().load(in);
            }
            Object modules = Optional.ofNullable(root)
                .map(r -> r.get("tuoyu")).map(t -> ((Map<String, Object>) t).get("monolith"))
                .map(m -> ((Map<String, Object>) m).get("modules")).orElse(null);
            assertTrue(modules instanceof Map, file + "：没有 tuoyu.monolith.modules");
            for (Map.Entry<String, Object> module : ((Map<String, Object>) modules).entrySet()) {
                Map<String, Object> declared = (Map<String, Object>) module.getValue();
                String where = file + " 中的 " + module.getKey();
                assertTrue(declared.get("prefix") instanceof String, where + "：缺少 prefix");
                assertTrue(declared.get("packages") instanceof List<?> list && !list.isEmpty(), where + "：缺少 packages");
                String prefix = (String) declared.get("prefix");
                for (Object pkg : (List<Object>) declared.get("packages")) {
                    String key = pkg + ".";
                    assertFalse(FRAMEWORK_PREFIXES.containsKey(key), where + "：包 " + pkg + " 属于框架模块");
                    String previous = result.putIfAbsent(key, prefix);
                    assertTrue(previous == null || previous.equals(prefix), where + "：包 " + pkg + " 已声明为前缀 " + previous);
                }
            }
        }
        return result;
    }

    /**
     * 业务模块（tuoyu-business 下）的菜单声明 META-INF/tuoyu/menus/*.json 中的权限码
     */
    private static Set<String> moduleMenuPermissions() throws IOException {
        Set<String> result = new HashSet<>();
        Path business = repositoryRoot.resolve("tuoyu-business");
        if (!Files.isDirectory(business)) {
            return result;
        }
        try (Stream<Path> files = Files.walk(business, 8)) {
            for (Path file : files.filter(p -> p.toString().replace('\\', '/').contains("src/main/resources/META-INF/tuoyu/menus/")
                && p.getFileName().toString().endsWith(".json")).toList()) {
                Matcher matcher = MENU_PERMISSION.matcher(Files.readString(file));
                while (matcher.find()) {
                    result.add(matcher.group(1));
                }
            }
        }
        return result;
    }

    private static Set<String> seededPermissions(Path migrationDir) throws IOException {
        assertTrue(Files.isDirectory(migrationDir), "未找到迁移脚本目录：" + migrationDir);
        Set<String> result = new HashSet<>();
        try (Stream<Path> scripts = Files.list(migrationDir)) {
            for (Path script : scripts.filter(p -> p.getFileName().toString().endsWith(".sql")).toList()) {
                Matcher matcher = PERMISSION_CODE.matcher(Files.readString(script));
                while (matcher.find()) {
                    result.add(matcher.group(1));
                }
            }
        }
        return result;
    }

}
