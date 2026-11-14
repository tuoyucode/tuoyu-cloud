package org.tuoyu.auth.service;

import org.springframework.stereotype.Service;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.enums.LoginType;
import org.tuoyu.common.core.exception.user.CaptchaException;
import org.tuoyu.common.core.exception.user.CaptchaExpireException;
import org.tuoyu.common.core.exception.user.UserException;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.core.utils.ServletUtils;
import org.tuoyu.common.redis.guard.VerifyCodeService;
import org.tuoyu.common.redis.guard.VerifyResult;
import org.tuoyu.system.api.model.LoginUser;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 登录凭证的核验：失败锁定、统一提示、验证码。
 * <ul>
 *     <li>提示不区分账号不存在、已停用与凭证错误（避免借提示枚举账号），真实原因只写入登录日志；</li>
 *     <li>按账号与客户端 IP 计数，达到次数后锁定；锁定期内直接拒绝，不执行凭证校验；</li>
 *     <li>图形验证码与短信、邮箱验证码都是一次性的，由 {@link VerifyCodeService} 保证。</li>
 * </ul>
 *
 * @author tuoyu
 */
@Service
public class CredentialVerifier {

    private static final String USER_NOT_EXISTS = "user.not.exists";

    private static final String USER_BLOCKED = "user.blocked";

    private static final String REASON_SEPARATOR = "；";

    private final LoginFailureLimiter loginFailureLimiter;

    private final VerifyCodeService verifyCodeService;

    private final AuthSecurityProperties authSecurityProperties;

    private final LoginAudit loginAudit;

    public CredentialVerifier(LoginFailureLimiter loginFailureLimiter, VerifyCodeService verifyCodeService,
                              AuthSecurityProperties authSecurityProperties, LoginAudit loginAudit) {
        this.loginFailureLimiter = loginFailureLimiter;
        this.verifyCodeService = verifyCodeService;
        this.authSecurityProperties = authSecurityProperties;
        this.loginAudit = loginAudit;
    }

    /**
     * 在失败锁定的保护下核验凭证
     *
     * @param loginType 登录方式，决定失败时的提示
     * @param account   登录账号（用户名、手机号或邮箱），按它计数与锁定
     * @param attempt   凭证校验
     * @return 通过的用户
     * @throws UserException 处于锁定期或校验未通过时
     */
    public LoginUser authenticate(LoginType loginType, String account, Supplier<AuthAttempt> attempt) {
        String ip = ServletUtils.getClientIP();
        Duration remaining = loginFailureLimiter.lockedFor(account, ip);
        if (remaining != null) {
            loginAudit.record(account, Constants.LOGIN_FAIL, MessageUtils.message("user.login.reason.rejected"));
            throw new UserException("user.login.locked", LoginFailureLimiter.toMinutes(remaining));
        }
        AuthAttempt result = attempt.get();
        if (result.passed()) {
            loginFailureLimiter.reset(account);
            return result.user();
        }
        LoginFailureLimiter.Failure failure = loginFailureLimiter.recordFailure(account, ip);
        if (failure.locked()) {
            long minutes = LoginFailureLimiter.toMinutes(failure.lockedFor());
            String reason = result.failReason() + REASON_SEPARATOR
                + MessageUtils.message("user.login.reason.locked", failure.accountFailures(), minutes);
            loginAudit.record(account, Constants.LOGIN_FAIL, reason);
            throw new UserException("user.login.locked", minutes);
        }
        loginAudit.record(account, Constants.LOGIN_FAIL, result.failReason());
        throw new UserException(loginType == LoginType.PASSWORD ? "user.login.failed" : "user.login.code.failed");
    }

    /**
     * 查询登录用户。账号不存在与已停用按校验失败处理（原因写入登录日志），其他异常照常抛出
     *
     * @param loader 查询
     * @return 校验结果
     */
    public AuthAttempt findUser(Supplier<LoginUser> loader) {
        LoginUser user;
        try {
            user = loader.get();
        } catch (UserException e) {
            if (USER_NOT_EXISTS.equals(e.getCode()) || USER_BLOCKED.equals(e.getCode())) {
                return AuthAttempt.failure(e.getMessage());
            }
            throw e;
        }
        return user == null ? AuthAttempt.failure(MessageUtils.message(USER_NOT_EXISTS, "")) : AuthAttempt.success(user);
    }

    /**
     * 校验短信、邮箱验证码
     *
     * @param scene  场景，例如 {@link VerifyCodeService#SCENE_SMS}
     * @param target 接收方（手机号、邮箱）
     * @param input  用户输入
     * @return 通过时为 null，否则为写入登录日志的原因
     */
    public String checkVerifyCode(String scene, String target, String input) {
        VerifyResult result = verifyCodeService.checkCode(scene, target, input,
            authSecurityProperties.getVerifyCode().getMaxAttempts());
        return switch (result) {
            case PASSED -> null;
            case EXPIRED -> MessageUtils.message("user.login.reason.code.expire");
            case MISMATCH -> MessageUtils.message("user.login.reason.code.mismatch");
            case EXHAUSTED -> MessageUtils.message("user.login.reason.code.exhausted");
        };
    }

    /**
     * 校验图形验证码，未通过时写登录日志
     *
     * @param username 用户名，写入登录日志
     * @param code     用户输入
     * @param uuid     验证码标识
     * @throws CaptchaExpireException 验证码不存在、已过期或已使用
     * @throws CaptchaException       验证码错误
     */
    public void verifyCaptcha(String username, String code, String uuid) {
        VerifyResult result = verifyCodeService.checkCaptcha(uuid, code);
        if (result == VerifyResult.PASSED) {
            return;
        }
        if (result == VerifyResult.EXPIRED) {
            loginAudit.record(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire"));
            throw new CaptchaExpireException();
        }
        loginAudit.record(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error"));
        throw new CaptchaException();
    }

}
