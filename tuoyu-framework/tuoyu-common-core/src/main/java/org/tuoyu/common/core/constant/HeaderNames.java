package org.tuoyu.common.core.constant;

/**
 * 框架约定的请求头名
 *
 * @author tuoyu
 */
public final class HeaderNames {

    /**
     * 客户端标识（对应客户端管理中的 clientId）。前端每个请求都带上，登录时也写进会话，
     * 服务端据此校验令牌与客户端是否一致、记录日志中的客户端
     */
    public static final String CLIENT_ID = "clientid";

    private HeaderNames() {
    }

}
