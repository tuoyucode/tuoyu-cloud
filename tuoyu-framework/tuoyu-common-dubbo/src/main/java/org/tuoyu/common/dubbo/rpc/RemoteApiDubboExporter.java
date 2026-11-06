package org.tuoyu.common.dubbo.rpc;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.tuoyu.common.core.context.ContextPropagators;
import org.tuoyu.common.rpc.RemoteApiRegistry;

import java.util.Map;

/**
 * 微服务方式下，把本服务中 {@code @RemoteApi} 接口的实现导出为 Dubbo 服务（业务代码不需要 {@code @DubboService}）。
 * <p>
 * 在所有单例创建完成后、Dubbo 模块启动前登记，由 Dubbo 模块启动时统一导出。服务名为接口全名，与 {@code @DubboService} 导出时相同，
 * 所以使用 {@code @DubboReference} 的外部调用方也能直接调用。
 * 同时把上下文透传的组合放进 Dubbo 的容器，供过滤器使用。
 *
 * @author tuoyu
 */
@Slf4j
public class RemoteApiDubboExporter implements SmartInitializingSingleton {

    private final BeanFactory beanFactory;

    private final ModuleModel moduleModel;

    public RemoteApiDubboExporter(BeanFactory beanFactory, ModuleModel moduleModel) {
        this.beanFactory = beanFactory;
        this.moduleModel = moduleModel;
    }

    @Override
    public void afterSingletonsInstantiated() {
        ContextPropagators propagators = beanFactory.getBean(ContextPropagators.class);
        moduleModel.getApplicationModel().getBeanFactory().getOrRegisterBean(ContextPropagators.class, type -> propagators);
        RemoteApiRegistry registry = beanFactory.getBean(RemoteApiRegistry.class);
        for (Map.Entry<Class<?>, String> entry : registry.localImplementations().entrySet()) {
            export(entry.getKey(), beanFactory.getBean(entry.getValue()));
        }
        log.info("跨模块接口：导出 {} 个，远程引用 {} 个", registry.localImplementations().size(), registry.remoteApis().size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void export(Class<?> api, Object implementation) {
        ServiceConfig service = new ServiceConfig<>(moduleModel);
        service.setInterface(api);
        service.setRef(implementation);
        moduleModel.getConfigManager().addService(service);
    }

}
