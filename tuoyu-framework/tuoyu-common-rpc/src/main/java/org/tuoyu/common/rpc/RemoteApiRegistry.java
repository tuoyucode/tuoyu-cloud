package org.tuoyu.common.rpc;

import java.util.Map;
import java.util.Set;

/**
 * 启动时确定的跨模块接口清单
 *
 * @param localImplementations 有本地实现的接口 → 实现的 Bean 名（微服务方式下由 RPC 实现导出给其他服务）
 * @param remoteApis           没有本地实现、使用远程引用的接口
 * @author tuoyu
 */
public record RemoteApiRegistry(Map<Class<?>, String> localImplementations, Set<Class<?>> remoteApis) {

    public RemoteApiRegistry {
        localImplementations = Map.copyOf(localImplementations);
        remoteApis = Set.copyOf(remoteApis);
    }

}
