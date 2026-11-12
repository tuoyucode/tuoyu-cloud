package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.UniqueCodes;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.bo.MesFactoryBo;
import org.tuoyu.mes.masterdata.domain.vo.MesFactoryVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.masterdata.service.IMesFactoryService;

import java.util.Collection;
import java.util.List;

/**
 * 工厂
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesFactoryServiceImpl implements IMesFactoryService {

    private final MesFactoryMapper factoryMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesWarehouseMapper warehouseMapper;
    private final MesDeviceMapper deviceMapper;

    @Override
    public MesFactoryVo queryById(Long factoryId) {
        MesFactory factory = factoryMapper.selectById(factoryId);
        if (factory == null) {
            throw new ServiceException("工厂不存在");
        }
        return BeanMapper.map(factory, MesFactoryVo.class);
    }

    @Override
    public PageResult<MesFactoryVo> queryPageList(MesFactoryBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesFactory> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        return PageResults.of(factoryMapper.selectPage(pageParam.toPage(MesFactory.class), lqw), MesFactoryVo.class);
    }

    @Override
    public List<MesFactoryVo> queryList(MesFactoryBo bo) {
        return BeanMapper.mapList(factoryMapper.selectList(buildQueryWrapper(bo, true)), MesFactoryVo.class);
    }

    @Override
    public long countList(MesFactoryBo bo) {
        return factoryMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options() {
        return factoryMapper.selectList(QueryBuilder.lambda(MesFactory.class)
                .select(MesFactory::getFactoryId, MesFactory::getFactoryCode, MesFactory::getFactoryName)
                .eq(MesFactory::getStatus, SystemConstants.NORMAL)
                .orderByAsc(MesFactory::getSortNo, MesFactory::getFactoryId)
                .build())
            .stream().map(f -> new MesOptionVo(f.getFactoryId(), f.getFactoryCode(), f.getFactoryName())).toList();
    }

    private LambdaQueryWrapper<MesFactory> buildQueryWrapper(MesFactoryBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesFactory.class)
            .likeIfText(MesFactory::getFactoryCode, bo.getFactoryCode())
            .likeIfText(MesFactory::getFactoryName, bo.getFactoryName())
            .eqIfText(MesFactory::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesFactory::getSortNo)
            .orderByAsc(defaultOrder, MesFactory::getFactoryId)
            .build();
    }

    @Override
    public void insertByBo(MesFactoryBo bo) {
        checkCodeUnique(bo.getFactoryCode(), null);
        MesFactory factory = BeanMapper.map(bo, MesFactory.class);
        factory.setFactoryId(null);
        if (factory.getStatus() == null) {
            factory.setStatus(SystemConstants.NORMAL);
        }
        if (factory.getSortNo() == null) {
            factory.setSortNo(0);
        }
        UniqueCodes.write(() -> factoryMapper.insert(factory), duplicateCode(factory.getFactoryCode()));
        bo.setFactoryId(factory.getFactoryId());
    }

    @Override
    public void updateByBo(MesFactoryBo bo) {
        checkCodeUnique(bo.getFactoryCode(), bo.getFactoryId());
        MesFactory factory = BeanMapper.map(bo, MesFactory.class);
        if (UniqueCodes.write(() -> factoryMapper.updateById(factory), duplicateCode(bo.getFactoryCode())) == 0) {
            throw new ServiceException("修改工厂失败，工厂不存在");
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = factoryMapper.exists(QueryBuilder.lambda(MesFactory.class)
            .func(UniqueCodes.sameCode(MesFactory::getFactoryCode, code))
            .neIfPresent(MesFactory::getFactoryId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException(duplicateCode(code));
        }
    }

    private static String duplicateCode(String code) {
        return "工厂编码“" + code + "”已存在";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        if (workshopMapper.exists(QueryBuilder.lambda(MesWorkshop.class).in(MesWorkshop::getFactoryId, ids).build())) {
            throw new ServiceException("工厂下还有车间，不能删除");
        }
        if (warehouseMapper.exists(QueryBuilder.lambda(MesWarehouse.class).in(MesWarehouse::getFactoryId, ids).build())) {
            throw new ServiceException("工厂下还有仓库，不能删除");
        }
        if (deviceMapper.exists(QueryBuilder.lambda(MesDevice.class).in(MesDevice::getFactoryId, ids).build())) {
            throw new ServiceException("工厂下还有设备，不能删除");
        }
        if (factoryMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除工厂失败，工厂不存在");
        }
    }

}
