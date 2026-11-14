package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.api.RemotePostService;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.mapper.SysPostMapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 岗位服务的跨模块实现（不带数据权限，租户隔离照常）
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemotePostServiceImpl implements RemotePostService {

    private final SysPostMapper postMapper;

    @Override
    public Map<Long, String> selectPostNamesByIds(Collection<Long> postIds) {
        Map<Long, String> names = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return names;
        }
        for (SysPost post : postMapper.selectList(QueryBuilder.lambda(SysPost.class)
            .select(SysPost::getPostId, SysPost::getPostName)
            .in(SysPost::getPostId, postIds)
            .build())) {
            if (post != null) {
                names.put(post.getPostId(), post.getPostName());
            }
        }
        return names;
    }

}
