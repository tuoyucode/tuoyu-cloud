package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.bo.RemoteTaskAssigneeBo;
import org.tuoyu.system.api.domain.vo.RemoteTaskAssigneeVo;

/**
 * 工作流设计器选择办理人：按角色、岗位、部门、用户分页查询（按当前操作者的数据权限）
 *
 * @author tuoyu
 */
@RemoteApi
public interface RemoteTaskAssigneeService {

    /**
     * @param taskQuery 查询条件
     * @return 正常状态的角色
     */
    RemoteTaskAssigneeVo selectRolesByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

    /**
     * @param taskQuery 查询条件
     * @return 正常状态的岗位
     */
    RemoteTaskAssigneeVo selectPostsByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

    /**
     * @param taskQuery 查询条件
     * @return 正常状态的部门
     */
    RemoteTaskAssigneeVo selectDeptsByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

    /**
     * @param taskQuery 查询条件
     * @return 正常状态的用户
     */
    RemoteTaskAssigneeVo selectUsersByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

}
