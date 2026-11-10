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
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.bo.MesLineBo;
import org.tuoyu.mes.masterdata.domain.vo.MesLineVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.masterdata.service.IMesLineService;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 产线
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesLineServiceImpl implements IMesLineService {

    private final MesLineMapper lineMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesFactoryMapper factoryMapper;
    private final MesStationMapper stationMapper;
    private final MesDeviceMapper deviceMapper;
    private final MesWorkorderMapper workorderMapper;
    private final MesAndonEventMapper andonEventMapper;

    @Override
    public MesLineVo queryById(Long lineId) {
        MesLine line = MasterdataSupport.require(lineMapper.selectById(lineId), "产线不存在");
        MesLineVo vo = BeanMapper.map(line, MesLineVo.class);
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesLineVo> queryPageList(MesLineBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesLine> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesLineVo> result = PageResults.of(lineMapper.selectPage(pageParam.toPage(MesLine.class), lqw),
            MesLineVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesLineVo> queryList(MesLineBo bo) {
        List<MesLineVo> list = BeanMapper.mapList(lineMapper.selectList(buildQueryWrapper(bo, true)), MesLineVo.class);
        fillNames(list);
        return list;
    }

    @Override
    public long countList(MesLineBo bo) {
        return lineMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(Long workshopId) {
        return lineMapper.selectList(QueryBuilder.lambda(MesLine.class)
                .select(MesLine::getLineId, MesLine::getLineCode, MesLine::getLineName, MesLine::getWorkshopId)
                .eq(MesLine::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesLine::getWorkshopId, workshopId)
                .orderByAsc(MesLine::getSortNo, MesLine::getLineId)
                .build())
            .stream()
            .map(l -> new MesOptionVo(l.getLineId(), l.getLineCode(), l.getLineName(), l.getWorkshopId()))
            .toList();
    }

    private LambdaQueryWrapper<MesLine> buildQueryWrapper(MesLineBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesLine.class)
            .eqIfPresent(MesLine::getFactoryId, bo.getFactoryId())
            .eqIfPresent(MesLine::getWorkshopId, bo.getWorkshopId())
            .likeIfText(MesLine::getLineCode, bo.getLineCode())
            .likeIfText(MesLine::getLineName, bo.getLineName())
            .eqIfText(MesLine::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesLine::getSortNo)
            .orderByAsc(defaultOrder, MesLine::getLineId)
            .build();
    }

    private void fillNames(List<MesLineVo> list) {
        MasterdataSupport.fill(list, MesLineVo::getFactoryId, factoryMapper, MesFactory::getFactoryId,
            (vo, factory) -> vo.setFactoryName(factory.getFactoryName()));
        MasterdataSupport.fill(list, MesLineVo::getWorkshopId, workshopMapper, MesWorkshop::getWorkshopId,
            (vo, workshop) -> vo.setWorkshopName(workshop.getWorkshopName()));
    }

    @Override
    public void insertByBo(MesLineBo bo) {
        checkCodeUnique(bo.getLineCode(), null);
        MesLine line = BeanMapper.map(bo, MesLine.class);
        line.setLineId(null);
        line.setFactoryId(requireWorkshop(bo.getWorkshopId()).getFactoryId());
        line.setStatus(MasterdataSupport.statusOrNormal(line.getStatus()));
        line.setSortNo(MasterdataSupport.sortOrZero(line.getSortNo()));
        lineMapper.insert(line);
        bo.setLineId(line.getLineId());
    }

    @Override
    public void updateByBo(MesLineBo bo) {
        MesLine current = MasterdataSupport.require(lineMapper.selectById(bo.getLineId()), "修改产线失败，产线不存在");
        checkCodeUnique(bo.getLineCode(), bo.getLineId());
        MesWorkshop workshop = requireWorkshop(bo.getWorkshopId());
        if (!Objects.equals(current.getWorkshopId(), bo.getWorkshopId()) && hasChildren(bo.getLineId())) {
            // 工位、设备中保存了所属车间，换车间会使层级不一致
            throw new ServiceException("产线下已有工位或设备，不能更换所属车间");
        }
        MesLine line = BeanMapper.map(bo, MesLine.class);
        line.setFactoryId(workshop.getFactoryId());
        if (lineMapper.updateById(line) == 0) {
            throw new ServiceException("修改产线失败，产线不存在");
        }
    }

    private MesWorkshop requireWorkshop(Long workshopId) {
        return MasterdataSupport.require(workshopMapper.selectById(workshopId), "所属车间不存在");
    }

    private boolean hasChildren(Long lineId) {
        return stationMapper.exists(QueryBuilder.lambda(MesStation.class).eq(MesStation::getLineId, lineId).build())
            || deviceMapper.exists(QueryBuilder.lambda(MesDevice.class).eq(MesDevice::getLineId, lineId).build());
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = lineMapper.exists(QueryBuilder.lambda(MesLine.class)
            .eq(MesLine::getLineCode, code)
            .neIfPresent(MesLine::getLineId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException("产线编码“" + code + "”已存在");
        }
    }

    @Override
    public void deleteByIds(Collection<Long> ids) {
        if (stationMapper.exists(QueryBuilder.lambda(MesStation.class).in(MesStation::getLineId, ids).build())) {
            throw new ServiceException("产线下还有工位，不能删除");
        }
        if (deviceMapper.exists(QueryBuilder.lambda(MesDevice.class).in(MesDevice::getLineId, ids).build())) {
            throw new ServiceException("产线下还有设备，不能删除");
        }
        if (workorderMapper.exists(QueryBuilder.lambda(MesWorkorder.class).in(MesWorkorder::getLineId, ids).build())) {
            throw new ServiceException("产线已有工单，不能删除，可以停用");
        }
        if (andonEventMapper.exists(QueryBuilder.lambda(MesAndonEvent.class).in(MesAndonEvent::getLineId, ids).build())) {
            throw new ServiceException("产线已有安灯记录，不能删除，可以停用");
        }
        if (lineMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除产线失败，产线不存在");
        }
    }

}
