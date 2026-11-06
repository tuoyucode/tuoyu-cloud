package org.tuoyu.common.job.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cn.hutool.core.collection.CollUtil;
import com.aizuda.snailjob.client.common.appender.SnailLogbackAppender;
import com.aizuda.snailjob.client.common.event.SnailChannelReconnectEvent;
import com.aizuda.snailjob.client.common.event.SnailClientStartingEvent;
import com.aizuda.snailjob.client.starter.EnableSnailJob;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.job.config.properties.SnailJobServerProperties;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * 启动定时任务
 *
 * @author dhb52
 * @since 2024/3/12
 */
@AutoConfiguration
@EnableConfigurationProperties(SnailJobServerProperties.class)
@ConditionalOnProperty(prefix = "snail-job", name = "enabled", havingValue = "true")
@EnableScheduling
@EnableSnailJob
public class SnailJobConfig {

    @Autowired
    private SnailJobServerProperties properties;
    /**
     * 单体中没有注册中心，按 snail-job.server.host / port 直连服务端
     */
    @Autowired
    private ObjectProvider<DiscoveryClient> discoveryClient;

    @EventListener(SnailClientStartingEvent.class)
    public void onStarting(SnailClientStartingEvent event) {
        // 从 nacos 获取 server 服务连接
        registerServer();
        // 注册 日志监控配置
        registerLogging();
    }

    @EventListener(SnailChannelReconnectEvent.class)
    public void onReconnect(SnailChannelReconnectEvent event) {
        // 连接中断 重新从 nacos 获取存活的服务连接(高可用配置)
        registerServer();
    }

    private void registerServer() {
        String serverName = properties.getServerName();
        DiscoveryClient client = discoveryClient.getIfAvailable();
        if (StringUtils.isNotBlank(serverName) && client != null) {
            List<ServiceInstance> instances = client.getInstances(serverName);
            if (CollUtil.isNotEmpty(instances)) {
                ServiceInstance instance = instances.getFirst();
                System.setProperty("snail-job.server.host", instance.getHost());
                System.setProperty("snail-job.server.port", properties.getPort());
            }
        }
    }

    private void registerLogging() {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        SnailLogbackAppender<ILoggingEvent> ca = new SnailLogbackAppender<>();
        ca.setName("snail_log_appender");
        ca.start();
        Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(ca);
    }

}
