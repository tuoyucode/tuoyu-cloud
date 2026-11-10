package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysRoleVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.event.OnlineUserCleanEvent;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/role")
public class SysRoleController {

    private final ISysRoleService roleService;
    private final ISysUserService userService;
    private final ISysDeptService deptService;

    /**
     * 获取角色信息列表
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysRoleVo>> list(SysRoleBo role, PageParam pageParam) {
        return ApiResult.ok(roleService.selectPageRoleList(role, pageParam));
    }

    /**
     * 导出角色信息列表
     */
    @OperationLog(module = "角色管理", action = AuditAction.EXPORT)
    @SaCheckPermission("system:role:export")
    @PostMapping("/export")
    public void export(SysRoleBo role, HttpServletResponse response) {
        ExcelBuilder.query(() -> roleService.countRoleList(role), () -> roleService.selectRoleList(role), SysRoleVo.class)
            .sheetName("角色数据").toResponse(response);
    }

    /**
     * 根据角色编号获取详细信息
     *
     * @param roleId 角色ID
     */
    @SaCheckPermission("system:role:query")
    @GetMapping(value = "/{roleId}")
    public ApiResult<SysRoleVo> getInfo(@PathVariable Long roleId) {
        roleService.checkRoleDataScope(roleId);
        return ApiResult.ok(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色
     */
    @SaCheckPermission("system:role:add")
    @OperationLog(module = "角色管理", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated @RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        if (!roleService.checkRoleNameUnique(role)) {
            throw new ServiceException("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            throw new ServiceException("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        roleService.insertRole(role);
        return ApiResult.ok();
    }

    /**
     * 修改角色基础信息（不包含菜单权限、数据权限）。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @OperationLog(module = "角色管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated @RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        if (!roleService.checkRoleNameUnique(role)) {
            throw new ServiceException("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        } else if (!roleService.checkRoleKeyUnique(role)) {
            throw new ServiceException("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }

        roleService.updateRoleBaseInfo(role);
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getRoleId()));
        return ApiResult.ok();
    }

    /**
     * 修改角色权限信息（菜单权限 + 数据权限）。
     *
     * @param role 角色参数
     * @return 操作结果
     */
    @SaCheckPermission("system:role:edit")
    @OperationLog(module = "角色管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping("/permission")
    public ApiResult<Void> editPermission(@RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        roleService.updateRolePermission(role);
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getRoleId()));
        return ApiResult.ok();
    }

    /**
     * 状态修改
     */
    @SaCheckPermission("system:role:edit")
    @OperationLog(module = "角色管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping("/changeStatus")
    public ApiResult<Void> changeStatus(@RequestBody SysRoleBo role) {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        roleService.updateRoleStatus(role.getRoleId(), role.getStatus());
        SpringUtils.context().publishEvent(OnlineUserCleanEvent.byRole(role.getRoleId()));
        return ApiResult.ok();
    }

    /**
     * 删除角色
     *
     * @param roleIds 角色ID串
     */
    @SaCheckPermission("system:role:remove")
    @OperationLog(module = "角色管理", action = AuditAction.DELETE)
    @DeleteMapping("/{roleIds}")
    public ApiResult<Void> remove(@PathVariable Long[] roleIds) {
        roleService.deleteRoleByIds(List.of(roleIds));
        return ApiResult.ok();
    }

    /**
     * 获取角色选择框列表
     *
     * @param roleIds 角色ID串
     */
    @SaCheckPermission("system:role:query")
    @GetMapping("/optionselect")
    public ApiResult<List<SysRoleVo>> optionselect(@RequestParam(required = false) Long[] roleIds) {
        return ApiResult.ok(roleService.selectRoleByIds(roleIds == null ? null : List.of(roleIds)));
    }

    /**
     * 查询已分配用户角色列表
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/authUser/allocatedList")
    public ApiResult<PageResult<SysUserVo>> allocatedList(SysUserBo user, PageParam pageParam) {
        return ApiResult.ok(userService.selectAllocatedList(user, pageParam));
    }

    /**
     * 查询未分配用户角色列表
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/authUser/unallocatedList")
    public ApiResult<PageResult<SysUserVo>> unallocatedList(SysUserBo user, PageParam pageParam) {
        return ApiResult.ok(userService.selectUnallocatedList(user, pageParam));
    }

    /**
     * 取消授权用户
     */
    @SaCheckPermission("system:role:edit")
    @OperationLog(module = "角色管理", action = AuditAction.GRANT)
    @PreventResubmit
    @PutMapping("/authUser/cancel")
    public ApiResult<Void> cancelAuthUser(@RequestBody SysUserRole userRole) {
        roleService.deleteAuthUser(userRole);
        return ApiResult.ok();
    }

    /**
     * 批量取消授权用户
     *
     * @param roleId  角色ID
     * @param userIds 用户ID串
     */
    @SaCheckPermission("system:role:edit")
    @OperationLog(module = "角色管理", action = AuditAction.GRANT)
    @PreventResubmit
    @PutMapping("/authUser/cancelAll")
    public ApiResult<Void> cancelAuthUserAll(Long roleId, Long[] userIds) {
        roleService.deleteAuthUsers(roleId, userIds);
        return ApiResult.ok();
    }

    /**
     * 批量选择用户授权
     *
     * @param roleId  角色ID
     * @param userIds 用户ID串
     */
    @SaCheckPermission("system:role:edit")
    @OperationLog(module = "角色管理", action = AuditAction.GRANT)
    @PreventResubmit
    @PutMapping("/authUser/selectAll")
    public ApiResult<Void> selectAuthUserAll(Long roleId, Long[] userIds) {
        roleService.checkRoleDataScope(roleId);
        roleService.insertAuthUsers(roleId, userIds);
        return ApiResult.ok();
    }

    /**
     * 获取对应角色部门树列表。角色须在当前用户的数据范围内（含租户隔离），与读取角色的菜单一致
     *
     * @param roleId 角色ID
     */
    @SaCheckPermission("system:role:list")
    @GetMapping(value = "/deptTree/{roleId}")
    public ApiResult<DeptTreeSelectVo> roleDeptTreeselect(@PathVariable("roleId") Long roleId) {
        roleService.checkRoleDataScope(roleId);
        DeptTreeSelectVo treeSelect = new DeptTreeSelectVo(
            deptService.selectDeptListByRoleId(roleId),
            deptService.selectDeptTreeList(new SysDeptBo()));
        return ApiResult.ok(treeSelect);
    }

    /**
     * 角色部门列表树信息
     *
     * @param checkedKeys 选中部门列表
     * @param depts       下拉树结构列表
     */
    public record DeptTreeSelectVo(List<Long> checkedKeys, List<Tree<Long>> depts) {
    }

}
