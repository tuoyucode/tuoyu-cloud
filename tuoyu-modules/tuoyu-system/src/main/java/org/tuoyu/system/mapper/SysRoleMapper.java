package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Param;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.vo.SysRoleVo;

import java.util.Collection;
import java.util.List;

/**
 * 角色数据层。数据权限按角色的创建部门与创建人（经 Spring 代理调用标注的方法时生效）
 *
 * @author tuoyu
 */
public interface SysRoleMapper extends BaseMapper<SysRole>, MPJBaseMapper<SysRole> {

    /**
     * 分页查询角色
     *
     * @param page         分页
     * @param queryWrapper 条件
     * @return 分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<SysRole> selectPageRoleList(@Param("page") Page<SysRole> page,
                                             @Param(Constants.WRAPPER) Wrapper<SysRole> queryWrapper) {
        return this.selectPage(page, queryWrapper);
    }

    /**
     * 查询角色列表
     *
     * @param queryWrapper 条件
     * @return 角色
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<SysRole> selectRoleList(@Param(Constants.WRAPPER) Wrapper<SysRole> queryWrapper) {
        return this.selectList(queryWrapper);
    }

    /**
     * 数据权限内这些角色的个数
     *
     * @param roleIds 角色 ID
     * @return 个数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long selectRoleCount(Collection<Long> roleIds) {
        return this.selectCount(QueryBuilder.lambda(SysRole.class).in(SysRole::getRoleId, roleIds).build());
    }

    /**
     * 按 ID 查询数据权限内的角色
     *
     * @param roleId 角色 ID
     * @return 角色；不存在或不在数据权限内时为 null
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default SysRole selectRoleById(Long roleId) {
        return this.selectById(roleId);
    }

    /**
     * 用户的全部角色（含停用的角色，用于回显；需要只取正常角色时由调用方过滤）
     *
     * @param userId 用户 ID
     * @return 角色
     */
    default List<SysRoleVo> selectRolesByUserId(Long userId) {
        return this.selectJoinList(SysRoleVo.class, QueryBuilder.lambdaJoin("r", SysRole.class)
            .select(SysRole::getRoleId, SysRole::getRoleName, SysRole::getRoleKey, SysRole::getRoleSort,
                SysRole::getDataScope, SysRole::getStatus)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getRoleId, SysRole::getRoleId)
            .eq("sur", SysUserRole::getUserId, userId)
            .build());
    }

}
