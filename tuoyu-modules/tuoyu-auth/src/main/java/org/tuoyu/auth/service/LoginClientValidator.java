package org.tuoyu.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tuoyu.auth.form.LoginRequest;
import org.tuoyu.auth.properties.AuthSecurityProperties;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.MessageUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.system.api.RemoteClientService;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 登录时校验客户端：存在、允许该授权类型、已启用；开启 {@code tuoyu.auth.client.require-secret} 时校验密钥
 *
 * @author tuoyu
 */
@Slf4j
@Service
public class LoginClientValidator {

    private final RemoteClientService remoteClientService;

    private final AuthSecurityProperties authSecurityProperties;

    public LoginClientValidator(RemoteClientService remoteClientService, AuthSecurityProperties authSecurityProperties) {
        this.remoteClientService = remoteClientService;
        this.authSecurityProperties = authSecurityProperties;
    }

    /**
     * @param request 登录请求
     * @return 校验通过的客户端
     * @throws ServiceException 客户端不存在、不允许该授权类型、已停用或密钥不符时
     */
    public RemoteClientVo require(LoginRequest request) {
        RemoteClientVo client = remoteClientService.queryByClientId(request.clientId());
        if (client == null || !allowsGrantType(client.getGrantType(), request.grantType())) {
            log.info("客户端 {} 不存在或不允许授权类型 {}", request.clientId(), request.grantType());
            throw new ServiceException(MessageUtils.message("auth.grant.type.error"));
        }
        if (!SystemConstants.NORMAL.equals(client.getStatus())) {
            throw new ServiceException(MessageUtils.message("auth.grant.type.blocked"));
        }
        if (authSecurityProperties.getClient().isRequireSecret()
            && !secretMatches(client.getClientSecret(), request.clientSecret())) {
            throw new ServiceException(MessageUtils.message("auth.client.secret.error"));
        }
        return client;
    }

    /**
     * 客户端的授权类型以逗号分隔，按项精确匹配（忽略各项首尾空白）
     *
     * @param configured 客户端配置的授权类型
     * @param grantType  请求的授权类型
     * @return 是否允许
     */
    static boolean allowsGrantType(String configured, String grantType) {
        if (StringUtils.isBlank(configured) || StringUtils.isBlank(grantType)) {
            return false;
        }
        for (String item : configured.split(",")) {
            if (item.trim().equals(grantType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 常量时间比较：比对耗时不暴露密钥相同前缀的长度
     */
    private static boolean secretMatches(String expected, String actual) {
        if (StringUtils.isEmpty(expected) || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

}
