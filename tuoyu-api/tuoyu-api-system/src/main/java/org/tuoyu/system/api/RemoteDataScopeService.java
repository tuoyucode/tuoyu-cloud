package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;

/**
 * 数据权限服务：数据权限改写 SQL 时取部门范围。
 * <p>
 * 结果原样拼进 SQL，只会是逗号分隔的整数或 {@code -1}（查不到任何数据）。
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteDataScopeService {

    /**
     * @param roleId 角色 ID
     * @return 角色的自定义部门 ID（逗号分隔）；没有时为 {@code -1}
     */
    String getRoleCustom(Long roleId);

    /**
     * @param deptId 部门 ID
     * @return 部门及其全部下级的 ID（逗号分隔）；没有时为 {@code -1}
     */
    String getDeptAndChild(Long deptId);

}
