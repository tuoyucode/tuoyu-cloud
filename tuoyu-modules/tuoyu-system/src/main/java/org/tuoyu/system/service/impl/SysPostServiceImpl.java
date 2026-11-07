package org.tuoyu.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 岗位信息 服务层处理
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class SysPostServiceImpl implements ISysPostService {

    private final SysPostMapper postMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserPostMapper userPostMapper;

    /**
     * 分页查询岗位列表
     *
     * @param post      查询条件
     * @param pageParam 分页参数
     * @return 岗位分页列表
     */
    @Override
    public PageResult<SysPostVo> selectPagePostList(SysPostBo post, PageParam pageParam) {
        return PageResults.of(postMapper.selectPagePostList(pageParam.toPage(SysPost.class), buildQueryWrapper(post)), SysPostVo.class);
    }

    /**
     * 查询岗位信息集合（按数据权限过滤）
     *
     * @param post 岗位信息
     * @return 岗位信息集合
     */
    @Override
    public List<SysPostVo> selectPostList(SysPostBo post) {
        return BeanMapper.mapList(postMapper.selectPostList(buildQueryWrapper(post)), SysPostVo.class);
    }

    /**
     * 统计符合条件的岗位数量（导出前先统计，查询条件与数据权限都与列表一致）
     */
    @Override
    public long countPostList(SysPostBo post) {
        return postMapper.selectPagePostList(PageParam.countOnly(), buildQueryWrapper(post)).getTotal();
    }

    /**
     * 查询用户所属岗位组
     *
     * @param userId 用户ID
     * @return 岗位ID
     */
    @Override
    public List<SysPostVo> selectPostsByUserId(Long userId) {
        return postMapper.selectPostsByUserId(userId);
    }

    /**
     * 根据查询条件构建查询包装器
     *
     * @param bo 查询条件对象
     * @return 构建好的查询包装器
     */
    private Wrapper<SysPost> buildQueryWrapper(SysPostBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryBuilder<SysPost> wrapper = QueryBuilder.lambda(SysPost.class)
            .likeIfText(SysPost::getPostCode, bo.getPostCode())
            .likeIfText(SysPost::getPostCategory, bo.getPostCategory())
            .likeIfText(SysPost::getPostName, bo.getPostName())
            .eqIfText(SysPost::getStatus, bo.getStatus())
            .betweenParams(SysPost::getCreateTime, params, "beginTime", "endTime")
            .orderByAsc(SysPost::getPostSort);
        if (ObjectUtil.isNotNull(bo.getDeptId())) {
            //优先单部门搜索
            wrapper.eq(SysPost::getDeptId, bo.getDeptId());
        } else if (ObjectUtil.isNotNull(bo.getBelongDeptId())) {
            //部门树搜索
            wrapper.and(x -> {
                List<Long> deptIds = deptMapper.selectDeptAndChildById(bo.getBelongDeptId());
                x.in(SysPost::getDeptId, deptIds);
            });
        }
        return wrapper.build();
    }

    /**
     * 查询所有岗位
     *
     * @return 岗位列表
     */
    @Override
    public List<SysPostVo> selectPostAll() {
        return BeanMapper.mapList(postMapper.selectList(Wrappers.emptyWrapper()), SysPostVo.class);
    }

    /**
     * 通过岗位ID查询岗位信息
     *
     * @param postId 岗位ID
     * @return 角色对象信息
     */
    @Override
    public SysPostVo selectPostById(Long postId) {
        return BeanMapper.map(postMapper.selectById(postId), SysPostVo.class);
    }

    /**
     * 根据用户ID获取岗位选择框列表
     *
     * @param userId 用户ID
     * @return 选中岗位ID列表
     */
    @Override
    public List<Long> selectPostListByUserId(Long userId) {
        List<SysPostVo> list = postMapper.selectPostsByUserId(userId);
        return StreamUtils.toList(list, SysPostVo::getPostId);
    }

    /**
     * 通过岗位ID串查询岗位（按数据权限过滤）
     *
     * @param postIds 岗位id串
     * @return 岗位列表信息
     */
    @Override
    public List<SysPostVo> selectPostByIds(List<Long> postIds) {
        return BeanMapper.mapList(postMapper.selectPostOptions(postIds), SysPostVo.class);
    }

    /**
     * 校验岗位名称是否唯一
     *
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public boolean checkPostNameUnique(SysPostBo post) {
        boolean exist = postMapper.exists(QueryBuilder.lambda(SysPost.class)
            .eq(SysPost::getPostName, post.getPostName())
            .eq(SysPost::getDeptId, post.getDeptId())
            .neIfPresent(SysPost::getPostId, post.getPostId())
            .build());
        return !exist;
    }

    /**
     * 校验岗位编码是否唯一
     *
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public boolean checkPostCodeUnique(SysPostBo post) {
        boolean exist = postMapper.exists(QueryBuilder.lambda(SysPost.class)
            .eq(SysPost::getPostCode, post.getPostCode())
            .neIfPresent(SysPost::getPostId, post.getPostId())
            .build());
        return !exist;
    }

    /**
     * 通过岗位ID查询岗位使用数量
     *
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public long countUserPostById(Long postId) {
        return userPostMapper.selectCount(QueryBuilder.lambda(SysUserPost.class).eq(SysUserPost::getPostId, postId).build());
    }

    /**
     * 通过部门ID查询岗位使用数量
     *
     * @param deptId 部门id
     * @return 结果
     */
    @Override
    public long countPostByDeptId(Long deptId) {
        return postMapper.selectCount(QueryBuilder.lambda(SysPost.class).eq(SysPost::getDeptId, deptId).build());
    }

    /**
     * 删除岗位信息
     *
     * @param postId 岗位ID
     */
    @Override
    public void deletePostById(Long postId) {
        postMapper.deleteById(postId);
    }

    /**
     * 批量删除岗位信息
     *
     * @param postIds 需要删除的岗位ID
     */
    @Override
    public void deletePostByIds(List<Long> postIds) {
        List<SysPost> list = postMapper.selectByIds(postIds);
        for (SysPost post : list) {
            if (this.countUserPostById(post.getPostId()) > 0) {
                throw new ServiceException("{}已分配，不能删除!", post.getPostName());
            }
        }
        if (postMapper.deleteByIds(postIds) == 0) {
            throw new ServiceException("删除岗位失败，岗位不存在");
        }
    }

    /**
     * 新增保存岗位信息
     *
     * @param bo 岗位信息
     */
    @Override
    public void insertPost(SysPostBo bo) {
        SysPost post = BeanMapper.map(bo, SysPost.class);
        postMapper.insert(post);
    }

    /**
     * 修改保存岗位信息
     *
     * @param bo 岗位信息
     */
    @Override
    public void updatePost(SysPostBo bo) {
        SysPost post = BeanMapper.map(bo, SysPost.class);
        if (postMapper.updateById(post) == 0) {
            throw new ServiceException("修改岗位'{}'失败，岗位不存在", bo.getPostName());
        }
    }
}
