package org.tuoyu.workflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.service.IFlwInstanceService;

/**
 * 注册 Warm-Flow 自带接口的权限拦截器（规则见 {@link WarmFlowAccessInterceptor}）
 *
 * @author tuoyu
 */
@ConditionalOnEnable
@Configuration(proxyBeanMethods = false)
public class WarmFlowAccessConfig implements WebMvcConfigurer {

    private final IFlwInstanceService instanceService;

    public WarmFlowAccessConfig(IFlwInstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new WarmFlowAccessInterceptor(instanceService))
            .addPathPatterns(WarmFlowAccessInterceptor.PATTERN);
    }

}
