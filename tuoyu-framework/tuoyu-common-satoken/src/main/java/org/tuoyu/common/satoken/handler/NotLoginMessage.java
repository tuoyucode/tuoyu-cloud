package org.tuoyu.common.satoken.handler;

import cn.dev33.satoken.exception.NotLoginException;

/**
 * 未登录异常对客户端的提示。
 * <p>
 * Sa-Token 生成的异常消息会带上请求中的令牌（例如“token 已被冻结：xxx”），不能原样返回给客户端或写进日志：
 * 一律按异常类型给出固定的提示，日志中只记类型。
 *
 * @author tuoyu
 */
public final class NotLoginMessage {

    /**
     * 客户端 ID 与令牌不一致（{@code RequestAuthenticator} 抛出）
     */
    public static final String CLIENT_MISMATCH = "-100";

    private NotLoginMessage() {
    }

    /**
     * @param e 未登录异常
     * @return 返回给客户端的提示，不含令牌
     */
    public static String of(NotLoginException e) {
        return switch (e.getType()) {
            case NotLoginException.NOT_TOKEN -> "请先登录";
            case NotLoginException.TOKEN_TIMEOUT,
                 NotLoginException.TOKEN_FREEZE -> "登录已过期，请重新登录";
            case NotLoginException.BE_REPLACED -> "当前账号已在其他设备登录，您已被强制下线";
            case NotLoginException.KICK_OUT -> "账号已被管理员强制下线";
            case CLIENT_MISMATCH -> "客户端ID与令牌不匹配，请重新登录";
            default -> "登录状态异常，请重新登录";
        };
    }

}
