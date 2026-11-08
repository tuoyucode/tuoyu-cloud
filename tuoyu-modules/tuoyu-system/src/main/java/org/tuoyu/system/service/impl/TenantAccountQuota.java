package org.tuoyu.system.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.tenant.core.TenantHelper;
import org.tuoyu.system.domain.SysTenant;
import org.tuoyu.system.mapper.SysTenantMapper;
import org.tuoyu.system.mapper.SysUserMapper;

/**
 * 租户用户数上限：新增、导入、注册用户前检查当前租户的用户数。
 * 上限为空表示不限；未开启多租户时不检查。并发新增时可能短暂超出 1 ~ 2 个，不做加锁
 *
 * @author tuoyu
 */
@Component
@RequiredArgsConstructor
public class TenantAccountQuota {

    private final SysTenantMapper tenantMapper;

    private final SysUserMapper userMapper;

    /**
     * 当前租户再新增一个用户是否超出上限，超出时拒绝
     */
    public void checkBeforeAdd() {
        if (!TenantHelper.isEnabled()) {
            return;
        }
        String tenantId = TenantHelper.currentTenantId();
        if (tenantId == null) {
            return;
        }
        SysTenant tenant = TenantHelper.ignore(() -> tenantMapper.selectById(tenantId));
        if (tenant == null || tenant.getAccountCount() == null) {
            return;
        }
        // 用户表按租户隔离，统计的是当前租户的用户（不含已删除）
        long used = userMapper.selectCount(null);
        if (used >= tenant.getAccountCount()) {
            throw new ServiceException("租户用户数已达上限（" + tenant.getAccountCount() + "），不能再新增用户");
        }
    }

}
