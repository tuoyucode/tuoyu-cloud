package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.tuoyu.common.core.context.UserContext;
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.upload.UploadFileValidator;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.resource.api.RemoteFileService;
import org.tuoyu.resource.api.domain.RemoteFile;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.bo.SysUserProfileBo;
import org.tuoyu.system.domain.vo.ProfileUserVo;
import org.tuoyu.system.domain.vo.SysUserVo;
import org.tuoyu.system.service.ISysUserService;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

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

    /**
     * 头像只接受常见图片，按文件头校验（与文件服务的上传白名单无关）
     */
    private static final UploadFileValidator AVATAR_VALIDATOR = UploadFileValidator.of(List.of("jpg", "jpeg", "png", "webp"));

    /**
     * 头像的大小上限
     */
    private static final long AVATAR_MAX_SIZE = DataSize.ofMegabytes(2).toBytes();

    private final ISysUserService userService;
    private final PasswordHasher passwordHasher;
    private final RemoteFileService remoteFileService;

    /**
     * 在本人所属的租户中执行。超级管理员切换进其他租户后，SQL 按切换后的租户过滤，
     * 而个人中心读写的是本人的用户记录，它在本人所属的租户中
     */
    private static <T> T inOwnTenant(Supplier<T> action) {
        return TenantHelper.dynamic(UserContext.require().getTenantId(), action);
    }

    /**
     * 个人信息
     */
    @GetMapping
    public ApiResult<ProfileVo> profile() {
        return ApiResult.ok(inOwnTenant(() -> {
            SysUserVo user = userService.selectUserById(UserContext.userId());
            String roleGroup = userService.selectUserRoleGroup(user.getUserId());
            String postGroup = userService.selectUserPostGroup(user.getUserId());
            // 单独做一个vo专门给个人中心用 避免数据被脱敏
            ProfileUserVo profileUser = BeanUtil.toBean(user, ProfileUserVo.class);
            return new ProfileVo(profileUser, roleGroup, postGroup);
        }));
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
        inOwnTenant(() -> {
            if (StringUtils.isNotEmpty(user.getPhoneNumber()) && !userService.checkPhoneUnique(user)) {
                throw new ServiceException("修改用户'" + username + "'失败，手机号码已存在");
            }
            if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user)) {
                throw new ServiceException("修改用户'" + username + "'失败，邮箱账号已存在");
            }
            DataPermissionHelper.ignore(() -> userService.updateUserProfile(user));
            return null;
        });
        return ApiResult.ok();
    }

    /**
     * 上传本人的头像：jpg、jpeg、png、webp，不超过 2MB，按文件头校验。
     * 不需要文件上传权限，只能设置本人的头像；文件存入默认的对象存储
     *
     * @param file 头像图片
     */
    @OperationLog(module = "个人信息", action = AuditAction.UPDATE)
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ProfileAvatarVo> avatar(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择头像图片");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new ServiceException("头像图片不能超过 2MB", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        UploadFileValidator.ValidatedFile validated = AVATAR_VALIDATOR.validate(file.getOriginalFilename(), file);
        byte[] content = file.getBytes();
        Long userId = UserContext.userId();
        // 头像文件也记在本人所属的租户中，与用户记录一致
        RemoteFile uploaded = inOwnTenant(() -> {
            RemoteFile result = remoteFileService.upload(validated.filename(), content);
            DataPermissionHelper.ignore(() -> userService.updateUserAvatar(userId, result.getOssId()));
            return result;
        });
        return ApiResult.ok(new ProfileAvatarVo(uploaded.getUrl()));
    }

    /**
     * 上传头像的结果
     *
     * @param avatarUrl 头像的访问地址
     */
    public record ProfileAvatarVo(String avatarUrl) {
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
        inOwnTenant(() -> {
            SysUserVo user = userService.selectUserById(UserContext.userId());
            String password = user.getPassword();
            if (!passwordHasher.matches(bo.oldPassword(), password)) {
                throw new ServiceException("修改密码失败，旧密码错误");
            }
            if (passwordHasher.matches(bo.newPassword(), password)) {
                throw new ServiceException("新密码不能与旧密码相同");
            }
            DataPermissionHelper.ignore(() -> userService.resetUserPwd(user.getUserId(), passwordHasher.hash(bo.newPassword())));
            return null;
        });
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
