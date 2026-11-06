package org.tuoyu.common.redis.selfcheck;

import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.GlobalConstants;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.selfcheck.SelfCheckReport;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 各服务的启动自检结果在 Redis 中的存取。
 * <p>
 * 自检在每个服务自己的进程中执行，结果只在本进程内（{@link SelfCheckReport#latest()}）。服务启动完成后把结果写入 Redis，
 * 安全中心（tuoyu-system）据此汇总全部服务的结果，例如只有 tuoyu-auth 才检查的 P7。
 * 键不分租户（{@code global:} 前缀），每个服务名一个，按服务名覆盖：同一服务的多个实例只保留最后启动的那个的结果。
 * 以 JSON 字符串保存，不经过 Redis 默认编解码器的类型信息。
 *
 * @author tuoyu
 */
@Slf4j
public class SelfCheckReportStore {

    static final String KEY_PREFIX = GlobalConstants.GLOBAL_REDIS_KEY + "selfcheck:";

    private final BiConsumer<String, String> writer;

    private final Function<String, String> reader;

    /**
     * @param writer 写入（键、JSON）
     * @param reader 读取（键 → JSON，不存在时为 null）
     */
    public SelfCheckReportStore(BiConsumer<String, String> writer, Function<String, String> reader) {
        this.writer = writer;
        this.reader = reader;
    }

    static String key(String service) {
        return KEY_PREFIX + service;
    }

    /**
     * 写入本服务的结果。失败时只记日志：安全中心看不到本服务的结果，不影响服务本身
     */
    public void publish(String service, SelfCheckReport report) {
        try {
            writer.accept(key(service), JsonUtils.toJsonString(report));
        } catch (RuntimeException e) {
            log.warn("启动自检结果未能写入 Redis，安全中心将看不到 {} 的结果：{}", service, e.toString());
        }
    }

    /**
     * 读取某个服务最近一次写入的结果；没有写入过、读取或解析失败时为空
     */
    public Optional<SelfCheckReport> read(String service) {
        try {
            String json = reader.apply(key(service));
            return json == null ? Optional.empty() : Optional.ofNullable(JsonUtils.parseObject(json, SelfCheckReport.class));
        } catch (RuntimeException e) {
            log.warn("读取 {} 的启动自检结果失败：{}", service, e.toString());
            return Optional.empty();
        }
    }

}
