package org.tuoyu.system.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageQuery;
import org.tuoyu.system.domain.bo.SysTenantBo;
import org.tuoyu.system.domain.vo.SysTenantVo;

import java.util.Collection;
import java.util.List;

/**
 * 租户 服务层（ADR 0006）
 *
 * @author tuoyu
 */
public interface ISysTenantService {

    /**
     * 新增租户时创建的租户管理员角色的权限字符
     */
    String TENANT_ADMIN_ROLE_KEY = "tenant-admin";

    PageResult<SysTenantVo> selectPageList(SysTenantBo bo, PageQuery pageQuery);

    SysTenantVo selectById(String tenantId);

    /**
     * 新增租户，同时创建该租户的根部门、管理员角色（拥有套餐中的全部菜单）与管理员账号
     */
    void insertTenant(SysTenantBo bo);

    /**
     * 修改租户（租户编码不能修改）；套餐变化时同步租户管理员角色的菜单
     */
    void updateTenant(SysTenantBo bo);

    void updateStatus(String tenantId, String status);

    /**
     * 删除租户（逻辑删除，租户编码不能再次使用）；默认租户不能删除
     */
    void deleteByIds(Collection<String> tenantIds);

    /**
     * 把租户管理员角色的菜单同步为套餐中的菜单
     */
    void syncPackage(String tenantId);

    /**
     * @param packageId 套餐
     * @return 使用该套餐的租户
     */
    List<String> selectTenantIdsByPackage(Long packageId);

    /**
     * @param domain 访问域名，可以为空
     * @return 登录页可选择的租户（启用且未过期；绑定了该域名的租户存在时只返回它）
     */
    List<SysTenantVo> selectLoginTenants(String domain);

    /**
     * @return 启用且未过期的租户编码
     */
    List<String> selectActiveTenantIds();

}
