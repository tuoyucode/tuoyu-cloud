package org.tuoyu.common.selfcheck;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 最近一次启动自检的完整结果，包括通过的检查，供安全中心等页面展示。
 * <p>
 * 自检在创建 Bean 之前执行，所以结果保存在静态字段中；同一进程只有一个应用时，取到的就是本服务的结果。
 *
 * @param checkedAt  检查时间
 * @param production 是否按生产模式检查（生产检查未通过会拒绝启动）
 * @param results    每一项检查的结果，顺序为 C1、C2、P1 ~ P9
 * @author tuoyu
 */
public record SelfCheckReport(Instant checkedAt, boolean production, List<Result> results) {

    private static volatile SelfCheckReport latest;

    public SelfCheckReport {
        results = List.copyOf(results);
    }

    /**
     * 最近一次自检结果；本进程没有执行过自检时为空
     */
    public static Optional<SelfCheckReport> latest() {
        return Optional.ofNullable(latest);
    }

    static void record(SelfCheckReport report) {
        latest = report;
    }

    /**
     * 生产检查中未通过的项数（开发模式下这些项只是警告）
     */
    public long productionProblemCount() {
        return results.stream()
            .filter(result -> result.scope() == Scope.PRODUCTION && result.status() == Status.FAILED)
            .count();
    }

    /**
     * 检查范围
     */
    public enum Scope {
        /**
         * 任何环境都检查，未通过即拒绝启动
         */
        RUNTIME,
        /**
         * 生产检查：生产模式下未通过即拒绝启动，开发模式下只警告
         */
        PRODUCTION
    }

    /**
     * 检查结果
     */
    public enum Status {
        PASSED,
        FAILED,
        /**
         * 按 tuoyu.selfcheck.skip 跳过
         */
        SKIPPED,
        /**
         * 本服务不涉及，例如没有接口文档组件时的 P4、不从 Nacos 加载配置时的 P6；也用于无法判断的 P6（标题中说明原因）
         */
        NOT_APPLICABLE
    }

    /**
     * 一项检查的结果
     *
     * @param id      检查编号
     * @param title   通过时为检查项说明，未通过时为问题概述
     * @param scope   检查范围
     * @param status  结果
     * @param details 未通过时的具体条目（配置项名等，不包含敏感值）
     * @param action  未通过时的处理办法
     */
    public record Result(String id, String title, Scope scope, Status status, List<String> details, String action) {

        public Result {
            details = details == null ? List.of() : List.copyOf(details);
        }
    }

}
