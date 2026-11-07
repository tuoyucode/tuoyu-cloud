package org.tuoyu.common.satoken.utils;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.system.api.model.LoginUser;

import java.util.Optional;

/**
 * 登录会话查询：按令牌读取其他会话中保存的登录用户。
 * <p>
 * 供在线用户管理、角色权限变更后让相关会话下线等场景使用。当前请求的用户一律通过
 * {@code UserContext} 获取，不要用本类。
 *
 * @author tuoyu
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoginSessions {

    /**
     * 按令牌查找会话中的登录用户。
     *
     * @param token 令牌
     * @return 登录用户；令牌为空、无效、已过期或会话中没有登录用户时为空
     */
    public static Optional<LoginUser> findLoginUser(String token) {
        if (StringUtils.isBlank(token)) {
            return Optional.empty();
        }
        SaSession session;
        try {
            session = StpUtil.getTokenSessionByToken(token);
        } catch (SaTokenException e) {
            // 令牌无效或已过期时 Sa-Token 抛出的异常类型不止一种，一律视为没有会话
            return Optional.empty();
        }
        if (session == null) {
            return Optional.empty();
        }
        return session.get(LoginHelper.LOGIN_USER_KEY) instanceof LoginUser user ? Optional.of(user) : Optional.empty();
    }

}
