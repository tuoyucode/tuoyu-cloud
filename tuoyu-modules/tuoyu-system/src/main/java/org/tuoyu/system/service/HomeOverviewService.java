package org.tuoyu.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.system.domain.SysLoginInfo;
import org.tuoyu.system.domain.SysOperLog;
import org.tuoyu.system.domain.vo.HomeOverviewVo;
import org.tuoyu.system.mapper.SysLoginInfoMapper;
import org.tuoyu.system.mapper.SysOperLogMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * 首页概况
 *
 * @author tuoyu
 */
@Service
public class HomeOverviewService {

    /**
     * 首页展示的服务，可用 tuoyu.home.services 调整。只列出本项目的服务：注册中心可能与其他项目共用
     */
    static final List<String> DEFAULT_SERVICES = List.of(
        "tuoyu-gateway", "tuoyu-auth", "tuoyu-system", "tuoyu-resource", "tuoyu-workflow", "tuoyu-gen", "tuoyu-job");

    private static final int RECENT_OPERATIONS = 5;

    private final Environment environment;

    private final ObjectProvider<BuildProperties> buildProperties;

    private final ObjectProvider<DiscoveryClient> discoveryClient;

    private final SysLoginInfoMapper loginInfoMapper;

    private final SysOperLogMapper operLogMapper;

    private final SelfCheckAggregator selfCheckAggregator;

    private final Clock clock;

    /**
     * 判断当前用户是否有某个权限；单独抽出，便于测试
     */
    private final Predicate<String> permissionChecker;

    @Autowired
    public HomeOverviewService(Environment environment, ObjectProvider<BuildProperties> buildProperties,
                               ObjectProvider<DiscoveryClient> discoveryClient,
                               SysLoginInfoMapper loginInfoMapper, SysOperLogMapper operLogMapper,
                               SelfCheckAggregator selfCheckAggregator) {
        this(environment, buildProperties, discoveryClient, loginInfoMapper, operLogMapper, selfCheckAggregator,
            Clock.systemDefaultZone(), StpUtil::hasPermission);
    }

    HomeOverviewService(Environment environment, ObjectProvider<BuildProperties> buildProperties,
                        ObjectProvider<DiscoveryClient> discoveryClient,
                        SysLoginInfoMapper loginInfoMapper, SysOperLogMapper operLogMapper,
                        SelfCheckAggregator selfCheckAggregator, Clock clock, Predicate<String> permissionChecker) {
        this.environment = environment;
        this.buildProperties = buildProperties;
        this.discoveryClient = discoveryClient;
        this.loginInfoMapper = loginInfoMapper;
        this.operLogMapper = operLogMapper;
        this.selfCheckAggregator = selfCheckAggregator;
        this.clock = clock;
        this.permissionChecker = permissionChecker;
    }

    public HomeOverviewVo overview() {
        BuildProperties build = buildProperties.getIfAvailable();
        boolean security = permissionChecker.test("monitor:security:list");
        boolean operations = permissionChecker.test("monitor:operlog:list");
        LocalDateTime todayStart = LocalDate.now(clock).atStartOfDay();
        return new HomeOverviewVo(
            build == null ? null : build.getVersion(),
            String.join(",", environment.getActiveProfiles()),
            environment.getProperty("tuoyu.deployment.mode", "microservice"),
            environment.getProperty("tuoyu.tenant.enabled", Boolean.class, false),
            security ? services() : null,
            security ? securitySummary() : null,
            permissionChecker.test("monitor:online:list") ? onlineUsers() : null,
            permissionChecker.test("monitor:logininfor:list") ? loginStats(todayStart) : null,
            operations ? operationStats(todayStart) : null,
            operations ? recentOperations() : null);
    }

    /**
     * 本项目的服务名（tuoyu.home.services，未配置时为 {@link #DEFAULT_SERVICES}），安全中心也按它汇总各服务的自检结果
     */
    static List<String> serviceNames(Environment environment) {
        return Binder.get(environment).bind("tuoyu.home.services", Bindable.listOf(String.class))
            .orElse(DEFAULT_SERVICES);
    }

    List<HomeOverviewVo.ServiceStatus> services() {
        List<String> names = serviceNames(environment);
        DiscoveryClient client = discoveryClient.getIfAvailable();
        return names.stream()
            .map(name -> new HomeOverviewVo.ServiceStatus(name, client == null ? 0 : client.getInstances(name).size()))
            .toList();
    }

    /**
     * 自检摘要：与安全中心相同，汇总各服务的结果；检查时间取本服务的
     */
    private HomeOverviewVo.SecuritySummary securitySummary() {
        SelfCheckAggregator.Aggregate aggregate = selfCheckAggregator.aggregate();
        if (aggregate.sources().isEmpty()) {
            return null;
        }
        return new HomeOverviewVo.SecuritySummary(aggregate.production(), aggregate.productionProblems(),
            aggregate.sources().getFirst().checkedAt());
    }

    /**
     * 在线用户数：与“在线用户”页面相同，按未过期的登录令牌统计
     */
    private Long onlineUsers() {
        Collection<String> keys = RedisUtils.keys(CacheNames.ONLINE_TOKEN_KEY + "*");
        return keys.stream()
            .map(key -> StringUtils.substringAfterLast(key, StringUtils.COLON))
            .filter(token -> StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) >= -1)
            .count();
    }

    HomeOverviewVo.DailyStats loginStats(LocalDateTime todayStart) {
        long total = loginInfoMapper.selectCount(Wrappers.lambdaQuery(SysLoginInfo.class)
            .ge(SysLoginInfo::getLoginTime, todayStart));
        long failures = loginInfoMapper.selectCount(Wrappers.lambdaQuery(SysLoginInfo.class)
            .ge(SysLoginInfo::getLoginTime, todayStart)
            .eq(SysLoginInfo::getStatus, Constants.FAIL));
        return new HomeOverviewVo.DailyStats(total, failures);
    }

    HomeOverviewVo.DailyStats operationStats(LocalDateTime todayStart) {
        long total = operLogMapper.selectCount(Wrappers.lambdaQuery(SysOperLog.class)
            .ge(SysOperLog::getOperTime, todayStart));
        // 操作日志的状态：0 正常，1 异常（见 BusinessStatus）
        long failures = operLogMapper.selectCount(Wrappers.lambdaQuery(SysOperLog.class)
            .ge(SysOperLog::getOperTime, todayStart)
            .eq(SysOperLog::getStatus, 1));
        return new HomeOverviewVo.DailyStats(total, failures);
    }

    private List<HomeOverviewVo.RecentOperation> recentOperations() {
        // 用分页取前几条，由分页插件按数据库方言生成语句（Oracle 不支持 limit）
        return operLogMapper.selectPage(new Page<>(1, RECENT_OPERATIONS, false), Wrappers.lambdaQuery(SysOperLog.class)
                .select(SysOperLog::getOperId, SysOperLog::getTitle, SysOperLog::getBusinessType,
                    SysOperLog::getOperName, SysOperLog::getOperIp, SysOperLog::getCostTime,
                    SysOperLog::getStatus, SysOperLog::getOperTime)
                .orderByDesc(SysOperLog::getOperId))
            .getRecords()
            .stream()
            .map(log -> new HomeOverviewVo.RecentOperation(log.getOperId(), log.getTitle(), log.getBusinessType(),
                log.getOperName(), log.getOperIp(), log.getCostTime(), log.getStatus(), log.getOperTime()))
            .toList();
    }

}
