package org.tuoyu.system.domain.vo;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页概况。除版本与部署信息外，各部分只返回给有相应权限的用户，没有权限时为空
 *
 * @param version          版本号；构建信息不存在（例如在 IDE 中运行）时为空
 * @param profile          运行环境（激活的 profile）
 * @param deploymentMode   部署方式：microservice 或 monolith
 * @param tenantEnabled    是否开启多租户
 * @param services         各服务的实例数（需要 monitor:security:list）
 * @param security         安全自检摘要（需要 monitor:security:list）
 * @param onlineUsers      在线用户数（需要 monitor:online:list）
 * @param loginStats       今日登录日志统计（需要 monitor:logininfor:list）
 * @param operationStats   今日操作日志统计（需要 monitor:operlog:list）
 * @param recentOperations 最近的操作（需要 monitor:operlog:list）
 * @author tuoyu
 */
public record HomeOverviewVo(
    String version,
    String profile,
    String deploymentMode,
    boolean tenantEnabled,
    List<ServiceStatus> services,
    SecuritySummary security,
    Long onlineUsers,
    DailyStats loginStats,
    DailyStats operationStats,
    List<RecentOperation> recentOperations
) {

    /**
     * 服务状态
     *
     * @param name      服务名
     * @param instances 注册中心中健康的实例数，0 表示没有可用实例
     */
    public record ServiceStatus(String name, int instances) {
    }

    /**
     * 安全自检摘要
     *
     * @param production         是否按生产模式检查
     * @param productionProblems 生产检查中未通过的项数
     * @param checkedAt          自检时间
     */
    public record SecuritySummary(boolean production, long productionProblems, Instant checkedAt) {
    }

    /**
     * 今日统计
     *
     * @param total    记录总数。登录日志中包含登录、退出、注册的记录
     * @param failures 失败数（登录失败 / 操作异常）
     */
    public record DailyStats(long total, long failures) {
    }

    /**
     * 最近的一条操作
     */
    public record RecentOperation(
        Long operId,
        String title,
        Integer businessType,
        String operName,
        String operIp,
        Long costTime,
        Integer status,
        LocalDateTime operTime
    ) {
    }

}
