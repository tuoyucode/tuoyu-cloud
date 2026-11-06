package org.tuoyu.common.redis.selfcheck;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.tuoyu.common.selfcheck.SelfCheckReport;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("启动自检结果在 Redis 中的存取：按服务名、不分租户，失败时不影响服务")
class SelfCheckReportStoreTest {

    @BeforeAll
    static void initSpring() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    private static SelfCheckReport report() {
        return new SelfCheckReport(Instant.parse("2026-11-03T08:00:00Z"), false, List.of(
            new SelfCheckReport.Result("P7", "登录图形验证码已关闭", SelfCheckReport.Scope.PRODUCTION,
                SelfCheckReport.Status.FAILED, List.of("security.captcha.enabled=false"), "改回 true"),
            new SelfCheckReport.Result("P8", "强度不低于 10", SelfCheckReport.Scope.PRODUCTION,
                SelfCheckReport.Status.PASSED, null, null)));
    }

    @Test
    @DisplayName("写入后按服务名读回相同的结果，键带 global: 前缀")
    void roundTrip() {
        Map<String, String> redis = new HashMap<>();
        SelfCheckReportStore store = new SelfCheckReportStore(redis::put, redis::get);

        store.publish("tuoyu-auth", report());

        assertEquals(List.of("global:selfcheck:tuoyu-auth"), List.copyOf(redis.keySet()));
        assertEquals(report(), store.read("tuoyu-auth").orElseThrow());
        assertTrue(store.read("tuoyu-gen").isEmpty());
    }

    @Test
    @DisplayName("Redis 不可用或内容无法解析时：写入只记日志，读取为空")
    void failuresAreIgnored() {
        SelfCheckReportStore broken = new SelfCheckReportStore(
            (key, json) -> {
                throw new IllegalStateException("redis down");
            },
            key -> {
                throw new IllegalStateException("redis down");
            });
        broken.publish("tuoyu-auth", report());
        assertTrue(broken.read("tuoyu-auth").isEmpty());

        SelfCheckReportStore garbage = new SelfCheckReportStore((key, json) -> {
        }, key -> "not json");
        assertTrue(garbage.read("tuoyu-auth").isEmpty());
    }

}
