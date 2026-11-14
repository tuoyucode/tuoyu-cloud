package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;

import java.util.Collection;
import java.util.Map;

/**
 * 岗位服务
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemotePostService {

    /**
     * @param postIds 岗位 ID
     * @return 岗位 ID → 岗位名称
     */
    Map<Long, String> selectPostNamesByIds(Collection<Long> postIds);

}
