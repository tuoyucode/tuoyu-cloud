package org.tuoyu.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.tuoyu.common.web.handler.ApiErrorController;

/**
 * 错误页面返回 {@link org.tuoyu.common.core.domain.ApiResult}：排在 Spring Boot 的错误页面配置之前注册
 * {@link ApiErrorController}，Spring Boot 默认的错误控制器（返回自己的结构、不带 traceId）因此不会创建
 *
 * @author tuoyu
 */
@AutoConfiguration(before = ErrorMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ErrorPageConfiguration {

    @Bean
    @ConditionalOnMissingBean(ErrorController.class)
    public ApiErrorController apiErrorController() {
        return new ApiErrorController();
    }

}
