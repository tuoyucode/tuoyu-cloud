package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesTeam;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.bo.MesWorkshopBo;
import org.tuoyu.mes.masterdata.domain.vo.MesWorkshopVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.masterdata.service.IMesWorkshopService;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 车间
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesWorkshopServiceImpl implements IMesWorkshopService {

    private final MesWorkshopMapper workshopMapper;
    private final MesFactoryMapper factoryMapper;
    private final MesLineMapper lineMapper;
    private final MesTeamMapper teamMapper;
    private final MesDeviceMapper deviceMapper;
    private final MesWorkorderMapper workorderMapper;

    @Override
    public MesWorkshopVo queryById(Long workshopId) {
        MesWorkshop workshop = MasterdataSupport.require(workshopMapper.selectById(workshopId), "车间不存在");
        MesWorkshopVo vo = BeanMapper.map(workshop, MesWorkshopVo.class);
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesWorkshopVo> queryPageList(MesWorkshopBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesWorkshop> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesWorkshopVo> result = PageResults.of(
            workshopMapper.selectPage(pageParam.toPage(MesWorkshop.class), lqw), MesWorkshopVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesWorkshopVo> queryList(MesWorkshopBo bo) {
        List<MesWorkshopVo> list = BeanMapper.mapList(workshopMapper.selectList(buildQueryWrapper(bo, true)),
            MesWorkshopVo.class);
        fillNames(list);
        return list;
    }

    @Override
    public long countList(MesWorkshopBo bo) {
        return workshopMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(Long factoryId) {
        return workshopMapper.selectList(QueryBuilder.lambda(MesWorkshop.class)
                .select(MesWorkshop::getWorkshopId, MesWorkshop::getWorkshopCode, MesWorkshop::getWorkshopName,
                    MesWorkshop::getFactoryId)
                .eq(MesWorkshop::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesWorkshop::getFactoryId, factoryId)
                .orderByAsc(MesWorkshop::getSortNo, MesWorkshop::getWorkshopId)
                .build())
            .stream()
            .map(w -> new MesOptionVo(w.getWorkshopId(), w.getWorkshopCode(), w.getWorkshopName(), w.getFactoryId()))
            .toList();
    }

    private LambdaQueryWrapper<MesWorkshop> buildQueryWrapper(MesWorkshopBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesWorkshop.class)
            .eqIfPresent(MesWorkshop::getFactoryId, bo.getFactoryId())
            .likeIfText(MesWorkshop::getWorkshopCode, bo.getWorkshopCode())
            .likeIfText(MesWorkshop::getWorkshopName, bo.getWorkshopName())
            .eqIfText(MesWorkshop::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesWorkshop::getSortNo)
            .orderByAsc(defaultOrder, MesWorkshop::getWorkshopId)
            .build();
    }

    private void fillNames(List<MesWorkshopVo> list) {
        MasterdataSupport.fill(list, MesWorkshopVo::getFactoryId, factoryMapper, MesFactory::getFactoryId,
            (vo, factory) -> vo.setFactoryName(factory.getFactoryName()));
    }

    @Override
    public void insertByBo(MesWorkshopBo bo) {
        checkCodeUnique(bo.getWorkshopCode(), null);
        requireFactory(bo.getFactoryId());
        MesWorkshop workshop = BeanMapper.map(bo, MesWorkshop.class);
        workshop.setWorkshopId(null);
        workshop.setStatus(MasterdataSupport.statusOrNormal(workshop.getStatus()));
        workshop.setSortNo(MasterdataSupport.sortOrZero(workshop.getSortNo()));
        workshopMapper.insert(workshop);
        bo.setWorkshopId(workshop.getWorkshopId());
    }

    @Override
    public void updateByBo(MesWorkshopBo bo) {
        MesWorkshop current = MasterdataSupport.require(workshopMapper.selectById(bo.getWorkshopId()),
            "修改车间失败，车间不存在");
        checkCodeUnique(bo.getWorkshopCode(), bo.getWorkshopId());
        requireFactory(bo.getFactoryId());
        if (!Objects.equals(current.getFactoryId(), bo.getFactoryId()) && hasChildren(List.of(bo.getWorkshopId()))) {
            // 产线、班组、设备中保存了所属工厂，换工厂会使层级不一致
            throw new ServiceException("车间下已有产线、班组或设备，不能更换所属工厂");
        }
        MesWorkshop workshop = BeanMapper.map(bo, MesWorkshop.class);
        if (workshopMapper.updateById(workshop) == 0) {
            throw new ServiceException("修改车间失败，车间不存在");
        }
    }

    private void requireFactory(Long factoryId) {
        MasterdataSupport.require(factoryMapper.selectById(factoryId), "所属工厂不存在");
    }

    private boolean hasChildren(Collection<Long> ids) {
        return lineMapper.exists(QueryBuilder.lambda(MesLine.class).in(MesLine::getWorkshopId, ids).build())
            || teamMapper.exists(QueryBuilder.lambda(MesTeam.class).in(MesTeam::getWorkshopId, ids).build())
            || deviceMapper.exists(QueryBuilder.lambda(MesDevice.class).in(MesDevice::getWorkshopId, ids).build());
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = workshopMapper.exists(QueryBuilder.lambda(MesWorkshop.class)
            .eq(MesWorkshop::getWorkshopCode, code)
            .neIfPresent(MesWorkshop::getWorkshopId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException("车间编码“" + code + "”已存在");
        }
    }

    @Override
    public void deleteByIds(Collection<Long> ids) {
        if (lineMapper.exists(QueryBuilder.lambda(MesLine.class).in(MesLine::getWorkshopId, ids).build())) {
            throw new ServiceException("车间下还有产线，不能删除");
        }
        if (teamMapper.exists(QueryBuilder.lambda(MesTeam.class).in(MesTeam::getWorkshopId, ids).build())) {
            throw new ServiceException("车间下还有班组，不能删除");
        }
        if (deviceMapper.exists(QueryBuilder.lambda(MesDevice.class).in(MesDevice::getWorkshopId, ids).build())) {
            throw new ServiceException("车间下还有设备，不能删除");
        }
        if (workorderMapper.exists(QueryBuilder.lambda(MesWorkorder.class).in(MesWorkorder::getWorkshopId, ids).build())) {
            throw new ServiceException("车间已有工单，不能删除，可以停用");
        }
        if (workshopMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除车间失败，车间不存在");
        }
    }

}
