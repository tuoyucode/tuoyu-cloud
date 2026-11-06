package org.tuoyu.system.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.tuoyu.common.selfcheck.SelfCheckReport;
import org.tuoyu.common.selfcheck.SelfCheckReport.Result;
import org.tuoyu.common.selfcheck.SelfCheckReport.Scope;
import org.tuoyu.common.selfcheck.SelfCheckReport.Status;
import org.tuoyu.system.domain.vo.SecurityOverviewVo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("安全中心：汇总各服务的启动自检结果")
class SelfCheckAggregatorTest {

    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");

    private static Result passed(String id) {
        return new Result(id, id + " 通过", Scope.PRODUCTION, Status.PASSED, null, null);
    }

    private static Result notApplicable(String id) {
        return new Result(id, id + " 说明", Scope.PRODUCTION, Status.NOT_APPLICABLE, null, null);
    }

    private static Result failed(String id, String detail) {
        return new Result(id, id + " 未通过", Scope.PRODUCTION, Status.FAILED, List.of(detail), id + " 的处理办法");
    }

    private static SelfCheckReport report(Result... results) {
        return new SelfCheckReport(NOW, false, List.of(results));
    }

    private static SecurityOverviewVo.Check check(SelfCheckAggregator.Aggregate aggregate, String id) {
        return aggregate.checks().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("只在部分服务中适用的检查（P7 只有 tuoyu-auth 有验证码开关）取适用的结果，并列出来源服务")
    void notApplicableYieldsToOtherServices() {
        SelfCheckAggregator.Aggregate aggregate = SelfCheckAggregator.merge(Map.of(
            "tuoyu-system", report(passed("P2"), notApplicable("P7")),
            "tuoyu-auth", report(passed("P2"), failed("P7", "security.captcha.enabled=false"))));

        SecurityOverviewVo.Check p7 = check(aggregate, "P7");
        assertEquals(Status.FAILED, p7.status());
        assertEquals(List.of("tuoyu-auth"), p7.services());
        assertEquals(List.of("security.captcha.enabled=false"), p7.details());
        assertEquals("P7 的处理办法", p7.action());
        assertEquals(1, aggregate.productionProblems());
    }

    @Test
    @DisplayName("多个服务未通过同一项：只算一个问题，条目去重合并，列出全部服务")
    void sameProblemInSeveralServices() {
        SelfCheckAggregator.Aggregate aggregate = SelfCheckAggregator.merge(Map.of(
            "tuoyu-system", report(failed("P2", "management.endpoints.web.exposure.include=*")),
            "tuoyu-auth", report(failed("P2", "management.endpoints.web.exposure.include=*"))));

        SecurityOverviewVo.Check p2 = check(aggregate, "P2");
        assertEquals(List.of("management.endpoints.web.exposure.include=*"), p2.details());
        assertEquals(2, p2.services().size());
        assertEquals(1, aggregate.productionProblems());
    }

    @Test
    @DisplayName("跳过比通过更需要关注；都不适用时为不适用")
    void priorityOfStatuses() {
        Result skipped = new Result("P4", "P4 通过", Scope.PRODUCTION, Status.SKIPPED, null, null);
        SelfCheckAggregator.Aggregate aggregate = SelfCheckAggregator.merge(Map.of(
            "tuoyu-system", report(passed("P4"), notApplicable("P6")),
            "tuoyu-gen", report(skipped, notApplicable("P6"))));

        assertEquals(Status.SKIPPED, check(aggregate, "P4").status());
        assertEquals(List.of("tuoyu-gen"), check(aggregate, "P4").services());
        assertEquals(Status.NOT_APPLICABLE, check(aggregate, "P6").status());
        assertEquals(0, aggregate.productionProblems());
    }

    @Test
    @DisplayName("微服务：本服务排第一，其他服务从 Redis 读取，没有结果的服务不参与")
    void microserviceReadsPublishedReports() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.application.name", "tuoyu-system")
            .withProperty("tuoyu.home.services[0]", "tuoyu-auth")
            .withProperty("tuoyu.home.services[1]", "tuoyu-system")
            .withProperty("tuoyu.home.services[2]", "tuoyu-gen");
        List<String> read = new ArrayList<>();
        SelfCheckAggregator aggregator = new SelfCheckAggregator(environment, () -> Optional.of(report(passed("P2"))),
            service -> {
                read.add(service);
                return "tuoyu-auth".equals(service) ? Optional.of(report(passed("P2"))) : Optional.empty();
            });

        SelfCheckAggregator.Aggregate aggregate = aggregator.aggregate();

        assertEquals(List.of("tuoyu-system", "tuoyu-auth"),
            aggregate.sources().stream().map(SecurityOverviewVo.Source::service).toList());
        assertEquals(List.of("tuoyu-auth", "tuoyu-gen"), read, "本服务取本进程的结果，不读 Redis");
        assertFalse(aggregate.production());
    }

    @Test
    @DisplayName("单体只取本进程的结果，不读 Redis 中以前按微服务运行时写入的结果")
    void monolithUsesLocalReportOnly() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.application.name", "tuoyu-boot-all")
            .withProperty("tuoyu.deployment.mode", "monolith");
        List<String> read = new ArrayList<>();
        SelfCheckAggregator aggregator = new SelfCheckAggregator(environment, () -> Optional.of(report(passed("P7"))),
            service -> {
                read.add(service);
                return Optional.of(report(failed("P7", "stale")));
            });

        SelfCheckAggregator.Aggregate aggregate = aggregator.aggregate();

        assertTrue(read.isEmpty());
        assertEquals(Status.PASSED, check(aggregate, "P7").status());
        assertEquals(List.of("tuoyu-boot-all"), check(aggregate, "P7").services());
    }

}
