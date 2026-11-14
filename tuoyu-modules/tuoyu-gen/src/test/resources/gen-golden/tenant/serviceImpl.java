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
import org.tuoyu.system.domain.bo.VerifyDemoBo;
import org.tuoyu.system.domain.vo.VerifyDemoVo;
import org.tuoyu.system.domain.VerifyDemo;
import org.tuoyu.system.mapper.VerifyDemoMapper;
import org.tuoyu.system.service.IVerifyDemoService;

import java.util.Collection;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class VerifyDemoServiceImpl implements IVerifyDemoService {

    private final VerifyDemoMapper verifyDemoMapper;

    @Override
    public VerifyDemoVo queryById(Long demoId) {
        return BeanMapper.map(verifyDemoMapper.selectById(demoId), VerifyDemoVo.class);
    }

    @Override
    public PageResult<VerifyDemoVo> queryPageList(VerifyDemoBo bo, PageParam pageParam) {
        LambdaQueryWrapper<VerifyDemo> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        return PageResults.of(verifyDemoMapper.selectPage(pageParam.toPage(VerifyDemo.class), lqw), VerifyDemoVo.class);
    }

    @Override
    public List<VerifyDemoVo> queryList(VerifyDemoBo bo) {
        return BeanMapper.mapList(verifyDemoMapper.selectList(buildQueryWrapper(bo, true)), VerifyDemoVo.class);
    }

    @Override
    public long countList(VerifyDemoBo bo) {
        return verifyDemoMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    private LambdaQueryWrapper<VerifyDemo> buildQueryWrapper(VerifyDemoBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(VerifyDemo.class)
            .eqIfText(VerifyDemo::getDemoCode, bo.getDemoCode())
            .likeIfText(VerifyDemo::getDemoName, bo.getDemoName())
            .ge(bo.getAmount() != null, VerifyDemo::getAmount, bo.getAmount())
            .eqIfPresent(VerifyDemo::getDemoSort, bo.getDemoSort())
            .eqIfText(VerifyDemo::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, VerifyDemo::getDemoId)
            .build();
    }

    @Override
    public void insertByBo(VerifyDemoBo bo) {
        VerifyDemo add = BeanMapper.map(bo, VerifyDemo.class);
        validEntityBeforeSave(add);
        verifyDemoMapper.insert(add);
        bo.setDemoId(add.getDemoId());
    }

    @Override
    public void updateByBo(VerifyDemoBo bo) {
        VerifyDemo update = BeanMapper.map(bo, VerifyDemo.class);
        validEntityBeforeSave(update);
        if (verifyDemoMapper.updateById(update) == 0) {
            throw new ServiceException("修改验证示例失败，数据不存在");
        }
    }

    private void validEntityBeforeSave(VerifyDemo entity) {
    }

    @Override
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (Boolean.TRUE.equals(isValid)) {
        }
        if (verifyDemoMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除验证示例失败，数据不存在");
        }
    }

}
