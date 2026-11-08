package org.tuoyu.common.dubbo.config;

import org.apache.dubbo.config.spring.util.DubboBeanUtils;
import org.tuoyu.common.core.factory.YmlPropertySourceFactory;
import org.tuoyu.common.dubbo.handler.DubboExceptionHandler;
import org.tuoyu.common.dubbo.properties.DubboCustomProperties;
import org.tuoyu.common.dubbo.rpc.DubboRemoteReferenceFactory;
import org.tuoyu.common.dubbo.rpc.RemoteApiDubboExporter;
import org.tuoyu.common.rpc.RemoteReferenceFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

/**
 * dubbo 配置类：微服务方式下跨模块调用的 RPC 实现。业务模块不直接使用 Dubbo
 */
@AutoConfiguration
@EnableConfigurationProperties(DubboCustomProperties.class)
@PropertySource(value = "classpath:common-dubbo.yml", factory = YmlPropertySourceFactory.class)
public class DubboConfiguration {

    static {
        // JDK 25 下 Dubbo JsonUtils 可能在 ServiceLoader 扫描 JSON SPI 时提前探测到未引入的 Gson 实现。
        // 项目按 Dubbo 默认使用 fastjson2，这里只固定首选 JSON 实现，避免误加载 Gson。
        System.setProperty("dubbo.json-framework.prefer",
            System.getProperty("dubbo.json-framework.prefer", "fastjson2"));
    }

    /**
     * dubbo自定义IP注入(避免IP不正确问题)
     */
    @Bean
    public BeanFactoryPostProcessor customBeanFactoryPostProcessor() {
        return new CustomBeanFactoryPostProcessor();
    }

    /**
     * 异常处理器
     */
    @Bean
    public DubboExceptionHandler dubboExceptionHandler() {
        return new DubboExceptionHandler();
    }

    /**
     * 远程引用：本服务中没有实现的 {@code @RemoteApi} 接口通过它注入
     */
    @Bean
    public RemoteReferenceFactory dubboRemoteReferenceFactory(BeanFactory beanFactory) {
        return new DubboRemoteReferenceFactory(DubboBeanUtils.getModuleModel(beanFactory));
    }

    /**
     * 导出本服务中 {@code @RemoteApi} 接口的实现（取代 {@code @DubboService}）
     */
    @Bean
    public RemoteApiDubboExporter remoteApiDubboExporter(BeanFactory beanFactory) {
        return new RemoteApiDubboExporter(beanFactory, DubboBeanUtils.getModuleModel(beanFactory));
    }

}
