package org.tuoyu.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.api.RemoteTenantService;
import org.tuoyu.system.api.domain.vo.RemoteTenantVo;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 登录、注册时确定租户（ADR 0006 第 5 节）：开启多租户时必须指定租户，且租户存在、启用、未过期；
 * 之后的用户查询、登录日志等都在该租户中执行
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class TenantLoginGuard {

    private final RemoteTenantService remoteTenantService;

    /**
     * 在登录的租户中执行；未开启多租户时直接执行
     *
     * @param tenantId 请求中的租户
     * @param action   登录、注册等
     */
    public <T> T within(String tenantId, Supplier<T> action) {
        if (!TenantHelper.isEnabled()) {
            return action.get();
        }
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("请选择租户");
        }
        RemoteTenantVo tenant = TenantHelper.ignore(() -> remoteTenantService.queryById(tenantId.trim()));
        if (tenant == null) {
            throw new ServiceException("租户不存在");
        }
        if (!SystemConstants.NORMAL.equals(tenant.getStatus())) {
            throw new ServiceException("租户已停用，请联系管理员");
        }
        if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new ServiceException("租户已过期，请联系管理员");
        }
        return TenantHelper.dynamic(tenant.getTenantId(), action);
    }

}
