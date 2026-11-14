package org.tuoyu.system.service;

import org.tuoyu.system.domain.vo.VerifyDemoVo;
import org.tuoyu.system.domain.bo.VerifyDemoBo;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;

import java.util.Collection;
import java.util.List;

public interface IVerifyDemoService {

    VerifyDemoVo queryById(Long demoId);

    PageResult<VerifyDemoVo> queryPageList(VerifyDemoBo bo, PageParam pageParam);

    List<VerifyDemoVo> queryList(VerifyDemoBo bo);

    long countList(VerifyDemoBo bo);

    void insertByBo(VerifyDemoBo bo);

    void updateByBo(VerifyDemoBo bo);

    void deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
