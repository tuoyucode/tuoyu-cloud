package org.tuoyu.common.core.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.service.DataScopeService;
import org.tuoyu.system.api.RemoteDataScopeService;

/**
 * 数据权限所需的数据，由 tuoyu-system 提供（tuoyu-common-mybatis 只依赖 {@link DataScopeService}）
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    private final RemoteDataScopeService remoteDataScopeService;

    @Override
    public String getRoleCustom(Long roleId) {
        return remoteDataScopeService.getRoleCustom(roleId);
    }

    @Override
    public String getDeptAndChild(Long deptId) {
        return remoteDataScopeService.getDeptAndChild(deptId);
    }

}
