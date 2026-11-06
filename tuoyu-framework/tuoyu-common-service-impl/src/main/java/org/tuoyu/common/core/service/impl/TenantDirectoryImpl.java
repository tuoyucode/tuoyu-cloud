package org.tuoyu.common.core.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.tenant.core.TenantDirectory;
import org.tuoyu.system.api.RemoteTenantService;

import java.util.List;

/**
 * 租户清单，由 tuoyu-system 提供（{@code @TenantJob} 按它依次执行）
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class TenantDirectoryImpl implements TenantDirectory {

    private final RemoteTenantService remoteTenantService;

    @Override
    public List<String> activeTenantIds() {
        return remoteTenantService.selectActiveTenantIds();
    }

}
