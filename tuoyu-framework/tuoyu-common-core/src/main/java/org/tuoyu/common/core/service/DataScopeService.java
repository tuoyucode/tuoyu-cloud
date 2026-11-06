package org.tuoyu.common.core.service;

/**
 * 数据权限所需的数据：自定义数据范围的部门、部门及下级部门。
 * <p>
 * tuoyu-common-mybatis 只依赖本接口；实现在 tuoyu-common-service-impl 中，通过跨模块调用由 tuoyu-system 提供。
 *
 * @author tuoyu
 */
public interface DataScopeService {

    /**
     * @param roleId 角色 ID
     * @return 角色自定义数据范围的部门 ID，逗号分隔；没有时为空
     */
    String getRoleCustom(Long roleId);

    /**
     * @param deptId 部门 ID
     * @return 部门及其下级部门的 ID，逗号分隔；没有时为空
     */
    String getDeptAndChild(Long deptId);

}
