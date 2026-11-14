package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.vo.RemoteDeptVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 部门服务
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteDeptService {

    /**
     * @param deptIds 逗号分隔的部门 ID
     * @return 逗号连接的部门名称（跳过不存在的部门）
     */
    String selectDeptNameByIds(String deptIds);

    /**
     * @param deptId 部门 ID
     * @return 部门负责人的用户 ID；部门不存在时为 null
     */
    Long selectDeptLeaderById(Long deptId);

    /**
     * @return 正常状态的部门
     */
    List<RemoteDeptVo> selectDeptsByList();

    /**
     * @param deptIds 部门 ID
     * @return 部门 ID → 部门名称
     */
    Map<Long, String> selectDeptNamesByIds(Collection<Long> deptIds);

}
