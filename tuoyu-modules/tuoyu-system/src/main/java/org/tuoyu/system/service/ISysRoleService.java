package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.vo.SysRoleVo;

import java.util.List;
import java.util.Set;

/**
 * 角色服务
 *
 * @author tuoyu
 */
public interface ISysRoleService {

    /**
     * 分页查询角色（数据权限内）
     *
     * @param role      条件
     * @param pageParam 分页
     * @return 分页结果
     */
    PageResult<SysRoleVo> selectPageRoleList(SysRoleBo role, PageParam pageParam);

    /**
     * 查询角色列表（数据权限内）
     *
     * @param role 条件
     * @return 角色
     */
    List<SysRoleVo> selectRoleList(SysRoleBo role);

    /**
     * 统计角色数（导出前检查行数）
     *
     * @param role 条件
     * @return 行数
     */
    long countRoleList(SysRoleBo role);

    /**
     * @param userId 用户 ID
     * @return 用户的全部角色（含停用的角色）
     */
    List<SysRoleVo> selectRolesByUserId(Long userId);

    /**
     * 授权页的角色列表：当前用户可见的全部角色，用户已有的打上标记
     *
     * @param userId 用户 ID
     * @return 角色
     */
    List<SysRoleVo> selectRolesAuthByUserId(Long userId);

    /**
     * @param userId 用户 ID
     * @return 用户的正常状态角色的角色标识（逗号分隔的多项逐项拆开）
     */
    Set<String> selectRolePermissionByUserId(Long userId);

    /**
     * @param userId 用户 ID
     * @return 用户的角色 ID（含停用的角色，用于回显）
     */
    List<Long> selectRoleListByUserId(Long userId);

    /**
     * @param roleId 角色 ID
     * @return 数据权限内的角色；不存在或在范围外时为 null
     */
    SysRoleVo selectRoleById(Long roleId);

    /**
     * 正常状态的角色（下拉选项，数据权限内）
     *
     * @param roleIds 只查这些角色，为空时不限
     * @return 角色
     */
    List<SysRoleVo> selectRoleByIds(List<Long> roleIds);

    /**
     * @param roleId 角色 ID
     * @return 拥有该角色的用户数
     */
    long countUserRoleByRoleId(Long roleId);

    /**
     * @param role 角色（修改时排除自己）
     * @return 是否没有其他同名角色
     */
    boolean checkRoleNameUnique(SysRoleBo role);

    /**
     * @param role 角色（修改时排除自己）
     * @return 是否没有其他同标识角色
     */
    boolean checkRoleKeyUnique(SysRoleBo role);

    /**
     * 检查角色允许操作：不能操作超级管理员角色，不能使用或改掉内置管理员标识
     *
     * @param role 角色
     */
    void checkRoleAllowed(SysRoleBo role);

    /**
     * 检查角色在当前用户的数据权限内
     *
     * @param roleId 角色 ID，为 null 时不检查
     */
    void checkRoleDataScope(Long roleId);

    /**
     * 检查角色都在当前用户的数据权限内
     *
     * @param roleIds 角色 ID，为空时不检查
     */
    void checkRoleDataScope(List<Long> roleIds);

    /**
     * 新增角色与它的菜单
     *
     * @param bo 角色
     */
    void insertRole(SysRoleBo bo);

    /**
     * 修改角色的基础信息（不含菜单与数据权限）
     *
     * @param bo 角色
     */
    void updateRoleBaseInfo(SysRoleBo bo);

    /**
     * 修改角色的菜单与数据权限
     *
     * @param bo 角色（菜单与部门都必须提供，空数组表示清空）
     */
    void updateRolePermission(SysRoleBo bo);

    /**
     * 修改角色状态
     *
     * @param roleId 角色 ID
     * @param status 状态
     */
    void updateRoleStatus(Long roleId, String status);

    /**
     * 批量删除角色
     *
     * @param roleIds 角色 ID
     */
    void deleteRoleByIds(List<Long> roleIds);

    /**
     * 取消一个用户的角色
     *
     * @param userRole 用户与角色
     */
    void deleteAuthUser(SysUserRole userRole);

    /**
     * 批量取消用户的角色
     *
     * @param roleId  角色 ID
     * @param userIds 用户 ID
     */
    void deleteAuthUsers(Long roleId, Long[] userIds);

    /**
     * 批量授予用户角色
     *
     * @param roleId  角色 ID
     * @param userIds 用户 ID
     */
    void insertAuthUsers(Long roleId, Long[] userIds);

    /**
     * 强制退出拥有该角色的在线会话
     *
     * @param roleId 角色 ID
     */
    void cleanOnlineUserByRole(Long roleId);

    /**
     * 强制退出这些用户的在线会话
     *
     * @param userIds 用户 ID
     */
    void cleanOnlineUser(List<Long> userIds);

    /**
     * 强制退出该租户的在线会话
     *
     * @param tenantId 租户编号
     */
    void cleanOnlineUserByTenant(String tenantId);

}
