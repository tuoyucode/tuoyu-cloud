package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.system.api.RemoteTenantService;
import org.tuoyu.system.api.domain.vo.RemoteTenantVo;
import org.tuoyu.system.domain.vo.SysTenantVo;
import org.tuoyu.system.service.ISysTenantService;

import java.util.List;

/**
 * 租户服务（跨模块）。租户表平台共享，查询不受租户隔离限制
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemoteTenantServiceImpl implements RemoteTenantService {

    private final ISysTenantService tenantService;

    @Override
    public RemoteTenantVo queryById(String tenantId) {
        SysTenantVo tenant = tenantService.selectById(tenantId);
        return tenant == null ? null : toRemote(tenant);
    }

    @Override
    public List<RemoteTenantVo> queryLoginTenants(String domain) {
        return tenantService.selectLoginTenants(domain).stream().map(RemoteTenantServiceImpl::toRemote).toList();
    }

    @Override
    public List<String> selectActiveTenantIds() {
        return tenantService.selectActiveTenantIds();
    }

    private static RemoteTenantVo toRemote(SysTenantVo tenant) {
        RemoteTenantVo vo = new RemoteTenantVo();
        vo.setTenantId(tenant.getTenantId());
        vo.setTenantName(tenant.getTenantName());
        vo.setDomain(tenant.getDomain());
        vo.setStatus(tenant.getStatus());
        vo.setExpireTime(tenant.getExpireTime());
        return vo;
    }

}
