package org.tuoyu.common.selfcheck;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.ClassUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 启动自检：在全部配置（含 Nacos）加载完成后、创建任何 Bean 之前执行。
 * <ul>
 *     <li>C1、C2 在任何环境未通过都拒绝启动；</li>
 *     <li>P1 ~ P9 在生产模式（激活 prod profile，或 tuoyu.selfcheck.strict=true）未通过时拒绝启动，开发模式只输出警告。</li>
 * </ul>
 * 只能通过 {@value #SKIP_KEY} 按编号跳过个别检查，不提供整体关闭的开关。跳过 P6 时不会请求 Nacos。
 *
 * @author tuoyu
 */
public class SelfCheckEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * 紧跟在配置加载之后执行，此时 Nacos 配置已导入
     */
    public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 1;

    public static final String SKIP_KEY = "tuoyu.selfcheck.skip";

    public static final String STRICT_KEY = "tuoyu.selfcheck.strict";

    static final String SPRINGDOC_CLASS = "org.springdoc.core.properties.SpringDocConfigProperties";

    /**
     * 检查项的展示顺序
     */
    private static final List<String> CHECK_ORDER = List.copyOf(SelfChecks.PASSED_TITLES.keySet());

    private final Log log;

    private final NacosAuthProbe nacosAuthProbe;

    public SelfCheckEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this(logFactory, NacosAuthProbe.HTTP);
    }

    SelfCheckEnvironmentPostProcessor(DeferredLogFactory logFactory, NacosAuthProbe nacosAuthProbe) {
        this.log = logFactory.getLog(SelfCheckEnvironmentPostProcessor.class);
        this.nacosAuthProbe = nacosAuthProbe;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        apply(environment, ClassUtils.isPresent(SPRINGDOC_CLASS, application.getClassLoader()));
    }

    /**
     * 执行自检，springdoc 是否存在由参数传入以便测试
     */
    void apply(ConfigurableEnvironment environment, boolean springDocPresent) {
        boolean production = isProduction(environment);
        Set<String> skipped = skippedIds(environment);
        if (!skipped.isEmpty()) {
            log.warn("启动自检已按 " + SKIP_KEY + " 跳过以下检查：" + skipped);
        }

        List<SelfCheckProblem> runtimeProblems = SelfChecks.runtimeChecks(environment);
        Map<String, String> notApplicable = new HashMap<>();
        if (!springDocPresent) {
            notApplicable.put(SelfChecks.P4, null);
        }
        if (environment.getProperty(SelfChecks.CAPTCHA_ENABLED_KEY) == null) {
            notApplicable.put(SelfChecks.P7, null);
        }
        List<SelfCheckProblem> allProductionProblems = new ArrayList<>(SelfChecks.productionChecks(environment, springDocPresent));
        if (!skipped.contains(SelfChecks.P6)) {
            checkNacosAuth(environment, notApplicable).ifPresent(allProductionProblems::add);
        }
        allProductionProblems.sort(Comparator.comparingInt(p -> CHECK_ORDER.indexOf(p.id())));
        SelfCheckReport.record(report(production, skipped, notApplicable, runtimeProblems, allProductionProblems));

        List<SelfCheckProblem> blocking = new ArrayList<>(withoutSkipped(runtimeProblems, skipped));
        List<SelfCheckProblem> productionProblems = withoutSkipped(allProductionProblems, skipped);
        if (production) {
            blocking.addAll(productionProblems);
        }
        if (!blocking.isEmpty()) {
            throw new SelfCheckFailedException(production, blocking);
        }

        if (productionProblems.isEmpty()) {
            log.info("启动自检通过（" + (production ? "生产模式" : "开发模式") + "）");
        } else {
            log.warn("启动自检通过（开发模式），但以下问题在生产环境会导致拒绝启动：" + productionProblems.stream()
                .map(p -> "\n  [" + p.id() + "] " + p.title() + (p.details().isEmpty() ? "" : "：" + String.join("，", p.details())))
                .collect(Collectors.joining()));
        }
    }

    /**
     * P6：公共配置来自 Nacos 时，匿名请求一次 Nacos 判断是否开启了鉴权。无法判断时记为不适用并警告，不阻止启动
     */
    private Optional<SelfCheckProblem> checkNacosAuth(ConfigurableEnvironment environment, Map<String, String> notApplicable) {
        Optional<NacosAuthProbe.Target> target = SelfChecks.nacosProbeTarget(environment, SelfChecks.effectiveRawValues(environment));
        if (target.isEmpty()) {
            notApplicable.put(SelfChecks.P6, null);
            return Optional.empty();
        }
        NacosAuthProbe.Result result = nacosAuthProbe.probe(target.get());
        if (result.state() == NacosAuthProbe.State.UNKNOWN) {
            String title = "无法判断 Nacos 是否开启鉴权（" + result.detail() + "），请人工确认";
            log.warn("启动自检 [" + SelfChecks.P6 + "] " + title);
            notApplicable.put(SelfChecks.P6, title);
            return Optional.empty();
        }
        return SelfChecks.checkNacosAuth(result);
    }

    /**
     * 汇总每一项检查的结果（包括通过、跳过和不适用的项）
     *
     * @param notApplicable 本服务不涉及的检查项及其说明（说明为 null 时使用检查项本身的说明）
     */
    static SelfCheckReport report(boolean production, Set<String> skipped, Map<String, String> notApplicable,
                                  List<SelfCheckProblem> runtimeProblems, List<SelfCheckProblem> productionProblems) {
        Map<String, SelfCheckProblem> problems = new HashMap<>();
        runtimeProblems.forEach(p -> problems.put(p.id(), p));
        productionProblems.forEach(p -> problems.put(p.id(), p));
        List<SelfCheckReport.Result> results = new ArrayList<>();
        SelfChecks.PASSED_TITLES.forEach((id, passedTitle) -> {
            SelfCheckReport.Scope scope = SelfChecks.RUNTIME_IDS.contains(id)
                ? SelfCheckReport.Scope.RUNTIME : SelfCheckReport.Scope.PRODUCTION;
            SelfCheckProblem problem = problems.get(id);
            if (skipped.contains(id)) {
                results.add(new SelfCheckReport.Result(id, passedTitle, scope, SelfCheckReport.Status.SKIPPED, null, null));
            } else if (problem != null) {
                results.add(new SelfCheckReport.Result(id, problem.title(), scope, SelfCheckReport.Status.FAILED,
                    problem.details(), problem.action()));
            } else if (notApplicable.containsKey(id)) {
                String title = notApplicable.get(id) == null ? passedTitle : notApplicable.get(id);
                results.add(new SelfCheckReport.Result(id, title, scope, SelfCheckReport.Status.NOT_APPLICABLE, null, null));
            } else {
                results.add(new SelfCheckReport.Result(id, passedTitle, scope, SelfCheckReport.Status.PASSED, null, null));
            }
        });
        return new SelfCheckReport(Instant.now(), production, results);
    }

    /**
     * 是否按生产模式检查：prod profile，或 tuoyu.selfcheck.strict=true。其他模块的启动检查也按这个口径
     */
    public static boolean isProduction(ConfigurableEnvironment environment) {
        return environment.acceptsProfiles(Profiles.of("prod"))
            || "true".equalsIgnoreCase(environment.getProperty(STRICT_KEY));
    }

    static Set<String> skippedIds(ConfigurableEnvironment environment) {
        String value = environment.getProperty(SKIP_KEY, "");
        return Arrays.stream(value.split(","))
            .map(id -> id.trim().toUpperCase(Locale.ROOT))
            .filter(id -> !id.isEmpty())
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static List<SelfCheckProblem> withoutSkipped(List<SelfCheckProblem> problems, Set<String> skipped) {
        return problems.stream().filter(p -> !skipped.contains(p.id())).toList();
    }

}
