package org.tuoyu.mes.equipment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 设备保养的配置：按保养计划自动生成到期工单的定时任务
 * <pre>
 * tuoyu:
 *   mes:
 *     maintenance:
 *       generate:
 *         enabled: true        # 关闭后只能在页面上点“生成到期工单”
 *         interval: 1h         # 执行间隔，至少 1 分钟（例如每天一次写 24h）
 *         initial-delay: 2m    # 服务启动后首次执行前的等待
 * </pre>
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "tuoyu.mes.maintenance")
public class MesMaintenanceProperties {

    private Generate generate = new Generate();

    @Data
    public static class Generate {

        /**
         * 是否自动生成到期的保养工单
         */
        private boolean enabled = true;

        /**
         * 执行间隔
         */
        private Duration interval = Duration.ofHours(1);

        /**
         * 服务启动后首次执行前的等待
         */
        private Duration initialDelay = Duration.ofMinutes(2);

    }

}
