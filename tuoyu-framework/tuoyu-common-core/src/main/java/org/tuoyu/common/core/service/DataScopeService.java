package org.tuoyu.common.core.service;

/**
 * 数据权限需要的部门数据。
 *
 * @author tuoyu
 */
public interface DataScopeService {

    /**
     * @param roleId 角色 ID
     * @return 角色自定义数据范围中的部门 ID，逗号分隔；没有时为空
     */
    String getRoleCustom(Long roleId);

    /**
     * @param deptId 部门 ID
     * @return 该部门及其全部下级部门的 ID，逗号分隔
     */
    String getDeptAndChild(Long deptId);

}
