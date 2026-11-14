package org.tuoyu.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.domain.bo.BizItemBo;
import org.tuoyu.system.domain.vo.BizItemVo;
import org.tuoyu.system.domain.BizItem;
import org.tuoyu.system.mapper.BizItemMapper;
import org.tuoyu.system.service.IBizItemService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class BizItemServiceImpl implements IBizItemService {

    private final BizItemMapper bizItemMapper;

    @Override
    public BizItemVo queryById(Long itemId) {
        return BeanMapper.map(bizItemMapper.selectById(itemId), BizItemVo.class);
    }

    @Override
    public PageResult<BizItemVo> queryPageList(BizItemBo bo, PageParam pageParam) {
        LambdaQueryWrapper<BizItem> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        return PageResults.of(bizItemMapper.selectPage(pageParam.toPage(BizItem.class), lqw), BizItemVo.class);
    }

    @Override
    public List<BizItemVo> queryList(BizItemBo bo) {
        return BeanMapper.mapList(bizItemMapper.selectList(buildQueryWrapper(bo, true)), BizItemVo.class);
    }

    @Override
    public long countList(BizItemBo bo) {
        return bizItemMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    private LambdaQueryWrapper<BizItem> buildQueryWrapper(BizItemBo bo, boolean defaultOrder) {
        Map<String, Object> params = bo.getParams();
        return QueryBuilder.lambda(BizItem.class)
            .likeIfText(BizItem::getItemName, bo.getItemName())
            .ltIfPresent(BizItem::getPrice, bo.getPrice())
            .gtIfPresent(BizItem::getWeight, bo.getWeight())
            .neIfText(BizItem::getItemType, bo.getItemType())
            .eqIfPresent(BizItem::getEnabled, bo.getEnabled())
            .betweenParams(BizItem::getStartDate, params, "beginStartDate", "endStartDate")
            .eqIfText(BizItem::getItemCodeX, bo.getItemCodeX())
            .orderByAsc(defaultOrder, BizItem::getItemId)
            .build();
    }

    @Override
    public void insertByBo(BizItemBo bo) {
        BizItem add = BeanMapper.map(bo, BizItem.class);
        validEntityBeforeSave(add);
        bizItemMapper.insert(add);
        bo.setItemId(add.getItemId());
    }

    @Override
    public void updateByBo(BizItemBo bo) {
        BizItem update = BeanMapper.map(bo, BizItem.class);
        validEntityBeforeSave(update);
        if (bizItemMapper.updateById(update) == 0) {
            throw new ServiceException("修改【请填写功能名称】失败，数据不存在");
        }
    }

    private void validEntityBeforeSave(BizItem entity) {
    }

    @Override
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (Boolean.TRUE.equals(isValid)) {
        }
        if (bizItemMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除【请填写功能名称】失败，数据不存在");
        }
    }

}
