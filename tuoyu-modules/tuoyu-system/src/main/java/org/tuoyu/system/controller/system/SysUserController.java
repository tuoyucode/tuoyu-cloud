package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.LoginFailureKeys;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.excel.core.ExcelResult;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.common.redis.utils.RedisUtils;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.bo.SysPostBo;
import org.tuoyu.system.domain.bo.SysRoleBo;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.*;
import org.tuoyu.system.listener.SysUserImportListener;
import org.tuoyu.system.service.ISysDeptService;
import org.tuoyu.system.service.ISysPostService;
import org.tuoyu.system.service.ISysRoleService;
import org.tuoyu.system.service.ISysUserService;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户信息
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class SysUserController {

    private final ISysUserService userService;
    private final ISysRoleService roleService;
    private final ISysPostService postService;
    private final ISysDeptService deptService;
    private final PasswordHasher passwordHasher;

    /**
     * 获取用户列表
     */
    @SaCheckPermission("system:user:list")
    @GetMapping("/list")
    public ApiResult<PageResult<SysUserVo>> list(SysUserBo user, PageParam pageParam) {
        return ApiResult.ok(userService.selectPageUserList(user, pageParam));
    }

    /**
     * 导出用户列表
     */
    @OperationLog(module = "用户管理", action = AuditAction.EXPORT)
    @SaCheckPermission("system:user:export")
    @PostMapping("/export")
    public void export(SysUserBo user, HttpServletResponse response) {
        ExcelBuilder.query(() -> userService.countUserExportList(user),
                () -> userService.selectUserExportList(user), SysUserExportVo.class)
            .sheetName("用户数据").toResponse(response);
    }

    /**
     * 导入数据
     *
     * @param file          导入文件
     * @param updateSupport 是否更新已存在数据
     */
    @OperationLog(module = "用户管理", action = AuditAction.IMPORT)
    @SaCheckPermission("system:user:import")
    @PostMapping(value = "/importData", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<Void> importData(@RequestPart("file") MultipartFile file, boolean updateSupport) throws Exception {
        // 传入文件本身：先预读统计行数，超过 tuoyu.excel.max-import-rows 时一行都不导入
        ExcelResult<SysUserImportVo> result = ExcelBuilder.read(file, SysUserImportVo.class)
            .listener(new SysUserImportListener(updateSupport))
            .doRead();
        return ApiResult.ok(result.getAnalysis(), null);
    }

    /**
     * 获取导入模板
     */
    @SaCheckPermission("system:user:import")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response) {
        ExcelBuilder.of(new ArrayList<>(), SysUserImportVo.class).sheetName("用户数据").toResponse(response);
    }

    /**
     * 获取用户信息（仅登录：当前用户本人的信息、权限码与角色，前端启动时调用）
     *
     * @return 用户信息
     */
    @SaCheckLogin
    @GetMapping("/getInfo")
    public ApiResult<UserInfoVo> getInfo() {
        UserInfoVo userInfoVo = new UserInfoVo();
        CurrentUser loginUser = UserContext.require();
        SysUserVo user = DataPermissionHelper.ignore(() -> userService.selectUserById(loginUser.getUserId()));
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException("没有权限访问用户数据!");
        }
        user.setRoles(roleService.selectRolesByUserId(user.getUserId()));
        userInfoVo.setUser(user);
        userInfoVo.setPermissions(loginUser.getPermissions());
        userInfoVo.setRoles(loginUser.getRoleKeys());
        return ApiResult.ok(userInfoVo);
    }

    /**
     * 根据用户编号获取详细信息
     *
     * @param userId 用户ID
     */
    @SaCheckPermission("system:user:query")
    @GetMapping(value = {"/", "/{userId}"})
    public ApiResult<SysUserInfoVo> getInfo(@PathVariable(value = "userId", required = false) Long userId) {
        SysUserInfoVo userInfoVo = new SysUserInfoVo();
        if (ObjectUtil.isNotNull(userId)) {
            userService.checkUserDataScope(userId);
            SysUserVo sysUser = userService.selectUserById(userId);
            if (sysUser == null) {
                // 超级管理员不受数据权限限制，查询不存在的用户时会走到这里
                throw new ServiceException("用户不存在");
            }
            userInfoVo.setUser(sysUser);
            userInfoVo.setRoleIds(roleService.selectRoleListByUserId(userId));
            Long deptId = sysUser.getDeptId();
            if (ObjectUtil.isNotNull(deptId)) {
                SysPostBo postBo = new SysPostBo();
                postBo.setDeptId(deptId);
                userInfoVo.setPosts(postService.selectPostList(postBo));
                userInfoVo.setPostIds(postService.selectPostListByUserId(userId));
            }
        }
        SysRoleBo roleBo = new SysRoleBo();
        roleBo.setStatus(SystemConstants.NORMAL);
        List<SysRoleVo> roles = roleService.selectRoleList(roleBo);
        userInfoVo.setRoles(UserContext.isSuperAdmin(userId) ? roles : StreamUtils.filter(roles, r -> !r.isSuperAdmin()));
        return ApiResult.ok(userInfoVo);
    }

    /**
     * 新增用户（请求中带明文初始密码，必须加密传输）
     */
    @ApiEncrypt
    @SaCheckPermission("system:user:add")
    @OperationLog(module = "用户管理", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated @RequestBody SysUserBo user) {
        deptService.checkDeptDataScope(user.getDeptId());
        if (!userService.checkUserNameUnique(user)) {
            throw new ServiceException("新增用户'" + user.getUserName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(user.getPhoneNumber()) && !userService.checkPhoneUnique(user)) {
            throw new ServiceException("新增用户'" + user.getUserName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            throw new ServiceException("新增用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        user.setPassword(passwordHasher.hash(user.getPassword()));
        userService.insertUser(user);
        return ApiResult.ok();
    }

    /**
     * 修改用户
     */
    @SaCheckPermission("system:user:edit")
    @OperationLog(module = "用户管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated @RequestBody SysUserBo user) {
        userService.checkUserAllowed(user.getUserId());
        userService.checkUserDataScope(user.getUserId());
        deptService.checkDeptDataScope(user.getDeptId());
        if (!userService.checkUserNameUnique(user)) {
            throw new ServiceException("修改用户'" + user.getUserName() + "'失败，登录账号已存在");
        } else if (StringUtils.isNotEmpty(user.getPhoneNumber()) && !userService.checkPhoneUnique(user)) {
            throw new ServiceException("修改用户'" + user.getUserName() + "'失败，手机号码已存在");
        } else if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            throw new ServiceException("修改用户'" + user.getUserName() + "'失败，邮箱账号已存在");
        }
        userService.updateUser(user);
        return ApiResult.ok();
    }

    /**
     * 删除用户
     *
     * @param userIds 角色ID串
     */
    @SaCheckPermission("system:user:remove")
    @OperationLog(module = "用户管理", action = AuditAction.DELETE)
    @DeleteMapping("/{userIds}")
    public ApiResult<Void> remove(@PathVariable Long[] userIds) {
        if (ArrayUtil.contains(userIds, UserContext.userId())) {
            throw new ServiceException("当前用户不能删除");
        }
        userService.deleteUserByIds(userIds);
        return ApiResult.ok();
    }

    /**
     * 选择用户（工作流转办、委派、加签、抄送等）
     * <p>
     * 只要求登录：办理流程的人通常没有用户管理权限。只返回账号、昵称与部门，不含联系方式；
     * 只查正常状态的用户，不按数据权限过滤（要能选到其他部门的人），多租户开启时仍只在本租户内
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<PageResult<SysUserOptionVo>> options(SysUserBo user, PageParam pageParam) {
        return ApiResult.ok(userService.selectUserOptionPage(user, pageParam));
    }

    /**
     * 根据用户ID串批量获取用户基础信息
     *
     * @param userIds 用户ID串
     * @param deptId  部门ID
     */
    @SaCheckPermission("system:user:query")
    @GetMapping("/optionselect")
    public ApiResult<List<SysUserVo>> optionselect(@RequestParam(required = false) Long[] userIds,
                                           @RequestParam(required = false) Long deptId) {
        return ApiResult.ok(userService.selectUserByIds(ArrayUtil.isEmpty(userIds) ? null : List.of(userIds), deptId));
    }

    /**
     * 重置密码
     */
    @ApiEncrypt
    @SaCheckPermission("system:user:resetPwd")
    @OperationLog(module = "用户管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping("/resetPwd")
    public ApiResult<Void> resetPwd(@RequestBody SysUserBo user) {
        userService.checkUserAllowed(user.getUserId());
        userService.checkUserDataScope(user.getUserId());
        user.setPassword(passwordHasher.hash(user.getPassword()));
        userService.resetUserPwd(user.getUserId(), user.getPassword());
        return ApiResult.ok();
    }

    /**
     * 状态修改
     */
    @SaCheckPermission("system:user:edit")
    @OperationLog(module = "用户管理", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping("/changeStatus")
    public ApiResult<Void> changeStatus(@RequestBody SysUserBo user) {
        userService.checkUserAllowed(user.getUserId());
        userService.checkUserDataScope(user.getUserId());
        userService.updateUserStatus(user.getUserId(), user.getStatus());
        return ApiResult.ok();
    }

    /**
     * 解锁用户
     */
    @SaCheckPermission("system:user:edit")
    @OperationLog(module = "用户解锁", action = AuditAction.OTHER)
    @PreventResubmit
    @GetMapping("/unlock/{userId}")
    public ApiResult<Void> unlock(@PathVariable Long userId) {
        userService.checkUserAllowed(userId);
        userService.checkUserDataScope(userId);
        SysUserVo user = userService.selectUserById(userId);
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException("用户不存在");
        }
        // 清除该用户按用户名、手机号、邮箱统计的登录失败计数
        RedisUtils.deleteObject(LoginFailureKeys.account(user.getUserName()));
        if (StringUtils.isNotBlank(user.getPhoneNumber())) {
            RedisUtils.deleteObject(LoginFailureKeys.account(user.getPhoneNumber()));
        }
        if (StringUtils.isNotBlank(user.getEmail())) {
            RedisUtils.deleteObject(LoginFailureKeys.account(user.getEmail()));
        }
        return ApiResult.ok();
    }

    /**
     * 根据用户编号获取授权角色
     *
     * @param userId 用户ID
     */
    @SaCheckPermission("system:user:query")
    @GetMapping("/authRole/{userId}")
    public ApiResult<SysUserInfoVo> authRole(@PathVariable Long userId) {
        userService.checkUserDataScope(userId);
        SysUserVo user = userService.selectUserById(userId);
        List<SysRoleVo> roles = roleService.selectRolesAuthByUserId(userId);
        SysUserInfoVo userInfoVo = new SysUserInfoVo();
        userInfoVo.setUser(user);
        userInfoVo.setRoles(UserContext.isSuperAdmin(userId) ? roles : StreamUtils.filter(roles, r -> !r.isSuperAdmin()));
        return ApiResult.ok(userInfoVo);
    }

    /**
     * 用户授权角色
     *
     * @param userId  用户Id
     * @param roleIds 角色ID串
     */
    @SaCheckPermission("system:user:edit")
    @OperationLog(module = "用户管理", action = AuditAction.GRANT)
    @PreventResubmit
    @PutMapping("/authRole")
    public ApiResult<Void> insertAuthRole(Long userId, Long[] roleIds) {
        userService.checkUserDataScope(userId);
        userService.insertUserAuth(userId, roleIds);
        return ApiResult.ok();
    }

    /**
     * 获取部门树列表
     */
    @SaCheckPermission("system:user:list")
    @GetMapping("/deptTree")
    public ApiResult<List<Tree<Long>>> deptTree(SysDeptBo dept) {
        return ApiResult.ok(deptService.selectDeptTreeList(dept));
    }

    /**
     * 获取部门下的所有用户信息
     */
    @SaCheckPermission("system:user:list")
    @GetMapping("/list/dept/{deptId}")
    public ApiResult<List<SysUserVo>> listByDept(@PathVariable @NotNull Long deptId) {
        return ApiResult.ok(userService.selectUserListByDept(deptId));
    }

}
