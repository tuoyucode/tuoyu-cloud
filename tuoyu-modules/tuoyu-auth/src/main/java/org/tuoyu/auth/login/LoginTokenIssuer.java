package org.tuoyu.auth.login;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.springframework.stereotype.Component;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;
import org.tuoyu.system.api.model.LoginUser;

/**
 * 为已核验的用户签发令牌：按客户端配置设置设备类型、有效期，并把客户端的访问限制写入令牌扩展信息
 * （各服务的登录校验按它们比对客户端 ID、可访问路径与来源 IP）。
 *
 * @author tuoyu
 */
@Component
public class LoginTokenIssuer {

    /**
     * 按客户端配置生成登录参数。客户端没有配置有效期时沿用 Sa-Token 的全局配置
     *
     * @param client 客户端
     * @return 登录参数
     */
    public static SaLoginParameter loginParameter(RemoteClientVo client) {
        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setDeviceType(client.getDeviceType());
        if (client.getTimeout() != null) {
            parameter.setTimeout(client.getTimeout());
        }
        if (client.getActiveTimeout() != null) {
            parameter.setActiveTimeout(client.getActiveTimeout());
        }
        parameter.setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
        parameter.setExtra(LoginHelper.CLIENT_ACCESS_PATH_KEY, client.getAccessPath());
        parameter.setExtra(LoginHelper.CLIENT_IP_WHITELIST_KEY, client.getIpWhitelist());
        return parameter;
    }

    /**
     * 登录并签发令牌
     *
     * @param login  已核验的登录
     * @param client 客户端
     * @return 登录结果
     */
    public LoginVo issue(VerifiedLogin login, RemoteClientVo client) {
        LoginUser user = login.user();
        user.setClientKey(client.getClientKey());
        user.setDeviceType(client.getDeviceType());
        // 密码哈希只在核验凭证时使用，登录用户会存进令牌会话（Redis），不带上它
        user.setPassword(null);
        LoginHelper.login(user, loginParameter(client));
        return LoginVo.of(StpUtil.getTokenValue(), StpUtil.getTokenTimeout(), client.getClientId(), login.openid());
    }

}
