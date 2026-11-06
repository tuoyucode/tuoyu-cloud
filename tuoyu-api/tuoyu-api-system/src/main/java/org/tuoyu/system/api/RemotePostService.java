package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import java.util.Collection;
import java.util.Map;

/**
 * 岗位服务
 *
 * @author Lion Li
 */
@RemoteApi
public interface RemotePostService {

    /**
     * 根据岗位 ID 列表查询岗位名称映射关系
     *
     * @param postIds 岗位 ID 列表
     * @return Map，其中 key 为岗位 ID，value 为对应的岗位名称
     */
    Map<Long, String> selectPostNamesByIds(Collection<Long> postIds);

}
