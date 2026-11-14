package org.tuoyu.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.bo.DeptDemoBo;
import org.tuoyu.system.domain.vo.DeptDemoVo;
import org.tuoyu.system.domain.DeptDemo;
import org.tuoyu.system.mapper.DeptDemoMapper;
import org.tuoyu.system.service.IDeptDemoService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class DeptDemoServiceImpl implements IDeptDemoService {

    private final DeptDemoMapper deptDemoMapper;

    @Override
    public DeptDemoVo queryById(Long deptId) {
        return BeanMapper.map(deptDemoMapper.selectById(deptId), DeptDemoVo.class);
    }

    @Override
    public List<DeptDemoVo> queryList(DeptDemoBo bo) {
        return BeanMapper.mapList(deptDemoMapper.selectList(buildQueryWrapper(bo, true)), DeptDemoVo.class);
    }

    private LambdaQueryWrapper<DeptDemo> buildQueryWrapper(DeptDemoBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(DeptDemo.class)
            .likeIfText(DeptDemo::getDeptName, bo.getDeptName())
            .eqIfText(DeptDemo::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, DeptDemo::getAncestors)
            .orderByAsc(defaultOrder, DeptDemo::getParentId)
            .orderByAsc(defaultOrder, DeptDemo::getOrderNum)
            .orderByAsc(defaultOrder, DeptDemo::getDeptId)
            .build();
    }

    @Override
    public void insertByBo(DeptDemoBo bo) {
        DeptDemo add = BeanMapper.map(bo, DeptDemo.class);
        fillTreeMetaBeforeSave(add, false);
        validEntityBeforeSave(add);
        deptDemoMapper.insert(add);
        bo.setDeptId(add.getDeptId());
    }

    @Override
    public void updateByBo(DeptDemoBo bo) {
        DeptDemo update = BeanMapper.map(bo, DeptDemo.class);
        fillTreeMetaBeforeSave(update, true);
        validEntityBeforeSave(update);
        if (deptDemoMapper.updateById(update) == 0) {
            throw new ServiceException("修改部门失败，数据不存在");
        }
    }

    private void validEntityBeforeSave(DeptDemo entity) {
    }

    private void fillTreeMetaBeforeSave(DeptDemo entity, boolean updateMode) {
        if (entity.getParentId() == null) {
            entity.setParentId(0L);
        }
        if (ObjectUtil.equal(entity.getDeptId(), entity.getParentId())) {
            throw new ServiceException("部门父节点不能选择自身");
        }
        DeptDemo parent = null;
        if (!ObjectUtil.equal(entity.getParentId(), 0L)) {
            parent = deptDemoMapper.selectById(entity.getParentId());
            if (ObjectUtil.isNull(parent)) {
                throw new ServiceException("部门父节点不存在");
            }
        }
        if (updateMode && entity.getDeptId() != null && ObjectUtil.isNotNull(parent)
            && containsAncestor(parent.getAncestors(), entity.getDeptId())) {
            throw new ServiceException("不能选择当前节点或其子节点作为父节点");
        }
        String newAncestors = resolveAncestors(entity.getParentId(), parent);
        if (updateMode && entity.getDeptId() != null) {
            DeptDemo oldEntity = deptDemoMapper.selectById(entity.getDeptId());
            if (ObjectUtil.isNull(oldEntity)) {
                throw new ServiceException("部门不存在，无法修改");
            }
            String oldAncestors = oldEntity.getAncestors();
            entity.setAncestors(newAncestors);
            if (!StringUtils.equals(oldAncestors, newAncestors)) {
                updateChildrenAncestors(entity.getDeptId(), newAncestors, oldAncestors);
            }
        } else {
            entity.setAncestors(newAncestors);
        }
    }

    private String resolveAncestors(Long parentId, DeptDemo parent) {
        if (ObjectUtil.equal(parentId, 0L)) {
            return "0";
        }
        String parentAncestors = parent.getAncestors();
        if (StringUtils.isBlank(parentAncestors)) {
            return String.valueOf(parentId);
        }
        return parentAncestors + StringUtils.SEPARATOR + parentId;
    }

    private void updateChildrenAncestors(Long currentId, String newAncestors, String oldAncestors) {
        List<DeptDemo> children = deptDemoMapper.selectList(QueryBuilder.lambda(DeptDemo.class)
            .select(DeptDemo::getDeptId, DeptDemo::getAncestors)
            .findInSet(currentId, "ancestors")
            .build());
        List<DeptDemo> updateList = new ArrayList<>();
        for (DeptDemo child : children) {
            String ancestors = child.getAncestors();
            if (StringUtils.isBlank(ancestors)) {
                continue;
            }
            DeptDemo update = new DeptDemo();
            update.setDeptId(child.getDeptId());
            update.setAncestors(StringUtils.replaceOnce(ancestors, oldAncestors, newAncestors));
            updateList.add(update);
        }
        if (!updateList.isEmpty()) {
            deptDemoMapper.updateById(updateList);
        }
    }

    private boolean containsAncestor(String ancestors, Long nodeId) {
        for (String item : StringUtils.splitList(ancestors)) {
            if (StringUtils.equals(item, String.valueOf(nodeId))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (Boolean.TRUE.equals(isValid)) {
        }
        if (deptDemoMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除部门失败，数据不存在");
        }
    }

}
