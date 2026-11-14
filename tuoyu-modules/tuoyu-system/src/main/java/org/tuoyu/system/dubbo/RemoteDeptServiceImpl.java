package org.tuoyu.system.dubbo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.api.RemoteDeptService;
import org.tuoyu.system.api.domain.vo.RemoteDeptVo;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.service.ISysDeptService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门服务的跨模块实现（不带数据权限，租户隔离照常）
 *
 * @author tuoyu
 */
@Service
@RequiredArgsConstructor
public class RemoteDeptServiceImpl implements RemoteDeptService {

    private final ISysDeptService deptService;
    private final SysDeptMapper deptMapper;

    @Override
    public String selectDeptNameByIds(String deptIds) {
        return deptService.selectDeptNameByIds(deptIds);
    }

    @Override
    public Long selectDeptLeaderById(Long deptId) {
        SysDeptVo dept = deptService.selectDeptById(deptId);
        return dept == null ? null : dept.getLeader();
    }

    @Override
    public List<RemoteDeptVo> selectDeptsByList() {
        List<RemoteDeptVo> result = new ArrayList<>();
        for (SysDept dept : deptMapper.selectList(QueryBuilder.lambda(SysDept.class)
            .select(SysDept::getDeptId, SysDept::getDeptName, SysDept::getParentId)
            .eq(SysDept::getStatus, SystemConstants.NORMAL)
            .build())) {
            if (dept != null) {
                RemoteDeptVo vo = new RemoteDeptVo();
                vo.setDeptId(dept.getDeptId());
                vo.setParentId(dept.getParentId());
                vo.setDeptName(dept.getDeptName());
                result.add(vo);
            }
        }
        return result;
    }

    @Override
    public Map<Long, String> selectDeptNamesByIds(Collection<Long> deptIds) {
        Map<Long, String> names = new HashMap<>();
        if (deptIds == null || deptIds.isEmpty()) {
            return names;
        }
        for (SysDept dept : deptMapper.selectList(QueryBuilder.lambda(SysDept.class)
            .select(SysDept::getDeptId, SysDept::getDeptName)
            .in(SysDept::getDeptId, deptIds)
            .build())) {
            if (dept != null) {
                names.put(dept.getDeptId(), dept.getDeptName());
            }
        }
        return names;
    }

}
