package org.tuoyu.common.redis.config;

import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.tuoyu.common.redis.selfcheck.SelfCheckReportStore;
import org.tuoyu.common.selfcheck.SelfCheckReport;

/**
 * 服务启动完成后把启动自检结果写入 Redis（{@link SelfCheckReportStore}）。只在依赖了 tuoyu-common-selfcheck 的服务中生效
 *
 * @author tuoyu
 */
@AutoConfiguration(after = RedisConfiguration.class, afterName = {
    "org.redisson.spring.starter.RedissonAutoConfigurationV2", "org.redisson.spring.starter.RedissonAutoConfigurationV4"})
@ConditionalOnClass(SelfCheckReport.class)
@ConditionalOnBean(RedissonClient.class)
public class SelfCheckReportAutoConfiguration {

    @Bean
    public SelfCheckReportStore selfCheckReportStore(RedissonClient redissonClient) {
        return new SelfCheckReportStore(
            (key, json) -> redissonClient.<String>getBucket(key, StringCodec.INSTANCE).set(json),
            key -> redissonClient.<String>getBucket(key, StringCodec.INSTANCE).get());
    }

    @Bean
    public SelfCheckReportPublisher selfCheckReportPublisher(SelfCheckReportStore store, Environment environment) {
        return new SelfCheckReportPublisher(store, environment.getProperty("spring.application.name"));
    }

    /**
     * 启动完成（ApplicationReadyEvent）后写入；本进程没有执行自检或没有服务名时不写
     */
    public static class SelfCheckReportPublisher {

        private final SelfCheckReportStore store;

        private final String service;

        public SelfCheckReportPublisher(SelfCheckReportStore store, String service) {
            this.store = store;
            this.service = service;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void publish() {
            if (service == null || service.isBlank()) {
                return;
            }
            SelfCheckReport.latest().ifPresent(report -> store.publish(service, report));
        }

    }

}
