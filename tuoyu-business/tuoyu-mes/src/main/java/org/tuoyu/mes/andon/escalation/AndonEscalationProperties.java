package org.tuoyu.mes.andon.escalation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 安灯超时检查的配置（{@code tuoyu.mes.andon.escalation.*}），默认值即推荐值，一般不需要配置
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "tuoyu.mes.andon.escalation")
public class AndonEscalationProperties {

    /**
     * 检查间隔的下限：太频繁的检查没有意义，只会增加数据库压力
     */
    static final Duration MIN_INTERVAL = Duration.ofSeconds(10);

    /**
     * 每批数量的上限
     */
    static final int MAX_BATCH_SIZE = 1000;

    /**
     * 是否在本服务中执行超时检查。多个实例都开启也不会重复升级（按条件更新，同一时限只有一个实例成功）
     */
    private boolean enabled = true;

    /**
     * 两次检查之间的间隔（上一次结束到下一次开始），不小于 10 秒
     */
    private Duration interval = Duration.ofMinutes(1);

    /**
     * 服务启动后多久开始第一次检查
     */
    private Duration initialDelay = Duration.ofSeconds(30);

    /**
     * 每次检查最多处理的事件数（计算截止时间、超时升级各一批），其余的留给下一次
     */
    private int batchSize = 200;

    /**
     * 配置是否有效，无效时说明原因
     *
     * @throws IllegalStateException 配置无效
     */
    public void validate() {
        if (interval == null || interval.compareTo(MIN_INTERVAL) < 0) {
            throw new IllegalStateException("tuoyu.mes.andon.escalation.interval 不能小于 " + MIN_INTERVAL.toSeconds() + " 秒");
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalStateException("tuoyu.mes.andon.escalation.initial-delay 不能为负数");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalStateException("tuoyu.mes.andon.escalation.batch-size 应在 1 ~ " + MAX_BATCH_SIZE + " 之间");
        }
    }

}
