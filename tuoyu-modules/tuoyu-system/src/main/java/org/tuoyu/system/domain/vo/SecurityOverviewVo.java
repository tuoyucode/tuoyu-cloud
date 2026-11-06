package org.tuoyu.system.domain.vo;

import org.tuoyu.common.selfcheck.SelfCheckReport;

import java.time.Instant;
import java.util.List;

/**
 * 安全中心：各服务启动自检的汇总结果与当前生效的运行时防护配置
 *
 * @param production         是否有服务按生产模式检查
 * @param productionProblems 生产检查中未通过的项数（同一项在多个服务中未通过只算一次）
 * @param sources            参与汇总的服务（本服务排在第一个）；为空表示没有任何自检结果
 * @param checks             每一项检查的汇总结果（C1、C2、P1 ~ P9）
 * @param protections        本服务（tuoyu-system）的运行时防护配置
 * @author tuoyu
 */
public record SecurityOverviewVo(
    boolean production,
    long productionProblems,
    List<Source> sources,
    List<Check> checks,
    Protections protections
) {

    /**
     * 参与汇总的一个服务
     *
     * @param service    服务名
     * @param checkedAt  该服务最近一次启动时的自检时间
     * @param production 是否按生产模式检查
     */
    public record Source(String service, Instant checkedAt, boolean production) {
    }

    /**
     * 一项检查的汇总结果：取各服务中最需要关注的结果（未通过 &gt; 已跳过 &gt; 通过 &gt; 不适用）
     *
     * @param id       检查编号
     * @param title    通过时为检查项说明，未通过时为问题概述
     * @param scope    检查范围
     * @param status   汇总结果
     * @param details  未通过时的具体条目（配置项名等，不包含敏感值），多个服务的条目去重合并
     * @param action   未通过时的处理办法
     * @param services 得出该结果的服务
     */
    public record Check(String id, String title, SelfCheckReport.Scope scope, SelfCheckReport.Status status,
                        List<String> details, String action, List<String> services) {
    }

    /**
     * 运行时防护配置
     *
     * @param requestMaxBodySize   非文件请求的请求体上限
     * @param uploadMaxFileSize    单个上传文件上限
     * @param uploadMaxRequestSize 整个上传请求上限
     * @param formMaxSize          表单提交上限
     * @param maxThreads           最大并发
     * @param pageMaxSize          每页记录数上限
     * @param excelMaxExportRows   Excel 导出行数上限
     * @param excelMaxImportRows   Excel 导入行数上限
     * @param redisAllowlist       Redis 反序列化白名单
     * @param apiCrypto            接口加密
     * @param tenantEnabled        是否开启多租户
     * @param trustedProxies       可信代理网段（规范写法）；为空表示不信任任何代理，忽略全部转发头
     */
    public record Protections(
        String requestMaxBodySize,
        String uploadMaxFileSize,
        String uploadMaxRequestSize,
        String formMaxSize,
        Integer maxThreads,
        Integer pageMaxSize,
        Integer excelMaxExportRows,
        Integer excelMaxImportRows,
        RedisAllowlist redisAllowlist,
        ApiCrypto apiCrypto,
        boolean tenantEnabled,
        List<String> trustedProxies
    ) {
    }

    /**
     * Redis 反序列化白名单（始终启用，不能关闭）
     *
     * @param extraPrefixes 业务扩展的类型前缀
     * @param rejected      本进程启动以来拒绝的次数
     */
    public record RedisAllowlist(List<String> extraPrefixes, long rejected) {
    }

    /**
     * 接口加密
     *
     * @param enabled      是否开启
     * @param algorithm    算法
     * @param keySize      RSA 密钥位数
     * @param kid          当前密钥的 kid
     * @param requireHttps 是否要求浏览器使用 HTTPS
     * @param rotating     是否处于密钥轮换期（同时接受上一把密钥）
     */
    public record ApiCrypto(
        boolean enabled,
        String algorithm,
        Integer keySize,
        String kid,
        Boolean requireHttps,
        boolean rotating
    ) {
    }

}
