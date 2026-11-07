package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Param;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.vo.SysRoleVo;

import java.util.Collection;
import java.util.List;

/**
 * 角色表 数据层
 *
 * @author Lion Li
 */
public interface SysRoleMapper extends BaseMapper<SysRole>, MPJBaseMapper<SysRole> {

    /**
     * 分页查询角色列表
     *
     * @param page         分页对象
     * @param queryWrapper 查询条件
     * @return 包含角色信息的分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<SysRole> selectPageRoleList(@Param("page") Page<SysRole> page, @Param(Constants.WRAPPER) Wrapper<SysRole> queryWrapper) {
        return this.selectPage(page, queryWrapper);
    }

    /**
     * 根据条件查询角色数据
     *
     * @param queryWrapper 查询条件
     * @return 角色数据集合信息
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<SysRole> selectRoleList(@Param(Constants.WRAPPER) Wrapper<SysRole> queryWrapper) {
        return this.selectList(queryWrapper);
    }

    /**
     * 根据角色ID集合查询角色数量
     *
     * @param roleIds 角色ID列表
     * @return 匹配的角色数量
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long selectRoleCount(Collection<Long> roleIds) {
        return this.selectCount(QueryBuilder.lambda(SysRole.class).in(SysRole::getRoleId, roleIds).build());
    }

    /**
     * 根据角色ID查询角色信息
     *
     * @param roleId 角色ID
     * @return 对应的角色信息
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default SysRole selectRoleById(Long roleId) {
        return this.selectById(roleId);
    }

    /**
     * 根据用户ID查询角色
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    default List<SysRoleVo> selectRolesByUserId(Long userId) {
        return this.selectJoinList(SysRoleVo.class, QueryBuilder.lambdaJoin("r", SysRole.class)
            .select(SysRole::getRoleId, SysRole::getRoleName, SysRole::getRoleKey,
                SysRole::getRoleSort, SysRole::getDataScope, SysRole::getStatus)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getRoleId, SysRole::getRoleId)
            .eq("sur", SysUserRole::getUserId, userId)
            .build());
    }

}
