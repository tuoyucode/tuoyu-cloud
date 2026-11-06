package org.tuoyu.auth.service;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.ObjectUtil;
import org.tuoyu.auth.domain.vo.LoginVo;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;

import java.util.function.Consumer;

/**
 * 授权策略
 *
 * @author Michelle.Chung
 */
public interface IAuthStrategy {

    String BASE_NAME = "AuthStrategy";

    /**
     * 登录
     *
     * @param body      登录对象
     * @param client    授权管理视图对象
     * @param grantType 授权类型
     * @return 登录验证信息
     */
    static LoginVo login(String body, RemoteClientVo client, String grantType) {
        // 授权类型和客户端id
        String beanName = grantType + BASE_NAME;
        if (!SpringUtils.containsBean(beanName)) {
            throw new ServiceException("授权类型不正确!");
        }
        IAuthStrategy instance = SpringUtils.getBean(beanName);
        return instance.login(body, client);
    }

    /**
     * 按客户端配置构建统一登录参数。
     */
    static SaLoginParameter buildLoginParameter(RemoteClientVo client) {
        return buildLoginParameter(client, null);
    }

    /**
     * 按客户端配置构建统一登录参数，并预留自定义扩展入口。
     */
    static SaLoginParameter buildLoginParameter(RemoteClientVo client, Consumer<SaLoginParameter> customizer) {
        SaLoginParameter model = new SaLoginParameter();
        model.setDeviceType(client.getDeviceType());
        // 客户端没有配置超时时沿用 Sa-Token 的全局配置（直接设置 null 会在拆箱时空指针）
        if (client.getTimeout() != null) {
            model.setTimeout(client.getTimeout());
        }
        if (client.getActiveTimeout() != null) {
            model.setActiveTimeout(client.getActiveTimeout());
        }
        model.setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
        model.setExtra(LoginHelper.CLIENT_ACCESS_PATH_KEY, client.getAccessPath());
        model.setExtra(LoginHelper.CLIENT_IP_WHITELIST_KEY, client.getIpWhitelist());
        if (ObjectUtil.isNotNull(customizer)) {
            customizer.accept(model);
        }
        return model;
    }

    /**
     * 登录
     *
     * @param body   登录对象
     * @param client 授权管理视图对象
     * @return 登录验证信息
     */
    LoginVo login(String body, RemoteClientVo client);

}
