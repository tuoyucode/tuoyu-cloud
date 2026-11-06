package org.tuoyu.common.bus.redis;

import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.bus.config.RedisBusProperties;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.*;
import org.springframework.cloud.stream.binder.AbstractBinder;
import org.springframework.cloud.stream.binder.Binding;
import org.springframework.cloud.stream.binder.ConsumerProperties;
import org.springframework.cloud.stream.binder.ProducerProperties;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal Redis Stream binder used by Spring Cloud Bus.
 *
 * @author dromara
 */
@Slf4j
public class RedisMessageChannelBinder extends AbstractBinder<MessageChannel, ConsumerProperties, ProducerProperties> {

    private static final String MESSAGE_FIELD = "message";

    private final RedissonClient redissonClient;

    private final RedisBusProperties properties;

    public RedisMessageChannelBinder(RedissonClient redissonClient, RedisBusProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    @Override
    protected Binding<MessageChannel> doBindConsumer(String name, String group, MessageChannel inboundBindTarget, ConsumerProperties consumerProperties) {
        Assert.notNull(inboundBindTarget, "inboundBindTarget must not be null");
        RStream<String, Object> stream = redissonClient.getStream(buildStreamName(name));
        String groupName = buildGroupName(name);
        String consumerName = buildConsumerName(name);
        createGroupIfNecessary(stream, groupName);
        stream.createConsumer(groupName, consumerName);
        RStream<String, Object> dlqStream = redissonClient.getStream(buildDlqStreamName(name, groupName));
        RedisConsumerBinding binding = new RedisConsumerBinding(name, inboundBindTarget, stream, dlqStream, groupName, consumerName, properties);
        binding.start();
        log.info("Redis bus consumer bound to stream: {}, group: {}", name, groupName);
        return binding;
    }

    @Override
    protected Binding<MessageChannel> doBindProducer(String name, MessageChannel outboundBindTarget, ProducerProperties producerProperties) {
        Assert.isInstanceOf(SubscribableChannel.class, outboundBindTarget, "Redis bus producer requires a SubscribableChannel");
        RStream<String, Object> stream = redissonClient.getStream(buildStreamName(name));
        MessageHandler handler = message -> stream.add(StreamAddArgs.<String, Object>entry(
                MESSAGE_FIELD, new RedisBusMessage(message.getPayload(), new HashMap<>(message.getHeaders())))
            .trimNonStrict().maxLen(properties.getStreamMaxLen()).noLimit());
        ((SubscribableChannel) outboundBindTarget).subscribe(handler);
        log.info("Redis bus producer bound to stream: {}", name);
        return new RedisProducerBinding(name, outboundBindTarget, (SubscribableChannel) outboundBindTarget, handler);
    }

    private String buildStreamName(String destination) {
        return "spring:cloud:bus:" + (StringUtils.hasText(destination) ? destination : "springCloudBus");
    }

    private String buildDlqStreamName(String destination, String groupName) {
        return buildStreamName(destination) + ":dlq:" + groupName;
    }

    private String buildGroupName(String destination) {
        Environment environment = getApplicationContext().getEnvironment();
        String busId = environment.getProperty("spring.cloud.bus.id");
        if (!StringUtils.hasText(busId)) {
            busId = environment.getProperty("spring.application.name", "application");
        }
        return (StringUtils.hasText(destination) ? destination : "springCloudBus") + ":" + busId;
    }

    private String buildConsumerName(String destination) {
        return (StringUtils.hasText(destination) ? destination : "springCloudBus") + ":" + buildInstanceId(getApplicationContext().getEnvironment());
    }

    private String buildInstanceId(Environment environment) {
        String instanceId = firstText(
            environment.getProperty("spring.cloud.bus.redis.instance-id"),
            environment.getProperty("spring.cloud.nacos.discovery.ip"),
            environment.getProperty("spring.cloud.client.ip-address"),
            environment.getProperty("HOSTNAME"),
            environment.getProperty("COMPUTERNAME"),
            "localhost"
        );
        String port = firstText(
            environment.getProperty("server.port"),
            environment.getProperty("local.server.port"),
            "0"
        );
        return instanceId + ":" + port;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private void createGroupIfNecessary(RStream<String, Object> stream, String groupName) {
        try {
            stream.createGroup(StreamCreateGroupArgs.name(groupName).id(StreamMessageId.NEWEST).makeStream());
        } catch (Exception e) {
            if (!String.valueOf(e.getMessage()).contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    public static class RedisBusMessage implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Object payload;

        private Map<String, Object> headers = Map.of();

        private int attempts;

        private String lastError;

        public RedisBusMessage() {
        }

        public RedisBusMessage(Object payload, Map<String, Object> headers) {
            this.payload = payload;
            this.headers = headers == null ? Map.of() : headers;
        }

        public Object getPayload() {
            return payload;
        }

        public void setPayload(Object payload) {
            this.payload = payload;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, Object> headers) {
            this.headers = headers == null ? Map.of() : headers;
        }

        public int getAttempts() {
            return attempts;
        }

        public void setAttempts(int attempts) {
            this.attempts = attempts;
        }

        public String getLastError() {
            return lastError;
        }

        public void setLastError(String lastError) {
            this.lastError = lastError;
        }

    }

    private abstract static class AbstractRedisBinding implements Binding<MessageChannel> {

        private final String name;
        private final MessageChannel target;
        private final AtomicBoolean running = new AtomicBoolean(false);

        private AbstractRedisBinding(String name, MessageChannel target) {
            this.name = name;
            this.target = target;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void start() {
            running.set(true);
        }

        @Override
        public void stop() {
            running.set(false);
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public void unbind() {
            stop();
        }

        protected MessageChannel getTarget() {
            return target;
        }

    }

    private static class RedisConsumerBinding extends AbstractRedisBinding {

        private final RStream<String, Object> stream;
        private final RStream<String, Object> dlqStream;
        private final String groupName;
        private final String consumerName;
        private final RedisBusProperties properties;
        private final ExecutorService executor;

        private RedisConsumerBinding(String name, MessageChannel target, RStream<String, Object> stream, RStream<String, Object> dlqStream,
                                     String groupName, String consumerName, RedisBusProperties properties) {
            super(name, target);
            this.stream = stream;
            this.dlqStream = dlqStream;
            this.groupName = groupName;
            this.consumerName = consumerName;
            this.properties = properties;
            ThreadFactory threadFactory = task -> {
                Thread thread = new Thread(task, "redis-bus-stream-" + name);
                thread.setDaemon(true);
                return thread;
            };
            this.executor = Executors.newSingleThreadExecutor(threadFactory);
        }

        @Override
        public void start() {
            if (isRunning()) {
                return;
            }
            super.start();
            executor.execute(this::poll);
        }

        /**
         * 优雅停止（ADR 0007 第 4 节）：不再读取新消息，等待正在处理的一批完成（处理完的会确认）；
         * 超过 spring.cloud.bus.redis.shutdown-timeout 才中断。未确认的消息由其他实例按 claim-idle-timeout 认领重新处理
         */
        @Override
        public void stop() {
            super.stop();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(properties.getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    log.warn("Redis bus consumer did not finish in {}, interrupting, group: {}", properties.getShutdownTimeout(), groupName);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        private void poll() {
            while (isRunning() && !Thread.currentThread().isInterrupted()) {
                try {
                    claimPendingMessages();
                    Map<StreamMessageId, Map<String, Object>> messages = stream.readGroup(
                        groupName, consumerName, StreamReadGroupArgs.neverDelivered()
                            .count(properties.getReadCount())
                            .timeout(properties.getReadTimeout()));
                    processMessages(messages);
                } catch (Exception e) {
                    if (isRunning()) {
                        log.error("Redis bus stream consume failed, group: {}", groupName, e);
                    }
                }
            }
        }

        private void claimPendingMessages() {
            AutoClaimResult<String, Object> result = stream.autoClaim(groupName, consumerName,
                properties.getClaimIdleTimeout().toMillis(), TimeUnit.MILLISECONDS,
                StreamMessageId.MIN, properties.getClaimCount());
            processMessages(result.getMessages());
        }

        private void processMessages(Map<StreamMessageId, Map<String, Object>> messages) {
            if (messages == null || messages.isEmpty()) {
                return;
            }
            for (Map.Entry<StreamMessageId, Map<String, Object>> entry : messages.entrySet()) {
                RedisBusMessage message = (RedisBusMessage) entry.getValue().get(MESSAGE_FIELD);
                if (message == null) {
                    stream.ack(groupName, entry.getKey());
                    continue;
                }
                processMessage(entry.getKey(), message);
            }
        }

        private void processMessage(StreamMessageId messageId, RedisBusMessage message) {
            try {
                Message<Object> springMessage = MessageBuilder.withPayload(message.getPayload())
                    .copyHeaders(message.getHeaders())
                    .build();
                getTarget().send(springMessage);
                stream.ack(groupName, messageId);
            } catch (Exception e) {
                handleFailure(messageId, message, e);
            }
        }

        private void handleFailure(StreamMessageId messageId, RedisBusMessage message, Exception e) {
            int attempts = message.getAttempts() + 1;
            message.setAttempts(attempts);
            message.setLastError(e.getClass().getName() + ": " + e.getMessage());
            if (attempts >= properties.getMaxAttempts()) {
                if (properties.isDlqEnabled()) {
                    dlqStream.add(StreamAddArgs.<String, Object>entry(MESSAGE_FIELD, message)
                        .trimNonStrict().maxLen(properties.getStreamMaxLen()).noLimit());
                }
                stream.ack(groupName, messageId);
                log.error("Redis bus message exhausted retries, group: {}, attempts: {}", groupName, attempts, e);
                return;
            }
            stream.add(StreamAddArgs.<String, Object>entry(MESSAGE_FIELD, message)
                .trimNonStrict().maxLen(properties.getStreamMaxLen()).noLimit());
            stream.ack(groupName, messageId);
            log.warn("Redis bus message retry scheduled, group: {}, attempts: {}", groupName, attempts, e);
        }

        @Override
        public void unbind() {
            // 先停止并等待处理中的消息确认，再移除消费者：移除消费者会丢弃它名下尚未确认的消息
            stop();
            try {
                stream.removeConsumer(groupName, consumerName);
            } catch (Exception e) {
                log.debug("Remove Redis stream consumer failed, group: {}, consumer: {}", groupName, consumerName, e);
            }
        }

        @Override
        public boolean isInput() {
            return true;
        }

    }

    private static class RedisProducerBinding extends AbstractRedisBinding {

        private final SubscribableChannel channel;
        private final MessageHandler handler;

        private RedisProducerBinding(String name, MessageChannel target, SubscribableChannel channel, MessageHandler handler) {
            super(name, target);
            this.channel = channel;
            this.handler = handler;
        }

        @Override
        public void unbind() {
            channel.unsubscribe(handler);
            super.unbind();
        }

    }

}
