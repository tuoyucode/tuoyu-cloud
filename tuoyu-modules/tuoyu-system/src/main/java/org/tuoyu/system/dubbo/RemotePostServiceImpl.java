package org.tuoyu.system.dubbo;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.system.api.RemotePostService;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.mapper.SysPostMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 岗位服务
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class RemotePostServiceImpl implements RemotePostService {

    private final SysPostMapper postMapper;

    /**
     * 根据岗位 ID 列表查询岗位名称映射关系
     *
     * @param postIds 岗位 ID 列表
     * @return Map，其中 key 为岗位 ID，value 为对应的岗位名称
     */
    @Override
    public Map<Long, String> selectPostNamesByIds(Collection<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyMap();
        }
        List<SysPost> list = postMapper.lambda()
            .select(SysPost::getPostId, SysPost::getPostName)
            .in(SysPost::getPostId, postIds)
            .list();
        return StreamUtils.toMap(list, SysPost::getPostId, SysPost::getPostName);
    }

}
