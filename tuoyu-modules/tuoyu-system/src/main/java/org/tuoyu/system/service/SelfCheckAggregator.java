package org.tuoyu.system.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.tuoyu.common.redis.selfcheck.SelfCheckReportStore;
import org.tuoyu.common.selfcheck.SelfCheckReport;
import org.tuoyu.system.domain.vo.SecurityOverviewVo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 汇总各服务的启动自检结果，供安全中心与首页使用。
 * <p>
 * 本服务取本进程的结果；其他服务（{@code tuoyu.home.services}）取它们启动时写入 Redis 的结果（{@link SelfCheckReportStore}）。
 * 有些检查只在部分服务中适用（例如 P7 只有 tuoyu-auth 配置了验证码开关），所以同一检查项取最需要关注的结果：
 * 未通过 &gt; 已跳过 &gt; 通过 &gt; 不适用，并列出得出该结果的服务。
 *
 * @author tuoyu
 */
@Service
public class SelfCheckAggregator {

    /**
     * 结果的关注程度，越靠前越需要关注
     */
    private static final List<SelfCheckReport.Status> PRIORITY = List.of(SelfCheckReport.Status.FAILED,
        SelfCheckReport.Status.SKIPPED, SelfCheckReport.Status.PASSED, SelfCheckReport.Status.NOT_APPLICABLE);

    private final Environment environment;

    private final Supplier<Optional<SelfCheckReport>> localReport;

    private final Function<String, Optional<SelfCheckReport>> publishedReport;

    @Autowired
    public SelfCheckAggregator(Environment environment, ObjectProvider<SelfCheckReportStore> store) {
        this(environment, SelfCheckReport::latest, service -> {
            SelfCheckReportStore available = store.getIfAvailable();
            return available == null ? Optional.empty() : available.read(service);
        });
    }

    SelfCheckAggregator(Environment environment, Supplier<Optional<SelfCheckReport>> localReport,
                        Function<String, Optional<SelfCheckReport>> publishedReport) {
        this.environment = environment;
        this.localReport = localReport;
        this.publishedReport = publishedReport;
    }

    /**
     * 汇总结果。本服务排在第一个；没有写入过结果的服务（未启动过、Redis 不可用）不在其中。
     * 单体中全部模块在同一个进程、读取同一份配置，只取本进程的结果（Redis 中可能还有以前按微服务运行时写入的结果）
     */
    public Aggregate aggregate() {
        Map<String, SelfCheckReport> reports = new LinkedHashMap<>();
        String local = environment.getProperty("spring.application.name", "");
        localReport.get().ifPresent(report -> reports.put(local, report));
        if ("monolith".equals(environment.getProperty("tuoyu.deployment.mode"))) {
            return merge(reports);
        }
        for (String service : HomeOverviewService.serviceNames(environment)) {
            if (!reports.containsKey(service)) {
                publishedReport.apply(service).ifPresent(report -> reports.put(service, report));
            }
        }
        return merge(reports);
    }

    static Aggregate merge(Map<String, SelfCheckReport> reports) {
        List<SecurityOverviewVo.Source> sources = reports.entrySet().stream()
            .map(e -> new SecurityOverviewVo.Source(e.getKey(), e.getValue().checkedAt(), e.getValue().production()))
            .toList();
        // 按检查编号分组，保持各服务报告中的顺序（编号相同、顺序相同；旧版本服务缺少的编号排在后面）
        Map<String, List<Map.Entry<String, SelfCheckReport.Result>>> byId = new LinkedHashMap<>();
        reports.forEach((service, report) -> report.results().forEach(result ->
            byId.computeIfAbsent(result.id(), id -> new ArrayList<>()).add(Map.entry(service, result))));
        List<SecurityOverviewVo.Check> checks = byId.values().stream().map(SelfCheckAggregator::mergeOne).toList();
        long problems = checks.stream()
            .filter(c -> c.scope() == SelfCheckReport.Scope.PRODUCTION && c.status() == SelfCheckReport.Status.FAILED)
            .count();
        boolean production = sources.stream().anyMatch(SecurityOverviewVo.Source::production);
        return new Aggregate(production, problems, sources, checks);
    }

    private static SecurityOverviewVo.Check mergeOne(List<Map.Entry<String, SelfCheckReport.Result>> entries) {
        SelfCheckReport.Status status = entries.stream()
            .map(e -> e.getValue().status())
            .min(Comparator.comparingInt(PRIORITY::indexOf))
            .orElseThrow();
        List<Map.Entry<String, SelfCheckReport.Result>> matching = entries.stream()
            .filter(e -> e.getValue().status() == status)
            .toList();
        SelfCheckReport.Result first = matching.getFirst().getValue();
        List<String> details = matching.stream().flatMap(e -> e.getValue().details().stream()).distinct().toList();
        String action = matching.stream().map(e -> e.getValue().action()).filter(Objects::nonNull).findFirst().orElse(null);
        List<String> services = matching.stream().map(Map.Entry::getKey).toList();
        return new SecurityOverviewVo.Check(first.id(), first.title(), first.scope(), status, details, action, services);
    }

    /**
     * 汇总结果
     *
     * @param production         是否有服务按生产模式检查
     * @param productionProblems 生产检查中未通过的项数（同一项在多个服务中未通过只算一次）
     * @param sources            参与汇总的服务，本服务排在第一个
     * @param checks             每一项检查的汇总结果
     */
    public record Aggregate(boolean production, long productionProblems, List<SecurityOverviewVo.Source> sources,
                            List<SecurityOverviewVo.Check> checks) {
    }

}
