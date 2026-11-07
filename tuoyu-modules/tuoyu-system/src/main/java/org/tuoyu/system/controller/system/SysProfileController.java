package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.tuoyu.common.core.context.UserContext;
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.bo.SysUserProfileBo;
import org.tuoyu.system.domain.vo.ProfileUserVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.service.ISysUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 个人信息 业务处理（仅登录：只读写当前用户本人的信息）
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@SaCheckLogin
@RequestMapping("/user/profile")
public class SysProfileController {

    private final ISysUserService userService;
    private final PasswordHasher passwordHasher;

    /**
     * 个人信息
     */
    @GetMapping
    public ApiResult<ProfileVo> profile() {
        SysUserVo user = userService.selectUserById(UserContext.userId());
        String roleGroup = userService.selectUserRoleGroup(user.getUserId());
        String postGroup = userService.selectUserPostGroup(user.getUserId());
        // 单独做一个vo专门给个人中心用 避免数据被脱敏
        ProfileUserVo profileUser = BeanUtil.toBean(user, ProfileUserVo.class);
        ProfileVo profileVo = new ProfileVo(profileUser, roleGroup, postGroup);
        return ApiResult.ok(profileVo);
    }

    /**
     * 修改用户信息
     */
    @PreventResubmit
    @OperationLog(module = "个人信息", action = AuditAction.UPDATE)
    @PutMapping
    public ApiResult<Void> updateProfile(@Validated @RequestBody SysUserProfileBo profile) {
        SysUserBo user = BeanUtil.toBean(profile, SysUserBo.class);
        user.setUserId(UserContext.userId());
        String username = UserContext.require().getUserName();
        if (StringUtils.isNotEmpty(user.getPhoneNumber()) && !userService.checkPhoneUnique(user)) {
            throw new ServiceException("修改用户'" + username + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
            throw new ServiceException("修改用户'" + username + "'失败，邮箱账号已存在");
        }
        DataPermissionHelper.ignore(() -> userService.updateUserProfile(user));
        return ApiResult.ok();
    }

    /**
     * 重置密码
     *
     * @param bo 新旧密码
     */
    @PreventResubmit
    @ApiEncrypt
    @OperationLog(module = "个人信息", action = AuditAction.UPDATE)
    @PutMapping("/updatePwd")
    public ApiResult<Void> updatePwd(@Validated @RequestBody SysUserPasswordBo bo) {
        SysUserVo user = userService.selectUserById(UserContext.userId());
        String password = user.getPassword();
        if (!passwordHasher.matches(bo.oldPassword(), password)) {
            throw new ServiceException("修改密码失败，旧密码错误");
        }
        if (passwordHasher.matches(bo.newPassword(), password)) {
            throw new ServiceException("新密码不能与旧密码相同");
        }
        DataPermissionHelper.ignore(() -> userService.resetUserPwd(user.getUserId(), passwordHasher.hash(bo.newPassword())));
        return ApiResult.ok();
    }

    /**
     * 用户个人信息
     *
     * @param user      用户信息
     * @param roleGroup 用户所属角色组
     * @param postGroup 用户所属岗位组
     */
    public record ProfileVo(ProfileUserVo user, String roleGroup, String postGroup) {
    }

    /**
     * 用户密码修改
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    public record SysUserPasswordBo(
        @NotBlank(message = "旧密码不能为空") String oldPassword,
        @NotBlank(message = "新密码不能为空") String newPassword) {
    }

}
