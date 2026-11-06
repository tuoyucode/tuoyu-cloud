package org.tuoyu.common.rpc.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.tuoyu.common.core.context.ContextPropagator;
import org.tuoyu.common.core.context.ContextPropagators;
import org.tuoyu.common.rpc.support.RemoteApiRegistrar;

/**
 * 跨模块调用抽象的自动配置（ADR 0005）
 *
 * @author tuoyu
 */
@AutoConfiguration
public class RpcAutoConfiguration {

    /**
     * 必须是 static：它在其他 Bean 创建之前处理 Bean 定义
     */
    @Bean
    public static RemoteApiRegistrar remoteApiRegistrar() {
        return new RemoteApiRegistrar();
    }

    /**
     * 全部上下文透传实现的组合，供 RPC 实现与异步任务使用
     */
    @Bean
    public ContextPropagators contextPropagators(ObjectProvider<ContextPropagator> propagators) {
        return new ContextPropagators(propagators.orderedStream().toList());
    }

    /**
     * 异步任务（{@code @Async} 使用的执行器）在执行线程中恢复提交时的上下文（租户、数据权限状态）。
     * Spring Boot 会把唯一的 TaskDecorator Bean 应用到自动配置的执行器上
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public TaskDecorator contextPropagatingTaskDecorator(ContextPropagators propagators) {
        return propagators::wrap;
    }

}
