package org.tuoyu.common.rpc.support;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.tuoyu.common.rpc.RemoteReferenceFactory;

/**
 * 远程引用：微服务方式下，没有本地实现的接口由 RPC 实现（{@link RemoteReferenceFactory}）创建引用
 *
 * @author tuoyu
 */
public class RemoteReferenceFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware {

    private final Class<T> api;

    private BeanFactory beanFactory;

    public RemoteReferenceFactoryBean(Class<T> api) {
        this.api = api;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public T getObject() {
        ObjectProvider<RemoteReferenceFactory> factory = beanFactory.getBeanProvider(RemoteReferenceFactory.class);
        RemoteReferenceFactory referenceFactory = factory.getIfAvailable();
        if (referenceFactory == null) {
            throw new IllegalStateException(api.getName() + " 在本服务中没有实现，需要远程调用，但没有 RPC 实现："
                + "微服务方式请引入 tuoyu-common-dubbo；单体方式请设置 tuoyu.deployment.mode=monolith 并装入提供它的模块");
        }
        return referenceFactory.create(api);
    }

    @Override
    public Class<?> getObjectType() {
        return api;
    }

}
