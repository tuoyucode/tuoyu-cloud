package org.tuoyu.system.service;

import cn.hutool.core.lang.tree.Tree;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.vo.SysDeptVo;

import java.util.List;

/**
 * 部门服务
 *
 * @author tuoyu
 */
public interface ISysDeptService {

    /**
     * 分页查询部门（数据权限内）
     *
     * @param dept      条件
     * @param pageParam 分页
     * @return 分页结果
     */
    PageResult<SysDeptVo> selectPageDeptList(SysDeptBo dept, PageParam pageParam);

    /**
     * 查询部门列表（数据权限内）
     *
     * @param dept 条件
     * @return 部门（可修改的列表）
     */
    List<SysDeptVo> selectDeptList(SysDeptBo dept);

    /**
     * 部门树（数据权限内，可能有多个根）
     *
     * @param dept 条件
     * @return 树
     */
    List<Tree<Long>> selectDeptTreeList(SysDeptBo dept);

    /**
     * 角色已选的部门（回显）
     *
     * @param roleId 角色 ID
     * @return 部门 ID
     */
    List<Long> selectDeptListByRoleId(Long roleId);

    /**
     * 按 ID 查询部门（带上级名称，结果缓存）
     *
     * @param deptId 部门 ID
     * @return 部门；不存在时为 null
     */
    SysDeptVo selectDeptById(Long deptId);

    /**
     * 正常状态的部门（下拉选项）
     *
     * @param deptIds 只查这些部门，为空时不限
     * @return 部门
     */
    List<SysDeptVo> selectDeptByIds(List<Long> deptIds);

    /**
     * @param deptIds 逗号分隔的部门 ID
     * @return 逗号连接的部门名称（跳过不存在的部门）
     */
    String selectDeptNameByIds(String deptIds);

    /**
     * @param deptId 部门 ID
     * @return 正常状态的下级部门数
     */
    long selectNormalChildrenDeptById(Long deptId);

    /**
     * @param deptId 部门 ID
     * @return 是否有直接下级部门
     */
    boolean hasChildByDeptId(Long deptId);

    /**
     * @param deptId 部门 ID
     * @return 部门中是否有用户
     */
    boolean checkDeptExistUser(Long deptId);

    /**
     * @param dept 部门（修改时排除自己）
     * @return 同一上级下是否没有其他同名部门
     */
    boolean checkDeptNameUnique(SysDeptBo dept);

    /**
     * 检查部门在当前用户的数据权限内（超级管理员与空 ID 不检查）
     *
     * @param deptId 部门 ID
     */
    void checkDeptDataScope(Long deptId);

    /**
     * 新增部门
     *
     * @param bo 部门
     */
    void insertDept(SysDeptBo bo);

    /**
     * 修改部门（更换上级时改写自己与下级的祖级；启用时一并启用上级）
     *
     * @param bo 部门
     */
    void updateDept(SysDeptBo bo);

    /**
     * 删除部门
     *
     * @param deptId 部门 ID
     */
    void deleteDeptById(Long deptId);

}
