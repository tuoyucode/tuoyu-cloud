package org.tuoyu.system.api;

import org.tuoyu.common.rpc.annotation.RemoteApi;
import org.tuoyu.system.api.domain.bo.RemoteTaskAssigneeBo;
import org.tuoyu.system.api.domain.vo.RemoteTaskAssigneeVo;

/**
 * 工作流设计器获取任务执行人
 *
 * @author Lion Li
 */
@RemoteApi
public interface RemoteTaskAssigneeService {

    /**
     * 查询角色并返回任务指派的列表，支持分页
     *
     * @param taskQuery 查询条件
     * @return 办理人
     */
    RemoteTaskAssigneeVo selectRolesByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

    /**
     * 查询岗位并返回任务指派的列表，支持分页
     *
     * @param taskQuery 查询条件
     * @return 办理人
     */
    RemoteTaskAssigneeVo selectPostsByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

    /**
     * 查询部门并返回任务指派的列表，支持分页
     *
     * @param taskQuery 查询条件
     * @return 办理人
     */
    RemoteTaskAssigneeVo selectDeptsByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

    /**
     * 查询用户并返回任务指派的列表，支持分页
     *
     * @param taskQuery 查询条件
     * @return 办理人
     */
    RemoteTaskAssigneeVo selectUsersByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

}
