package org.tuoyu.common.core.config;

import jakarta.servlet.DispatcherType;
import lombok.Data;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;
import org.tuoyu.common.core.web.RequestSizeLimitFilter;

/**
 * 注册请求大小限制过滤器，网关与各业务服务都会加载。
 * <p>
 * 顺序紧跟在网关的 CORS 过滤器之后（保证 413 响应也带有跨域头），
 * 早于接口解密、网关日志等会整体读取请求体的过滤器。
 * <p>
 * 文件上传的上限取 spring.servlet.multipart 的 max-request-size 与 max-file-size 中较小的：上传接口每次只传一个文件，
 * 网关按它先拒绝。否则单个文件超限的请求会通过网关，下游在读完请求体之前返回 413 并断开连接，网关只能报转发失败（500）。
 * 需要一次上传多个文件的接口时再调整这条规则
 *
 * @author tuoyu
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(FilterRegistrationBean.class)
@EnableConfigurationProperties(RequestSizeLimitConfiguration.RequestLimitProperties.class)
public class RequestSizeLimitConfiguration {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    /**
     * 与 Spring 的 multipart 默认值保持一致
     */
    private static final DataSize DEFAULT_MULTIPART_MAX_REQUEST_SIZE = DataSize.ofMegabytes(10);

    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(RequestLimitProperties properties,
                                                                                 Environment environment) {
        Binder binder = Binder.get(environment);
        long maxRequest = binder.bind("spring.servlet.multipart.max-request-size", DataSize.class)
            .orElse(DEFAULT_MULTIPART_MAX_REQUEST_SIZE).toBytes();
        // 没有配置单个文件上限时只看整个请求的上限
        long maxFile = binder.bind("spring.servlet.multipart.max-file-size", DataSize.class)
            .map(DataSize::toBytes).orElse(-1L);
        RequestSizeLimitFilter filter = new RequestSizeLimitFilter(properties.getMaxBodySize().toBytes(),
            multipartLimit(maxRequest, maxFile));
        FilterRegistrationBean<RequestSizeLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("requestSizeLimitFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(ORDER);
        return registration;
    }

    /**
     * 上传请求的上限：两者中较小的，负数表示不限制
     */
    static long multipartLimit(long maxRequest, long maxFile) {
        if (maxRequest < 0) {
            return maxFile;
        }
        return maxFile < 0 ? maxRequest : Math.min(maxRequest, maxFile);
    }

    /**
     * 请求大小限制配置
     */
    @Data
    @ConfigurationProperties(prefix = "tuoyu.request")
    public static class RequestLimitProperties {

        /**
         * 非文件请求（JSON 等）的请求体上限。文件上传的上限见 spring.servlet.multipart.max-request-size
         */
        private DataSize maxBodySize = DataSize.ofMegabytes(10);

    }

}
