package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.bo.MesDeviceBo;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.masterdata.service.IMesDeviceService;

import java.util.Collection;
import java.util.List;

/**
 * 设备
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesDeviceServiceImpl implements IMesDeviceService {

    private final MesDeviceMapper deviceMapper;
    private final MesFactoryMapper factoryMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesLineMapper lineMapper;
    private final MesStationMapper stationMapper;
    private final MesAndonEventMapper andonEventMapper;

    @Override
    public MesDeviceVo queryById(Long deviceId) {
        MesDevice device = MasterdataSupport.require(deviceMapper.selectById(deviceId), "设备不存在");
        MesDeviceVo vo = BeanMapper.map(device, MesDeviceVo.class);
        fillNames(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesDeviceVo> queryPageList(MesDeviceBo bo, PageParam pageParam) {
        LambdaQueryWrapper<MesDevice> lqw = buildQueryWrapper(bo, !pageParam.hasSort());
        PageResult<MesDeviceVo> result = PageResults.of(
            deviceMapper.selectPage(pageParam.toPage(MesDevice.class), lqw), MesDeviceVo.class);
        fillNames(result.getItems());
        return result;
    }

    @Override
    public List<MesDeviceVo> queryList(MesDeviceBo bo) {
        List<MesDeviceVo> list = BeanMapper.mapList(deviceMapper.selectList(buildQueryWrapper(bo, true)),
            MesDeviceVo.class);
        fillNames(list);
        return list;
    }

    @Override
    public long countList(MesDeviceBo bo) {
        return deviceMapper.selectPage(PageParam.countOnly(), buildQueryWrapper(bo, false)).getTotal();
    }

    @Override
    public List<MesOptionVo> options(Long workshopId) {
        return deviceMapper.selectList(QueryBuilder.lambda(MesDevice.class)
                .select(MesDevice::getDeviceId, MesDevice::getDeviceCode, MesDevice::getDeviceName,
                    MesDevice::getWorkshopId)
                .eq(MesDevice::getStatus, SystemConstants.NORMAL)
                .eqIfPresent(MesDevice::getWorkshopId, workshopId)
                .orderByAsc(MesDevice::getDeviceCode, MesDevice::getDeviceId)
                .build())
            .stream()
            .map(d -> new MesOptionVo(d.getDeviceId(), d.getDeviceCode(), d.getDeviceName(), d.getWorkshopId()))
            .toList();
    }

    private LambdaQueryWrapper<MesDevice> buildQueryWrapper(MesDeviceBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesDevice.class)
            .eqIfPresent(MesDevice::getFactoryId, bo.getFactoryId())
            .eqIfPresent(MesDevice::getWorkshopId, bo.getWorkshopId())
            .eqIfPresent(MesDevice::getLineId, bo.getLineId())
            .eqIfPresent(MesDevice::getStationId, bo.getStationId())
            .likeIfText(MesDevice::getDeviceCode, bo.getDeviceCode())
            .likeIfText(MesDevice::getDeviceName, bo.getDeviceName())
            .eqIfText(MesDevice::getDeviceType, bo.getDeviceType())
            .eqIfText(MesDevice::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesDevice::getDeviceCode)
            .orderByAsc(defaultOrder, MesDevice::getDeviceId)
            .build();
    }

    private void fillNames(List<MesDeviceVo> list) {
        MasterdataSupport.fill(list, MesDeviceVo::getFactoryId, factoryMapper, MesFactory::getFactoryId,
            (vo, factory) -> vo.setFactoryName(factory.getFactoryName()));
        MasterdataSupport.fill(list, MesDeviceVo::getWorkshopId, workshopMapper, MesWorkshop::getWorkshopId,
            (vo, workshop) -> vo.setWorkshopName(workshop.getWorkshopName()));
        MasterdataSupport.fill(list, MesDeviceVo::getLineId, lineMapper, MesLine::getLineId,
            (vo, line) -> vo.setLineName(line.getLineName()));
        MasterdataSupport.fill(list, MesDeviceVo::getStationId, stationMapper, MesStation::getStationId,
            (vo, station) -> vo.setStationName(station.getStationName()));
    }

    @Override
    public void insertByBo(MesDeviceBo bo) {
        checkCodeUnique(bo.getDeviceCode(), null);
        MesDevice device = BeanMapper.map(bo, MesDevice.class);
        device.setDeviceId(null);
        resolveLocation(device);
        device.setStatus(MasterdataSupport.statusOrNormal(device.getStatus()));
        deviceMapper.insert(device);
        bo.setDeviceId(device.getDeviceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesDeviceBo bo) {
        checkCodeUnique(bo.getDeviceCode(), bo.getDeviceId());
        MesDevice device = BeanMapper.map(bo, MesDevice.class);
        resolveLocation(device);
        if (deviceMapper.updateById(device) == 0) {
            throw new ServiceException("修改设备失败，设备不存在");
        }
        // 实体中为 null 的字段不会更新：只填到上级时，把下级位置显式清空
        deviceMapper.update(null, Wrappers.<MesDevice>lambdaUpdate()
            .set(MesDevice::getWorkshopId, device.getWorkshopId())
            .set(MesDevice::getLineId, device.getLineId())
            .set(MesDevice::getStationId, device.getStationId())
            .eq(MesDevice::getDeviceId, device.getDeviceId()));
    }

    /**
     * 按填写的最低一级位置校验整条链（工位 → 产线 → 车间 → 工厂），并补齐上级
     */
    void resolveLocation(MesDevice device) {
        Long factoryId = device.getFactoryId();
        Long workshopId = device.getWorkshopId();
        Long lineId = device.getLineId();
        if (device.getStationId() != null) {
            MesStation station = MasterdataSupport.require(stationMapper.selectById(device.getStationId()),
                "所属工位不存在");
            lineId = MasterdataSupport.derive(lineId, station.getLineId(), "所选工位不属于所选产线");
            workshopId = MasterdataSupport.derive(workshopId, station.getWorkshopId(), "所选工位不属于所选车间");
            factoryId = MasterdataSupport.derive(factoryId, station.getFactoryId(), "所选工位不属于所选工厂");
        } else if (lineId != null) {
            MesLine line = MasterdataSupport.require(lineMapper.selectById(lineId), "所属产线不存在");
            workshopId = MasterdataSupport.derive(workshopId, line.getWorkshopId(), "所选产线不属于所选车间");
            factoryId = MasterdataSupport.derive(factoryId, line.getFactoryId(), "所选产线不属于所选工厂");
        } else if (workshopId != null) {
            MesWorkshop workshop = MasterdataSupport.require(workshopMapper.selectById(workshopId), "所属车间不存在");
            factoryId = MasterdataSupport.derive(factoryId, workshop.getFactoryId(), "所选车间不属于所选工厂");
        }
        if (factoryId == null) {
            throw new ServiceException("请选择设备所属的工厂");
        }
        MasterdataSupport.require(factoryMapper.selectById(factoryId), "所属工厂不存在");
        device.setFactoryId(factoryId);
        device.setWorkshopId(workshopId);
        device.setLineId(lineId);
    }

    private void checkCodeUnique(String code, Long excludeId) {
        boolean exists = deviceMapper.exists(QueryBuilder.lambda(MesDevice.class)
            .eq(MesDevice::getDeviceCode, code)
            .neIfPresent(MesDevice::getDeviceId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException("设备编码“" + code + "”已存在");
        }
    }

    @Override
    public void deleteByIds(Collection<Long> ids) {
        if (andonEventMapper.exists(QueryBuilder.lambda(MesAndonEvent.class).in(MesAndonEvent::getDeviceId, ids).build())) {
            throw new ServiceException("设备已有安灯记录，不能删除，可以停用");
        }
        if (deviceMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除设备失败，设备不存在");
        }
    }

}
