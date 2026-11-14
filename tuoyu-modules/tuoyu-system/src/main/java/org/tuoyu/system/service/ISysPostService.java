package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysPostBo;
import org.tuoyu.system.domain.vo.SysPostVo;

import java.util.List;

/**
 * 岗位服务
 *
 * @author tuoyu
 */
public interface ISysPostService {

    /**
     * 分页查询岗位（数据权限内）
     *
     * @param post      条件
     * @param pageParam 分页
     * @return 分页结果
     */
    PageResult<SysPostVo> selectPagePostList(SysPostBo post, PageParam pageParam);

    /**
     * 查询岗位列表（数据权限内）
     *
     * @param post 条件
     * @return 岗位
     */
    List<SysPostVo> selectPostList(SysPostBo post);

    /**
     * 统计岗位数（导出前检查行数）
     *
     * @param post 条件
     * @return 行数
     */
    long countPostList(SysPostBo post);

    /**
     * @param userId 用户 ID
     * @return 用户的岗位
     */
    List<SysPostVo> selectPostsByUserId(Long userId);

    /**
     * @param postId 岗位 ID
     * @return 数据权限内的岗位；不存在或在范围外时为 null
     */
    SysPostVo selectPostById(Long postId);

    /**
     * @param userId 用户 ID
     * @return 用户的岗位 ID
     */
    List<Long> selectPostListByUserId(Long userId);

    /**
     * 正常状态的岗位（下拉选项）
     *
     * @param postIds 只查这些岗位，为空时不限
     * @return 岗位
     */
    List<SysPostVo> selectPostByIds(List<Long> postIds);

    /**
     * @param post 岗位（修改时排除自己）
     * @return 同一部门内是否没有其他同名岗位
     */
    boolean checkPostNameUnique(SysPostBo post);

    /**
     * @param post 岗位（修改时排除自己）
     * @return 是否没有其他同编码的岗位
     */
    boolean checkPostCodeUnique(SysPostBo post);

    /**
     * @param postId 岗位 ID
     * @return 使用该岗位的用户数
     */
    long countUserPostById(Long postId);

    /**
     * @param deptId 部门 ID
     * @return 该部门的岗位数（不受数据权限限制，删除部门前检查）
     */
    long countPostByDeptId(Long deptId);

    /**
     * 新增岗位
     *
     * @param bo 岗位
     */
    void insertPost(SysPostBo bo);

    /**
     * 修改岗位（检查数据权限）
     *
     * @param bo 岗位
     */
    void updatePost(SysPostBo bo);

    /**
     * 批量删除岗位（检查数据权限；已分配给用户的岗位不能删除）
     *
     * @param postIds 岗位 ID
     */
    void deletePostByIds(List<Long> postIds);

}
