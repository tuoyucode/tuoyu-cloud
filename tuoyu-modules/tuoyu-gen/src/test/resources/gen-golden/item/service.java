package org.tuoyu.system.service;

import org.tuoyu.system.domain.vo.BizItemVo;
import org.tuoyu.system.domain.bo.BizItemBo;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;

import java.util.Collection;
import java.util.List;

public interface IBizItemService {

    BizItemVo queryById(Long itemId);

    PageResult<BizItemVo> queryPageList(BizItemBo bo, PageParam pageParam);

    List<BizItemVo> queryList(BizItemBo bo);

    long countList(BizItemBo bo);

    void insertByBo(BizItemBo bo);

    void updateByBo(BizItemBo bo);

    void deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

}
