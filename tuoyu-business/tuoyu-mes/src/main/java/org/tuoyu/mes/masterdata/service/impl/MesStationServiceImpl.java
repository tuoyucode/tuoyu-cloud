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
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.common.UniqueCodes;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.bo.MesStationBo;
import org.tuoyu.mes.masterdata.domain.vo.MesStationVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.masterdata.service.IMesStationService;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.production.domain.MesProductionReport;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 工位
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesStationServiceImpl implements IMesStationService {

    private final MesStationMapper stationMapper;
    private final MesLineMapper lineMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesFactoryMapper factoryMapper;
    private final MesDeviceMapper deviceMapper;
    private final MesOperationTaskMapper taskMapper;
    private final MesProductionReportMapper reportMapper;
    private final MesAndonEventMapper andonEventMapper;

    @Override
    public MesStationVo queryById(Long stationId) {
        MesStation station = MasterdataSupport.require(stationMapper.selectById(stationId), "工位不存在");
        MesStationVo vo = BeanMapper.map(station, MesStationVo.class);
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesStationVo> queryPageList(MesStationBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesStation> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesStationVo> result = PageResults.of(
            stationMapper.selectPage(pageParam.toPage(MesStation.class), lqw), MesStationVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesStationVo> queryList(MesStationBo bo) {
        List<MesStationVo> list = BeanMapper.mapList(stationMapper.selectList(buildQueryWrapper(bo, true)),
            MesStationVo.class);
        fillNames(list);
        return list;
    }

    @Override
    public long countList(MesStationBo bo) {
        return stationMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(Long lineId) {
        return stationMapper.selectList(QueryBuilder.lambda(MesStation.class)
                .select(MesStation::getStationId, MesStation::getStationCode, MesStation::getStationName,
                    MesStation::getLineId)
                .eq(MesStation::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesStation::getLineId, lineId)
                .orderByAsc(MesStation::getSortNo, MesStation::getStationId)
                .build())
            .stream()
            .map(s -> new MesOptionVo(s.getStationId(), s.getStationCode(), s.getStationName(), s.getLineId()))
            .toList();
    }

    private LambdaQueryWrapper<MesStation> buildQueryWrapper(MesStationBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesStation.class)
            .eqIfPresent(MesStation::getFactoryId, bo.getFactoryId())
            .eqIfPresent(MesStation::getWorkshopId, bo.getWorkshopId())
            .eqIfPresent(MesStation::getLineId, bo.getLineId())
            .likeIfText(MesStation::getStationCode, bo.getStationCode())
            .likeIfText(MesStation::getStationName, bo.getStationName())
            .eqIfText(MesStation::getStationType, bo.getStationType())
            .eqIfText(MesStation::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesStation::getSortNo)
            .orderByAsc(defaultOrder, MesStation::getStationId)
            .build();
    }

    private void fillNames(List<MesStationVo> list) {
        MasterdataSupport.fill(list, MesStationVo::getFactoryId, factoryMapper, MesFactory::getFactoryId,
            (vo, factory) -> vo.setFactoryName(factory.getFactoryName()));
        MasterdataSupport.fill(list, MesStationVo::getWorkshopId, workshopMapper, MesWorkshop::getWorkshopId,
            (vo, workshop) -> vo.setWorkshopName(workshop.getWorkshopName()));
        MasterdataSupport.fill(list, MesStationVo::getLineId, lineMapper, MesLine::getLineId,
            (vo, line) -> vo.setLineName(line.getLineName()));
    }

    @Override
    public void insertByBo(MesStationBo bo) {
        checkCodeUnique(bo.getStationCode(), null);
        MesLine line = requireLine(bo.getLineId());
        MesStation station = BeanMapper.map(bo, MesStation.class);
        station.setStationId(null);
        station.setWorkshopId(line.getWorkshopId());
        station.setFactoryId(line.getFactoryId());
        station.setStatus(MasterdataSupport.statusOrNormal(station.getStatus()));
        station.setSortNo(MasterdataSupport.sortOrZero(station.getSortNo()));
        UniqueCodes.write(() -> stationMapper.insert(station), duplicateCode(station.getStationCode()));
        bo.setStationId(station.getStationId());
    }

    @Override
    public void updateByBo(MesStationBo bo) {
        MesStation current = MasterdataSupport.require(stationMapper.selectById(bo.getStationId()),
            "修改工位失败，工位不存在");
        checkCodeUnique(bo.getStationCode(), bo.getStationId());
        MesLine line = requireLine(bo.getLineId());
        if (!Objects.equals(current.getLineId(), bo.getLineId())
            && deviceMapper.exists(QueryBuilder.lambda(MesDevice.class)
            .eq(MesDevice::getStationId, bo.getStationId()).build())) {
            // 设备中保存了所属产线，换产线会使层级不一致
            throw new ServiceException("工位上已有设备，不能更换所属产线");
        }
        MesStation station = BeanMapper.map(bo, MesStation.class);
        station.setWorkshopId(line.getWorkshopId());
        station.setFactoryId(line.getFactoryId());
        if (UniqueCodes.write(() -> stationMapper.updateById(station), duplicateCode(bo.getStationCode())) == 0) {
            throw new ServiceException("修改工位失败，工位不存在");
        }
    }

    private MesLine requireLine(Long lineId) {
        return MasterdataSupport.require(lineMapper.selectById(lineId), "所属产线不存在");
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = stationMapper.exists(QueryBuilder.lambda(MesStation.class)
            .func(UniqueCodes.sameCode(MesStation::getStationCode, code))
            .neIfPresent(MesStation::getStationId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException(duplicateCode(code));
        }
    }

    private static String duplicateCode(String code) {
        return "工位编码“" + code + "”已存在";
    }

    @Override
    public void deleteByIds(Collection<Long> ids) {
        if (deviceMapper.exists(QueryBuilder.lambda(MesDevice.class).in(MesDevice::getStationId, ids).build())) {
            throw new ServiceException("工位上还有设备，不能删除");
        }
        if (taskMapper.exists(QueryBuilder.lambda(MesOperationTask.class).in(MesOperationTask::getStationId, ids).build())
            || reportMapper.exists(QueryBuilder.lambda(MesProductionReport.class)
            .in(MesProductionReport::getStationId, ids).build())) {
            throw new ServiceException("工位已有工序任务或报工记录，不能删除，可以停用");
        }
        if (andonEventMapper.exists(QueryBuilder.lambda(MesAndonEvent.class).in(MesAndonEvent::getStationId, ids).build())) {
            throw new ServiceException("工位已有安灯记录，不能删除，可以停用");
        }
        if (stationMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除工位失败，工位不存在");
        }
    }

}
