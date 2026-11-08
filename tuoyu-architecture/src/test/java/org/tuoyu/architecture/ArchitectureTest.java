package org.tuoyu.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 模块之间的依赖规则：跨模块调用只经由对方的 api 包，业务代码不使用 Dubbo，mybatis 层不依赖认证与 RPC，
 * 当前用户统一经由 UserContext 读取。
 * <p>
 * 读取各模块编译后的类（target/classes），所以本模块在构建顺序的最后。
 */
@DisplayName("依赖规则（ArchUnit）")
class ArchitectureTest {

    /**
     * 业务模块的根包
     */
    private static final List<String> MODULES = List.of("auth", "system", "resource", "workflow", "gen", "job");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() throws IOException {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("tuoyu-framework"))) {
            root = root.getParent();
        }
        assertNotNull(root, "未找到仓库根目录");
        List<Path> outputs;
        try (Stream<Path> dirs = Files.walk(root, 4)) {
            outputs = dirs.filter(p -> p.endsWith(Path.of("target", "classes")) && Files.isDirectory(p))
                .filter(p -> !p.toString().contains("tuoyu-extensions") && !p.toString().contains("tuoyu-architecture"))
                .toList();
        }
        assertFalse(outputs.isEmpty(), "没有找到各模块编译后的类，请先完整构建（mvn clean verify）");
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPaths(outputs);
    }

    @Test
    @DisplayName("业务模块与 tuoyu-api 不使用 Dubbo（跨模块调用经 tuoyu-common-rpc）")
    void modulesDoNotUseDubbo() {
        ArchRule rule = noClasses().that().resideInAnyPackage(modulePackages())
            .should().dependOnClassesThat().resideInAPackage("org.apache.dubbo..")
            .because("业务代码不能出现 Dubbo，导出与引用由 tuoyu-common-dubbo 统一处理");
        rule.check(classes);
    }

    @Test
    @DisplayName("模块之间只能通过对方的 api 包访问")
    void modulesOnlyDependOnApis() {
        for (String module : MODULES) {
            for (String other : MODULES) {
                if (module.equals(other)) {
                    continue;
                }
                noClasses().that().resideInAPackage("org.tuoyu." + module + "..")
                    .and().resideOutsideOfPackage("org.tuoyu." + module + ".api..")
                    .should().dependOnClassesThat(implementationOf(other))
                    .because(module + " 只能通过 org.tuoyu." + other + ".api 调用 " + other)
                    .check(classes);
            }
        }
    }

    @Test
    @DisplayName("api 包中的接口都是带 @RemoteApi 的跨模块契约，api 包中没有 Spring Bean")
    void apiPackagesContainOnlyContracts() {
        classes().that().resideInAnyPackage(apiRootPackages()).and().areInterfaces()
            .should().beAnnotatedWith("org.tuoyu.common.rpc.annotation.RemoteApi")
            .check(classes);
        noClasses().that().resideInAnyPackage(apiPackages())
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
            .check(classes);
    }

    @Test
    @DisplayName("tuoyu-common-mybatis 不依赖认证框架、RPC 与业务 api")
    void mybatisDependsOnlyOnCore() {
        noClasses().that().resideInAPackage("org.tuoyu.common.mybatis..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "cn.dev33..", "org.tuoyu.common.satoken..", "org.tuoyu.common.security..",
                "org.tuoyu.common.rpc..", "org.apache.dubbo..", "org.tuoyu.system..")
            .because("ORM 层只依赖 core 中的上下文接口")
            .check(classes);
    }

    @Test
    @DisplayName("tuoyu-common-rpc 与 core 不依赖 Dubbo；框架中只有 RPC 实现相关的模块使用 Dubbo")
    void onlyRpcImplementationUsesDubbo() {
        noClasses().that().resideInAnyPackage("org.tuoyu.common..")
            .and().resideOutsideOfPackages("org.tuoyu.common.dubbo..", "org.tuoyu.common.loadbalance..",
                "org.tuoyu.common.seata..")
            .should().dependOnClassesThat().resideInAPackage("org.apache.dubbo..")
            .because("单体包中不包含 Dubbo")
            .check(classes);
    }

    @Test
    @DisplayName("LoginHelper 只在 tuoyu-common-satoken 与 tuoyu-auth 中使用，其他代码通过 UserContext 取当前用户")
    void loginHelperStaysInAuthentication() {
        noClasses().that().resideOutsideOfPackages("org.tuoyu.common.satoken..", "org.tuoyu.auth..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.tuoyu.common.satoken.utils.LoginHelper")
            .because("LoginHelper 处理登录、登出与会话，业务与其他框架代码读取当前用户用 core 的 UserContext")
            .check(classes);
    }

    private static String[] modulePackages() {
        return MODULES.stream().map(m -> "org.tuoyu." + m + "..").toArray(String[]::new);
    }

    private static String[] apiRootPackages() {
        return MODULES.stream().map(m -> "org.tuoyu." + m + ".api").toArray(String[]::new);
    }

    private static String[] apiPackages() {
        return MODULES.stream().map(m -> "org.tuoyu." + m + ".api..").toArray(String[]::new);
    }

    private static com.tngtech.archunit.base.DescribedPredicate<JavaClass> implementationOf(String module) {
        return com.tngtech.archunit.base.DescribedPredicate.describe("org.tuoyu." + module + " 的实现类（api 包以外）",
            c -> c.getPackageName().startsWith("org.tuoyu." + module + ".")
                && !c.getPackageName().startsWith("org.tuoyu." + module + ".api")
                || c.getPackageName().equals("org.tuoyu." + module));
    }

}
