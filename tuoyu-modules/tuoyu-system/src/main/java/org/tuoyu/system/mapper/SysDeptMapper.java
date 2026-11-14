package org.tuoyu.system.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.yulichang.base.MPJBaseMapper;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRoleDept;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 部门数据层。数据权限只按部门列。
 * <p>
 * 部门及下级的计算（{@link #selectDeptAndChildById}）是数据权限的输入，不能标注数据权限，否则改写时递归。
 *
 * @author tuoyu
 */
public interface SysDeptMapper extends BaseMapper<SysDept>, MPJBaseMapper<SysDept> {

    /**
     * 查询部门列表
     *
     * @param queryWrapper 条件
     * @return 部门
     */
    @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
    default List<SysDept> selectDeptList(Wrapper<SysDept> queryWrapper) {
        return this.selectList(queryWrapper);
    }

    /**
     * 分页查询部门
     *
     * @param page         分页
     * @param queryWrapper 条件
     * @return 分页结果
     */
    @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
    default Page<SysDept> selectPageDeptList(Page<SysDept> page, Wrapper<SysDept> queryWrapper) {
        return this.selectPage(page, queryWrapper);
    }

    /**
     * 数据权限内是否有该部门
     *
     * @param deptId 部门 ID
     * @return 数据权限内该 ID 的部门数
     */
    @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
    default long countDeptById(Long deptId) {
        return this.selectCount(QueryBuilder.lambda(SysDept.class).eq(SysDept::getDeptId, deptId).build());
    }

    /**
     * 部门及其全部下级的 ID
     *
     * @param parentId 部门 ID
     * @return 下级部门的 ID，末尾为部门本身（可修改的列表）
     */
    default List<Long> selectDeptAndChildById(Long parentId) {
        List<SysDept> children = this.selectList(QueryBuilder.lambda(SysDept.class)
            .select(SysDept::getDeptId)
            .findInSet(parentId, "ancestors")
            .build());
        List<Long> ids = new ArrayList<>(children.size() + 1);
        for (SysDept child : children) {
            ids.add(child.getDeptId());
        }
        ids.add(parentId);
        return ids;
    }

    /**
     * 角色已选的部门 ID（回显，不看角色状态）
     *
     * @param roleId            角色 ID
     * @param deptCheckStrictly 父子联动时只回显叶子：去掉作为其他结果上级的部门
     * @return 部门 ID，按上级、顺序排列
     */
    default List<Long> selectDeptListByRoleId(Long roleId, boolean deptCheckStrictly) {
        List<SysDept> depts = this.selectJoinList(SysDept.class, QueryBuilder.lambdaJoin("d", SysDept.class)
            .distinct()
            .select(SysDept::getDeptId, SysDept::getParentId, SysDept::getOrderNum)
            .leftJoin(SysRoleDept.class, "srd", SysRoleDept::getDeptId, SysDept::getDeptId)
            .eq("srd", SysRoleDept::getRoleId, roleId)
            .orderByAsc("d", SysDept::getParentId)
            .orderByAsc("d", SysDept::getOrderNum)
            .build());
        Set<Long> parents = new HashSet<>();
        if (deptCheckStrictly) {
            for (SysDept dept : depts) {
                parents.add(dept.getParentId());
            }
        }
        List<Long> ids = new ArrayList<>(depts.size());
        for (SysDept dept : depts) {
            if (!parents.contains(dept.getDeptId())) {
                ids.add(dept.getDeptId());
            }
        }
        return ids;
    }

}
