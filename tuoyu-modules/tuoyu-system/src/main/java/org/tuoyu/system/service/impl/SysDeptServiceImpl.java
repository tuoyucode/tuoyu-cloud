package org.tuoyu.system.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.tree.Tree;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.CacheNames;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.core.utils.TreeBuildUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.common.redis.utils.CacheUtils;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.SysRole;
import org.tuoyu.system.domain.SysUser;
import org.tuoyu.system.domain.bo.SysDeptBo;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.mapper.SysRoleMapper;
import org.tuoyu.system.mapper.SysUserMapper;
import org.tuoyu.system.service.ISysDeptService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 部门服务实现。列表、树与数据权限检查经带数据权限的 Mapper 方法执行
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl implements ISysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;

    @Override
    public PageResult<SysDeptVo> selectPageDeptList(SysDeptBo dept, PageParam pageParam) {
        return PageResults.of(deptMapper.selectPageDeptList(pageParam.toPage(SysDept.class), buildQueryWrapper(dept)),
            SysDeptVo.class);
    }

    @Override
    public List<SysDeptVo> selectDeptList(SysDeptBo dept) {
        return BeanMapper.mapList(deptMapper.selectDeptList(buildQueryWrapper(dept)), SysDeptVo.class);
    }

    @Override
    public List<Tree<Long>> selectDeptTreeList(SysDeptBo dept) {
        List<SysDept> depts = deptMapper.selectDeptList(buildQueryWrapper(dept));
        if (depts == null || depts.isEmpty()) {
            return new ArrayList<>();
        }
        return TreeBuildUtils.buildMultiRoot(depts, SysDept::getDeptId, SysDept::getParentId, (node, tree) -> {
            tree.setId(node.getDeptId())
                .setParentId(node.getParentId())
                .setName(node.getDeptName())
                .setWeight(node.getOrderNum());
            tree.putExtra("disabled", SystemConstants.DISABLE.equals(node.getStatus()));
        });
    }

    @Override
    public List<Long> selectDeptListByRoleId(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new ServiceException("角色不存在", HttpStatus.NOT_FOUND);
        }
        return deptMapper.selectDeptListByRoleId(roleId, Boolean.TRUE.equals(role.getDeptCheckStrictly()));
    }

    @Override
    @Cacheable(cacheNames = CacheNames.SYS_DEPT, key = "#deptId")
    public SysDeptVo selectDeptById(Long deptId) {
        SysDeptVo dept = BeanMapper.map(deptMapper.selectById(deptId), SysDeptVo.class);
        if (dept == null) {
            return null;
        }
        if (dept.getParentId() != null) {
            SysDept parent = deptMapper.selectOne(QueryBuilder.lambda(SysDept.class)
                .select(SysDept::getDeptName)
                .eq(SysDept::getDeptId, dept.getParentId())
                .build());
            dept.setParentName(parent == null ? null : parent.getDeptName());
        }
        return dept;
    }

    @Override
    public List<SysDeptVo> selectDeptByIds(List<Long> deptIds) {
        return BeanMapper.mapList(deptMapper.selectDeptList(QueryBuilder.lambda(SysDept.class)
            .select(SysDept::getDeptId, SysDept::getDeptName, SysDept::getLeader)
            .eq(SysDept::getStatus, SystemConstants.NORMAL)
            .inIfNotEmpty(SysDept::getDeptId, deptIds)
            .build()), SysDeptVo.class);
    }

    @Override
    public String selectDeptNameByIds(String deptIds) {
        if (StringUtils.isBlank(deptIds)) {
            return StringUtils.EMPTY;
        }
        ISysDeptService self = SpringUtils.getAopProxy(this);
        List<String> names = new ArrayList<>();
        for (Long deptId : StringUtils.splitTo(deptIds, Convert::toLong)) {
            if (deptId == null) {
                continue;
            }
            SysDeptVo dept = self.selectDeptById(deptId);
            if (dept != null) {
                names.add(dept.getDeptName());
            }
        }
        return String.join(StringUtils.SEPARATOR, names);
    }

    @Override
    public long selectNormalChildrenDeptById(Long deptId) {
        return deptMapper.selectCount(QueryBuilder.lambda(SysDept.class)
            .eq(SysDept::getStatus, SystemConstants.NORMAL)
            .findInSet(deptId, "ancestors")
            .build());
    }

    @Override
    public boolean hasChildByDeptId(Long deptId) {
        return deptMapper.exists(QueryBuilder.lambda(SysDept.class).eq(SysDept::getParentId, deptId).build());
    }

    @Override
    public boolean checkDeptExistUser(Long deptId) {
        return userMapper.exists(QueryBuilder.lambda(SysUser.class).eq(SysUser::getDeptId, deptId).build());
    }

    @Override
    public boolean checkDeptNameUnique(SysDeptBo dept) {
        return !deptMapper.exists(QueryBuilder.lambda(SysDept.class)
            .eq(SysDept::getDeptName, dept.getDeptName())
            .eq(SysDept::getParentId, dept.getParentId())
            .neIfPresent(SysDept::getDeptId, dept.getDeptId())
            .build());
    }

    @Override
    public void checkDeptDataScope(Long deptId) {
        if (deptId == null || UserContext.isSuperAdmin()) {
            return;
        }
        if (deptMapper.countDeptById(deptId) == 0) {
            throw new ServiceException("没有权限访问部门数据！");
        }
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SYS_DEPT_AND_CHILD, allEntries = true)
    public void insertDept(SysDeptBo bo) {
        SysDept parent = deptMapper.selectById(bo.getParentId());
        if (parent == null) {
            throw new ServiceException("父部门不存在");
        }
        if (!SystemConstants.NORMAL.equals(parent.getStatus())) {
            throw new ServiceException("部门停用，不允许新增");
        }
        SysDept dept = BeanMapper.map(bo, SysDept.class);
        dept.setAncestors(parent.getAncestors() + StringUtils.SEPARATOR + bo.getParentId());
        deptMapper.insert(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.SYS_DEPT, key = "#bo.deptId"),
        @CacheEvict(cacheNames = CacheNames.SYS_DEPT_AND_CHILD, allEntries = true)
    })
    public void updateDept(SysDeptBo bo) {
        SysDept old = deptMapper.selectById(bo.getDeptId());
        if (old == null) {
            throw new ServiceException("部门不存在，无法修改");
        }
        SysDept dept = BeanMapper.map(bo, SysDept.class);
        if (Objects.equals(old.getParentId(), bo.getParentId())) {
            dept.setAncestors(old.getAncestors());
        } else {
            checkDeptDataScope(bo.getParentId());
            SysDept newParent = deptMapper.selectById(bo.getParentId());
            if (newParent != null) {
                if (StringUtils.splitList(newParent.getAncestors()).contains(String.valueOf(bo.getDeptId()))) {
                    throw new ServiceException("修改部门'{}'失败，上级部门不能是自己的下级部门", bo.getDeptName());
                }
                String newAncestors = newParent.getAncestors() + StringUtils.SEPARATOR + newParent.getDeptId();
                updateChildrenAncestors(bo.getDeptId(), newAncestors, old.getAncestors());
                dept.setAncestors(newAncestors);
            }
        }
        if (deptMapper.updateById(dept) == 0) {
            throw new ServiceException("修改部门'{}'失败，部门不存在", bo.getDeptName());
        }
        // 全局更新策略为 NOT_NULL，按 ID 更新不会写入 null；修改接口提交完整表单，未选负责人表示清空
        if (old.getLeader() != null && bo.getLeader() == null) {
            deptMapper.update(Wrappers.<SysDept>lambdaUpdate()
                .set(SysDept::getLeader, null)
                .eq(SysDept::getDeptId, bo.getDeptId()));
        }
        String ancestors = dept.getAncestors();
        if (SystemConstants.NORMAL.equals(dept.getStatus()) && StringUtils.isNotEmpty(ancestors)
            && !StringUtils.equals(SystemConstants.ROOT_DEPT_ANCESTORS, ancestors)) {
            enableParents(ancestors);
        }
    }

    @Override
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheNames.SYS_DEPT, key = "#deptId"),
        @CacheEvict(cacheNames = CacheNames.SYS_DEPT_AND_CHILD, allEntries = true)
    })
    public void deleteDeptById(Long deptId) {
        if (deptMapper.deleteById(deptId) == 0) {
            throw new ServiceException("删除部门失败，部门不存在");
        }
    }

    /**
     * 更换上级后改写全部下级的祖级：把其中的旧祖级前缀换成新祖级，并清除它们的部门缓存
     *
     * @param deptId       被移动的部门
     * @param newAncestors 被移动部门的新祖级
     * @param oldAncestors 被移动部门的旧祖级
     */
    private void updateChildrenAncestors(Long deptId, String newAncestors, String oldAncestors) {
        List<SysDept> children = deptMapper.selectList(QueryBuilder.lambda(SysDept.class)
            .select(SysDept::getDeptId, SysDept::getAncestors)
            .findInSet(deptId, "ancestors")
            .build());
        if (children == null || children.isEmpty()) {
            return;
        }
        List<SysDept> updates = new ArrayList<>(children.size());
        for (SysDept child : children) {
            SysDept update = new SysDept();
            update.setDeptId(child.getDeptId());
            update.setAncestors(StringUtils.replaceOnce(child.getAncestors(), oldAncestors, newAncestors));
            updates.add(update);
        }
        deptMapper.updateById(updates);
        for (SysDept update : updates) {
            CacheUtils.evict(CacheNames.SYS_DEPT, update.getDeptId());
        }
    }

    /**
     * 启用部门时一并启用它的全部上级，并清除这些上级的部门缓存
     *
     * @param ancestors 部门的祖级
     */
    private void enableParents(String ancestors) {
        List<Long> parentIds = StringUtils.splitTo(ancestors, Convert::toLong);
        deptMapper.update(Wrappers.<SysDept>lambdaUpdate()
            .set(SysDept::getStatus, SystemConstants.NORMAL)
            .in(SysDept::getDeptId, parentIds));
        for (Long parentId : parentIds) {
            if (parentId != null && parentId > 0) {
                CacheUtils.evict(CacheNames.SYS_DEPT, parentId);
            }
        }
    }

    /**
     * 列表条件：ID、上级相等，名称、类别模糊，状态相等，创建时间区间，所属部门含下级
     */
    private LambdaQueryWrapper<SysDept> buildQueryWrapper(SysDeptBo bo) {
        LambdaQueryBuilder<SysDept> query = QueryBuilder.lambda(SysDept.class)
            .eqIfPresent(SysDept::getDeptId, bo.getDeptId())
            .eqIfPresent(SysDept::getParentId, bo.getParentId())
            .likeIfText(SysDept::getDeptName, bo.getDeptName())
            .likeIfText(SysDept::getDeptCategory, bo.getDeptCategory())
            .eqIfText(SysDept::getStatus, bo.getStatus())
            .betweenParams(SysDept::getCreateTime, bo.getParams(), "beginTime", "endTime")
            .orderByAsc(SysDept::getAncestors, SysDept::getParentId, SysDept::getOrderNum, SysDept::getDeptId);
        if (bo.getBelongDeptId() != null) {
            query.in(SysDept::getDeptId, deptMapper.selectDeptAndChildById(bo.getBelongDeptId()));
        }
        return query.build();
    }

}
