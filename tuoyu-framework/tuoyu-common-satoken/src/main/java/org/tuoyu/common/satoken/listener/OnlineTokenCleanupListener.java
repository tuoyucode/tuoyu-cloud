package org.tuoyu.common.satoken.listener;

import cn.dev33.satoken.listener.SaTokenListenerForSimple;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.redis.utils.RedisUtils;

/**
 * 令牌失效时清理在线用户记录（{@code online_tokens:<token>}）。
 * <p>
 * 注册在框架中，所有服务都生效：在 tuoyu-system 中踢人下线、在任意服务中注销或被顶下线时都会清理。
 *
 * @author tuoyu
 */
@Slf4j
public class OnlineTokenCleanupListener extends SaTokenListenerForSimple {

    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        cleanup("logout", loginId, tokenValue);
    }

    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        cleanup("kickout", loginId, tokenValue);
    }

    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        cleanup("replaced", loginId, tokenValue);
    }

    private static void cleanup(String reason, Object loginId, String tokenValue) {
        RedisUtils.deleteObject(CacheNames.ONLINE_TOKEN_KEY + tokenValue);
        log.info("user {}, userId:{}, token:***{}", reason, loginId, StringUtils.right(tokenValue, 8));
    }

}
