package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.bo.SysPostBo;
import org.tuoyu.system.domain.vo.SysPostVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysPostMapper;
import org.tuoyu.system.mapper.SysUserPostMapper;
import org.tuoyu.system.service.ISysPostService;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 岗位服务实现。列表、详情与数量检查经带数据权限的 Mapper 方法执行；修改与删除前检查岗位在数据权限内
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysPostServiceImpl implements ISysPostService {

    private final SysPostMapper postMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserPostMapper userPostMapper;

    @Override
    public PageResult<SysPostVo> selectPagePostList(SysPostBo post, PageParam pageParam) {
        return PageResults.of(postMapper.selectPagePostList(pageParam.toPage(SysPost.class), buildQueryWrapper(post)),
            SysPostVo.class);
    }

    @Override
    public List<SysPostVo> selectPostList(SysPostBo post) {
        return BeanMapper.mapList(postMapper.selectPostList(buildQueryWrapper(post)), SysPostVo.class);
    }

    @Override
    public long countPostList(SysPostBo post) {
        return postMapper.selectPagePostList(PageParam.countOnly(), buildQueryWrapper(post)).getTotal();
    }

    @Override
    public List<SysPostVo> selectPostsByUserId(Long userId) {
        return postMapper.selectPostsByUserId(userId);
    }

    @Override
    public SysPostVo selectPostById(Long postId) {
        return BeanMapper.map(postMapper.selectPostById(postId), SysPostVo.class);
    }

    @Override
    public List<Long> selectPostListByUserId(Long userId) {
        return StreamUtils.toList(postMapper.selectPostsByUserId(userId), SysPostVo::getPostId);
    }

    @Override
    public List<SysPostVo> selectPostByIds(List<Long> postIds) {
        return BeanMapper.mapList(postMapper.selectPostOptions(postIds), SysPostVo.class);
    }

    @Override
    public boolean checkPostNameUnique(SysPostBo post) {
        return !postMapper.exists(QueryBuilder.lambda(SysPost.class)
            .eq(SysPost::getPostName, post.getPostName())
            .eq(SysPost::getDeptId, post.getDeptId())
            .neIfPresent(SysPost::getPostId, post.getPostId())
            .build());
    }

    @Override
    public boolean checkPostCodeUnique(SysPostBo post) {
        return !postMapper.exists(QueryBuilder.lambda(SysPost.class)
            .eq(SysPost::getPostCode, post.getPostCode())
            .neIfPresent(SysPost::getPostId, post.getPostId())
            .build());
    }

    @Override
    public long countUserPostById(Long postId) {
        return userPostMapper.selectCount(QueryBuilder.lambda(SysUserPost.class)
            .eq(SysUserPost::getPostId, postId)
            .build());
    }

    @Override
    public long countPostByDeptId(Long deptId) {
        return postMapper.selectCount(QueryBuilder.lambda(SysPost.class)
            .eq(SysPost::getDeptId, deptId)
            .build());
    }

    @Override
    public void insertPost(SysPostBo bo) {
        postMapper.insert(BeanMapper.map(bo, SysPost.class));
    }

    @Override
    public void updatePost(SysPostBo bo) {
        checkPostDataScope(Collections.singletonList(bo.getPostId()));
        if (postMapper.updateById(BeanMapper.map(bo, SysPost.class)) == 0) {
            throw new ServiceException("修改岗位'{}'失败，岗位不存在", bo.getPostName());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePostByIds(List<Long> postIds) {
        checkPostDataScope(postIds);
        for (SysPost post : postMapper.selectByIds(postIds)) {
            if (countUserPostById(post.getPostId()) > 0) {
                throw new ServiceException("{}已分配，不能删除!", post.getPostName());
            }
        }
        if (postMapper.deleteByIds(postIds) == 0) {
            throw new ServiceException("删除岗位失败，岗位不存在");
        }
    }

    /**
     * 当前用户不是超级管理员时，岗位必须都在数据权限内（按带数据权限的计数判断）
     *
     * @param postIds 岗位 ID（空 ID 不参与检查）
     */
    private void checkPostDataScope(List<Long> postIds) {
        if (UserContext.isSuperAdmin() || postIds == null) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long postId : postIds) {
            if (Objects.nonNull(postId)) {
                ids.add(postId);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        if (postMapper.selectPostCount(ids) != ids.size()) {
            throw new ServiceException("没有权限访问岗位数据！");
        }
    }

    /**
     * 列表条件：编码、类别、名称模糊，状态相等，创建时间区间；指定部门时只查该部门，否则按所属部门含下级
     */
    private LambdaQueryWrapper<SysPost> buildQueryWrapper(SysPostBo bo) {
        LambdaQueryBuilder<SysPost> query = QueryBuilder.lambda(SysPost.class)
            .likeIfText(SysPost::getPostCode, bo.getPostCode())
            .likeIfText(SysPost::getPostCategory, bo.getPostCategory())
            .likeIfText(SysPost::getPostName, bo.getPostName())
            .eqIfText(SysPost::getStatus, bo.getStatus())
            .betweenParams(SysPost::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByAsc(SysPost::getPostSort);
        if (bo.getDeptId() != null) {
            query.eq(SysPost::getDeptId, bo.getDeptId());
        } else if (bo.getBelongDeptId() != null) {
            query.in(SysPost::getDeptId, deptMapper.selectDeptAndChildById(bo.getBelongDeptId()));
        }
        return query.build();
    }

}
