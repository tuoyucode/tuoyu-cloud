package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.vo.RemoteTenantVo;

import java.util.List;

/**
 * 租户服务（ADR 0006）。查询租户本身不受租户隔离限制
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteTenantService {

    /**
     * @param tenantId 租户编码
     * @return 租户；不存在时为 null
     */
    RemoteTenantVo queryById(String tenantId);

    /**
     * 登录页可选择的租户：启用且未过期的租户；某个租户绑定了该域名时只返回它
     *
     * @param domain 访问域名，可以为空
     * @return 租户
     */
    List<RemoteTenantVo> queryLoginTenants(String domain);

    /**
     * @return 启用且未过期的租户编码（{@code @TenantJob} 按它们依次执行）
     */
    List<String> selectActiveTenantIds();

}
