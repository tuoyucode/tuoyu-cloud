package org.tuoyu.common.rpc.support;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.reflect.Proxy;

/**
 * 本地调用代理：包装同一容器中的实现
 *
 * @author tuoyu
 */
public class LocalRemoteProxyFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware {

    /**
     * 测试用的严格模式：拷贝参数与返回值，用来发现修改了对方对象的代码
     */
    public static final String STRICT_COPY_KEY = "tuoyu.rpc.local.strict-copy";

    private final Class<T> api;

    private final String targetBeanName;

    private BeanFactory beanFactory;

    public LocalRemoteProxyFactoryBean(Class<T> api, String targetBeanName) {
        this.api = api;
        this.targetBeanName = targetBeanName;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public T getObject() {
        boolean strictCopy = beanFactory.getBean(Environment.class)
            .getProperty(STRICT_COPY_KEY, Boolean.class, false);
        LocalInvocationHandler handler = new LocalInvocationHandler(api, targetBeanName, beanFactory,
            beanFactory.getBeanProvider(PlatformTransactionManager.class), strictCopy);
        return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api, LocalRemoteProxy.class}, handler));
    }

    @Override
    public Class<?> getObjectType() {
        return api;
    }

}
