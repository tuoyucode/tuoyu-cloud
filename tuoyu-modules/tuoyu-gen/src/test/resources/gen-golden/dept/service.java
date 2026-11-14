package org.tuoyu.system.service;

import org.tuoyu.system.domain.vo.DeptDemoVo;
import org.tuoyu.system.domain.bo.DeptDemoBo;

import java.util.Collection;
import java.util.List;

public interface IDeptDemoService {

    DeptDemoVo queryById(Long deptId);

    List<DeptDemoVo> queryList(DeptDemoBo bo);

    void insertByBo(DeptDemoBo bo);

    void updateByBo(DeptDemoBo bo);

    void deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
