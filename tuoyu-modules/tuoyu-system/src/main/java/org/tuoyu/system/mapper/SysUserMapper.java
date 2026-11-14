package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.apache.ibatis.annotations.Param;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.LambdaJoinQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.SysUserRole;
import org.tuoyu.system.domain.bo.SysUserBo;
import org.tuoyu.system.domain.vo.SysUserExportVo;
import org.tuoyu.system.domain.vo.SysUserVo;

import java.util.List;
import java.util.Map;

/**
 * 用户数据层。
 * <p>
 * 数据权限由方法上的 {@link DataPermission} 声明，经 Spring 代理调用这些方法时生效（部门列、创建人列）。
 * 按条件更新的 {@code update(Wrapper)} 在 BaseMapper 中是默认方法，在 MyBatis 代理内部转调两个参数的
 * {@code update}，不经过 Spring 代理，所以这里覆盖并标注它，修改状态、资料、头像与口令时数据权限才生效。
 *
 * @author tuoyu
 */
public interface SysUserMapper extends BaseMapper<SysUser>, MPJBaseMapper<SysUser> {

    /**
     * 分页查询用户
     *
     * @param page         分页
     * @param queryWrapper 条件
     * @return 分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<SysUser> selectPageUserList(Page<SysUser> page, Wrapper<SysUser> queryWrapper) {
        return this.selectPage(page, queryWrapper);
    }

    /**
     * 查询用户列表
     *
     * @param queryWrapper 条件
     * @return 用户
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<SysUser> selectUserList(Wrapper<SysUser> queryWrapper) {
        return this.selectList(queryWrapper);
    }

    /**
     * 查询导出的用户（带部门负责人的账号）
     *
     * @param user    条件
     * @param deptIds 部门范围，为空时不限
     * @return 导出的行
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "d.dept_id"),
        @DataColumn(key = "userName", value = "u.create_by")
    })
    default List<SysUserExportVo> selectUserExportList(SysUserBo user, List<Long> deptIds) {
        return this.selectJoinList(SysUserExportVo.class, exportQuery(user, deptIds)
            .orderByAsc("u", SysUser::getUserId)
            .build());
    }

    /**
     * 统计导出的行数（条件与 {@link #selectUserExportList} 相同）
     *
     * @param user    条件
     * @param deptIds 部门范围，为空时不限
     * @return 行数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "d.dept_id"),
        @DataColumn(key = "userName", value = "u.create_by")
    })
    default long countUserExportList(SysUserBo user, List<Long> deptIds) {
        // 经分页插件统计（不直接 COUNT）：与分页查询的统计方式一致，租户与数据权限条件照常生效
        Page<SysUserExportVo> counted = this.selectJoinPage(PageParam.countOnly(), SysUserExportVo.class,
            exportQuery(user, deptIds).build());
        return counted.getTotal();
    }

    /**
     * 分页查询已分配某个角色的用户
     *
     * @param page 分页
     * @param user 条件（roleId 为角色）
     * @return 分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "d.dept_id"),
        @DataColumn(key = "userName", value = "u.create_by")
    })
    default Page<SysUserVo> selectAllocatedList(Page<SysUserVo> page, SysUserBo user) {
        return this.selectJoinPage(page, SysUserVo.class, roleUserQuery(user)
            .eqIfPresent("r", SysRole::getRoleId, user.getRoleId())
            .orderByAsc("u", SysUser::getUserId)
            .build());
    }

    /**
     * 分页查询不在给定名单中的用户（未分配某个角色的用户）
     *
     * @param page    分页
     * @param user    条件
     * @param userIds 排除的用户，为空时不排除
     * @return 分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "d.dept_id"),
        @DataColumn(key = "userName", value = "u.create_by")
    })
    default Page<SysUserVo> selectUnallocatedList(Page<SysUserVo> page, SysUserBo user, List<Long> userIds) {
        return this.selectJoinPage(page, SysUserVo.class, roleUserQuery(user)
            .notInIfNotEmpty("u", SysUser::getUserId, userIds)
            .orderByAsc("u", SysUser::getUserId)
            .build());
    }

    /**
     * 数据权限内是否有该用户
     *
     * @param userId 用户 ID
     * @return 数据权限内该 ID 的用户数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long countUserById(Long userId) {
        return this.selectCount(QueryBuilder.lambda(SysUser.class).eq(SysUser::getUserId, userId).build());
    }

    /**
     * 按条件更新（声明在这里只为标注数据权限）
     */
    @Override
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    int update(@Param(Constants.ENTITY) SysUser user, @Param(Constants.WRAPPER) Wrapper<SysUser> updateWrapper);

    /**
     * 按条件更新：转调两个参数的版本，在这里声明使数据权限经 Spring 代理生效
     */
    @Override
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default int update(@Param(Constants.WRAPPER) Wrapper<SysUser> updateWrapper) {
        return this.update(null, updateWrapper);
    }

    /**
     * 按 ID 更新（声明在这里只为标注数据权限）
     */
    @Override
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    int updateById(@Param(Constants.ENTITY) SysUser user);

    /**
     * 导出的联表与条件：用户、所属部门、部门负责人
     */
    private static LambdaJoinQueryBuilder<SysUser> exportQuery(SysUserBo user, List<Long> deptIds) {
        Map<String, Object> params = user.getParams();
        return QueryBuilder.lambdaJoin("u", SysUser.class)
            .selectAll(SysUser.class)
            .selectAs("u1", SysUser::getUserName, SysUserExportVo::getLeaderName)
            .leftJoin(SysDept.class, "d", SysDept::getDeptId, SysUser::getDeptId)
            .leftJoin(SysUser.class, "u1", SysUser::getUserId, SysDept::getLeader)
            .likeIfText("u", SysUser::getUserName, user.getUserName())
            .likeIfText("u", SysUser::getNickName, user.getNickName())
            .likeIfText("u", SysUser::getPhoneNumber, user.getPhoneNumber())
            .eqIfText("u", SysUser::getStatus, user.getStatus())
            .betweenParams("u", SysUser::getCreateTime, params, "beginTime", "endTime")
            .inIfNotEmpty("u", SysUser::getDeptId, deptIds);
    }

    /**
     * 角色授权用户列表的联表与公共条件：用户、部门、用户角色、角色
     */
    private static LambdaJoinQueryBuilder<SysUser> roleUserQuery(SysUserBo user) {
        return QueryBuilder.lambdaJoin("u", SysUser.class)
            .distinct()
            .selectAll(SysUser.class)
            .leftJoin(SysDept.class, "d", SysDept::getDeptId, SysUser::getDeptId)
            .leftJoin(SysUserRole.class, "sur", SysUserRole::getUserId, SysUser::getUserId)
            .leftJoin(SysRole.class, "r", SysRole::getRoleId, SysUserRole::getRoleId)
            .likeIfText("u", SysUser::getUserName, user.getUserName())
            .eqIfText("u", SysUser::getStatus, user.getStatus())
            .likeIfText("u", SysUser::getPhoneNumber, user.getPhoneNumber());
    }

}
