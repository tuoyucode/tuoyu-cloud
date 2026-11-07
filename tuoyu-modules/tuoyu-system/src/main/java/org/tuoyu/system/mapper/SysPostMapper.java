package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.vo.SysPostVo;

import java.util.Collection;
import java.util.List;

/**
 * 岗位信息 数据层
 *
 * @author Lion Li
 */
public interface SysPostMapper extends BaseMapper<SysPost>, MPJBaseMapper<SysPost> {

    /**
     * 分页查询岗位列表
     *
     * @param page         分页对象
     * @param queryWrapper 查询条件
     * @return 包含岗位信息的分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<SysPost> selectPagePostList(Page<SysPost> page, Wrapper<SysPost> queryWrapper) {
        return this.selectPage(page, queryWrapper);
    }

    /**
     * 查询岗位列表
     *
     * @param queryWrapper 查询条件
     * @return 岗位信息列表
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<SysPost> selectPostList(Wrapper<SysPost> queryWrapper) {
        return this.selectList(queryWrapper);
    }

    /**
     * 根据岗位ID集合查询岗位数量
     *
     * @param postIds 岗位ID列表
     * @return 匹配的岗位数量
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long selectPostCount(Collection<Long> postIds) {
        return this.selectCount(QueryBuilder.lambda(SysPost.class).in(SysPost::getPostId, postIds).build());
    }

    /**
     * 按岗位ID查询正常状态的岗位（下拉选项用，只返回编号、名称、编码）
     *
     * @param postIds 岗位ID列表，为空时不按ID过滤
     * @return 岗位列表
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<SysPost> selectPostOptions(Collection<Long> postIds) {
        return this.selectList(QueryBuilder.lambda(SysPost.class)
            .select(SysPost::getPostId, SysPost::getPostName, SysPost::getPostCode)
            .eq(SysPost::getStatus, SystemConstants.NORMAL)
            .inIfNotEmpty(SysPost::getPostId, postIds)
            .build());
    }

    /**
     * 根据用户ID查询其关联的岗位列表
     *
     * @param userId 用户ID
     * @return 岗位信息列表
     */
    default List<SysPostVo> selectPostsByUserId(Long userId) {
        return this.selectJoinList(SysPostVo.class, QueryBuilder.lambdaJoin("p", SysPost.class)
            .selectAll(SysPost.class)
            .leftJoin(SysUserPost.class, "sup", SysUserPost::getPostId, SysPost::getPostId)
            .eq("sup", SysUserPost::getUserId, userId)
            .build());
    }

}
