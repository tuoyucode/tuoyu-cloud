package org.tuoyu.common.dubbo.rpc;

import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.tuoyu.common.core.context.ContextPropagators;

/**
 * 过滤器从 Dubbo 的容器中取上下文透传的组合（由 {@link RemoteApiDubboExporter} 放入）
 *
 * @author tuoyu
 */
final class ContextFilters {

    private ContextFilters() {
    }

    static ContextPropagators propagators(Invoker<?> invoker) {
        ApplicationModel application = invoker.getUrl().getApplicationModel();
        return application == null ? null : application.getBeanFactory().getBean(ContextPropagators.class);
    }

}
