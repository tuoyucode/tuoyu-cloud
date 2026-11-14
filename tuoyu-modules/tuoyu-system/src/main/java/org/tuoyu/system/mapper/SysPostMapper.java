package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysPost;
import org.tuoyu.system.domain.SysUserPost;
import org.tuoyu.system.domain.vo.SysPostVo;

import java.util.Collection;
import java.util.List;

/**
 * 岗位数据层。数据权限按岗位所属部门与创建人（经 Spring 代理调用标注的方法时生效）
 *
 * @author tuoyu
 */
public interface SysPostMapper extends BaseMapper<SysPost>, MPJBaseMapper<SysPost> {

    /**
     * 分页查询岗位
     *
     * @param page         分页
     * @param queryWrapper 条件
     * @return 分页结果
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
     * @param queryWrapper 条件
     * @return 岗位
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<SysPost> selectPostList(Wrapper<SysPost> queryWrapper) {
        return this.selectList(queryWrapper);
    }

    /**
     * 数据权限内这些岗位的个数
     *
     * @param postIds 岗位 ID
     * @return 个数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long selectPostCount(Collection<Long> postIds) {
        return this.selectCount(QueryBuilder.lambda(SysPost.class).in(SysPost::getPostId, postIds).build());
    }

    /**
     * 正常状态的岗位（下拉选项，只取识别列）
     *
     * @param postIds 只查这些岗位，为空时不限
     * @return 岗位
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
     * 按 ID 查询数据权限内的岗位
     *
     * @param postId 岗位 ID
     * @return 岗位；不存在或不在数据权限内时为 null
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default SysPost selectPostById(Long postId) {
        return this.selectById(postId);
    }

    /**
     * 用户的岗位
     *
     * @param userId 用户 ID
     * @return 岗位
     */
    default List<SysPostVo> selectPostsByUserId(Long userId) {
        return this.selectJoinList(SysPostVo.class, QueryBuilder.lambdaJoin("p", SysPost.class)
            .selectAll(SysPost.class)
            .leftJoin(SysUserPost.class, "sup", SysUserPost::getPostId, SysPost::getPostId)
            .eq("sup", SysUserPost::getUserId, userId)
            .build());
    }

}
