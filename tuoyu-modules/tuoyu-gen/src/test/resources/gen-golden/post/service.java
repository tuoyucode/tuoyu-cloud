package org.tuoyu.system.service;

import org.tuoyu.system.domain.vo.PostDemoVo;
import org.tuoyu.system.domain.bo.PostDemoBo;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;

import java.util.Collection;
import java.util.List;

public interface IPostDemoService {

    PostDemoVo queryById(Long postId);

    PageResult<PostDemoVo> queryPageList(PostDemoBo bo, PageParam pageParam);

    List<PostDemoVo> queryList(PostDemoBo bo);

    long countList(PostDemoBo bo);

    boolean checkUnique(PostDemoBo bo);

    void insertByBo(PostDemoBo bo);

    void updateByBo(PostDemoBo bo);

    void updateStatus(Long postId, String status);

    void updateSort(Long postId, Integer sortValue);

    void deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
