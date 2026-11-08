package org.tuoyu.common.dubbo.rpc;

import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.rpc.model.ModuleModel;
import org.tuoyu.common.rpc.RemoteReferenceFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * 用 Dubbo 创建远程引用。引用登记到 Dubbo 模块中，随模块启动建立（与 {@code @DubboReference} 相同），
 * 超时、负载均衡沿用 {@code dubbo.consumer.*} 的全局配置（超时默认 3 秒，TUOYU_DUBBO_TIMEOUT）；固定不重试
 *
 * @author tuoyu
 */
public class DubboRemoteReferenceFactory implements RemoteReferenceFactory {

    private final ModuleModel moduleModel;

    public DubboRemoteReferenceFactory(ModuleModel moduleModel) {
        this.moduleModel = moduleModel;
    }

    @Override
    public <T> T create(Class<T> api) {
        ReferenceConfig<T> reference = new ReferenceConfig<>(moduleModel);
        reference.setInterface(api);
        reference.setCheck(false);
        // 不重试：框架无法判断接口是否幂等，超时的调用可能已在提供方执行
        reference.setRetries(0);
        moduleModel.getConfigManager().addReference(reference);
        // 第一次调用时才取引用：容器启动阶段 Dubbo 模块可能尚未启动
        return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "DubboReference[" + api.getName() + "]";
                };
            }
            try {
                return method.invoke(reference.get(), args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }));
    }

}
