package org.tuoyu.system.dubbo;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.system.api.RemoteDeptService;
import org.tuoyu.system.api.domain.vo.RemoteDeptVo;
import org.tuoyu.system.domain.SysDept;
import org.tuoyu.system.domain.vo.SysDeptVo;
import org.tuoyu.system.mapper.SysDeptMapper;
import org.tuoyu.system.service.ISysDeptService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 部门服务
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Service
public class RemoteDeptServiceImpl implements RemoteDeptService {

    private final ISysDeptService deptService;
    private final SysDeptMapper deptMapper;

    /**
     * 通过部门ID查询部门名称
     *
     * @param deptIds 部门ID串逗号分隔
     * @return 部门名称串逗号分隔
     */
    @Override
    public String selectDeptNameByIds(String deptIds) {
        return deptService.selectDeptNameByIds(deptIds);
    }

    /**
     * 根据部门ID查询部门负责人
     *
     * @param deptId 部门ID，用于指定需要查询的部门
     * @return 返回该部门的负责人ID
     */
    @Override
    public Long selectDeptLeaderById(Long deptId) {
        SysDeptVo vo = deptService.selectDeptById(deptId);
        return ObjectUtil.isNull(vo) ? null : vo.getLeader();
    }

    /**
     * 查询部门
     *
     * @return 部门列表
     */
    @Override
    public List<RemoteDeptVo> selectDeptsByList() {
        List<SysDeptVo> list = deptMapper.lambda()
            .select(SysDept::getDeptId, SysDept::getDeptName, SysDept::getParentId)
            .eq(SysDept::getStatus, SystemConstants.NORMAL)
            .voList();
        return BeanUtil.copyToList(list, RemoteDeptVo.class);
    }

    /**
     * 根据部门 ID 列表查询部门名称映射关系
     *
     * @param deptIds 部门 ID 列表
     * @return Map，其中 key 为部门 ID，value 为对应的部门名称
     */
    @Override
    public Map<Long, String> selectDeptNamesByIds(Collection<Long> deptIds) {
        if (CollUtil.isEmpty(deptIds)) {
            return Collections.emptyMap();
        }
        List<SysDept> list = deptMapper.lambda()
            .select(SysDept::getDeptId, SysDept::getDeptName)
            .in(SysDept::getDeptId, deptIds)
            .list();
        return StreamUtils.toMap(list, SysDept::getDeptId, SysDept::getDeptName);
    }

}
