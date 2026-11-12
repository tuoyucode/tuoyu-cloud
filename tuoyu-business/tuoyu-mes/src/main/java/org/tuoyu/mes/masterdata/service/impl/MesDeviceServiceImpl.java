package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
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
import org.tuoyu.mes.equipment.domain.MesCheckRecord;
import org.tuoyu.mes.equipment.domain.MesMaintenanceOrder;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;
import org.tuoyu.mes.equipment.mapper.MesCheckRecordMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenanceOrderMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.excel.ImportErrors;
import org.tuoyu.mes.common.excel.ImportFields;
import org.tuoyu.mes.common.excel.ImportRow;
import org.tuoyu.mes.common.excel.MesExcelImport;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesStation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.bo.MesDeviceBo;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceImportVo;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.masterdata.service.IMesDeviceService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 设备
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesDeviceServiceImpl implements IMesDeviceService {

    /**
     * 导入写入时撞上编码的唯一索引：校验之后、写入之前有人新增了相同的编码
     */
    static final String IMPORT_DUPLICATE = "部分设备编码刚被其他人新增，请重新导入";

    private final MesDeviceMapper deviceMapper;
    private final MesFactoryMapper factoryMapper;
    private final MesWorkshopMapper workshopMapper;
    private final MesLineMapper lineMapper;
    private final MesStationMapper stationMapper;
    private final MesAndonEventMapper andonEventMapper;
    private final MesCheckRecordMapper checkRecordMapper;
    private final MesMaintenancePlanMapper maintenancePlanMapper;
    private final MesMaintenanceOrderMapper maintenanceOrderMapper;

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

    /**
     * 设备下拉；按车间查询时，同时返回没有指定车间、直接挂在该车间所属工厂下的设备（例如空压机等公用设备）
     */
    @Override
    public List<MesOptionVo> options(Long workshopId) {
        MesWorkshop workshop = workshopId == null ? null : workshopMapper.selectById(workshopId);
        if (workshopId != null && workshop == null) {
            return List.of();
        }
        return deviceMapper.selectList(QueryBuilder.lambda(MesDevice.class)
                .select(MesDevice::getDeviceId, MesDevice::getDeviceCode, MesDevice::getDeviceName,
                    MesDevice::getWorkshopId)
                .eq(MesDevice::getStatus, SystemConstants.NORMAL)
                .and(workshop != null, w -> w.eq(MesDevice::getWorkshopId, workshop.getWorkshopId())
                    .or(factoryLevel -> factoryLevel.isNull(MesDevice::getWorkshopId)
                        .eq(MesDevice::getFactoryId, workshop.getFactoryId())))
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
        UniqueCodes.write(() -> deviceMapper.insert(device), duplicateCode(device.getDeviceCode()));
        bo.setDeviceId(device.getDeviceId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesDeviceBo bo) {
        checkCodeUnique(bo.getDeviceCode(), bo.getDeviceId());
        MesDevice device = BeanMapper.map(bo, MesDevice.class);
        resolveLocation(device);
        if (UniqueCodes.write(() -> deviceMapper.updateById(device), duplicateCode(bo.getDeviceCode())) == 0) {
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
            .func(UniqueCodes.sameCode(MesDevice::getDeviceCode, code))
            .neIfPresent(MesDevice::getDeviceId, excludeId)
            .build());
        if (exists) {
            throw new ServiceException(duplicateCode(code));
        }
    }

    private static String duplicateCode(String code) {
        return "设备编码“" + code + "”已存在";
    }

    @Override
    public void deleteByIds(Collection<Long> ids) {
        if (andonEventMapper.exists(QueryBuilder.lambda(MesAndonEvent.class).in(MesAndonEvent::getDeviceId, ids).build())) {
            throw new ServiceException("设备已有安灯记录，不能删除，可以停用");
        }
        if (checkRecordMapper.exists(QueryBuilder.lambda(MesCheckRecord.class).in(MesCheckRecord::getDeviceId, ids).build())) {
            throw new ServiceException("设备已有点检记录，不能删除，可以停用");
        }
        if (maintenancePlanMapper.exists(QueryBuilder.lambda(MesMaintenancePlan.class).in(MesMaintenancePlan::getDeviceId, ids).build())
            || maintenanceOrderMapper.exists(QueryBuilder.lambda(MesMaintenanceOrder.class).in(MesMaintenanceOrder::getDeviceId, ids).build())) {
            throw new ServiceException("设备已有保养计划或保养工单，不能删除，可以停用");
        }
        if (deviceMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除设备失败，设备不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importData(MultipartFile file) {
        ImportErrors errors = new ImportErrors();
        List<ImportRow<MesDeviceImportVo>> rows = MesExcelImport.read(file, MesDeviceImportVo.class, errors);
        Map<String, MesFactory> factories = MesLookups.byCodes(factoryMapper, MesFactory.class, MesFactory::getFactoryCode,
            codes(rows, MesDeviceImportVo::getFactoryCode));
        Map<String, MesWorkshop> workshops = MesLookups.byCodes(workshopMapper, MesWorkshop.class,
            MesWorkshop::getWorkshopCode, codes(rows, MesDeviceImportVo::getWorkshopCode));
        Map<String, MesLine> lines = MesLookups.byCodes(lineMapper, MesLine.class, MesLine::getLineCode,
            codes(rows, MesDeviceImportVo::getLineCode));
        Map<String, MesStation> stations = MesLookups.byCodes(stationMapper, MesStation.class, MesStation::getStationCode,
            codes(rows, MesDeviceImportVo::getStationCode));
        Map<String, MesDevice> existing = MesLookups.byCodes(deviceMapper, MesDevice.class, MesDevice::getDeviceCode,
            codes(rows, MesDeviceImportVo::getDeviceCode));
        ImportLocations locations = new ImportLocations(factories, workshops, lines, stations);
        Map<String, Integer> firstRowOfCode = new HashMap<>();
        List<MesDevice> devices = new ArrayList<>();
        for (ImportRow<MesDeviceImportVo> row : rows) {
            ImportFields fields = errors.row(row.rowNumber());
            MesDeviceImportVo vo = row.data();
            MesDevice device = new MesDevice();
            device.setDeviceCode(fields.text("设备编码", vo.getDeviceCode(), 64, true));
            device.setDeviceName(fields.text("设备名称", vo.getDeviceName(), 128, true));
            device.setDeviceType(fields.text("设备类型", vo.getDeviceType(), 32, false));
            device.setModel(fields.text("型号", vo.getModel(), 128, false));
            device.setManufacturer(fields.text("制造商", vo.getManufacturer(), 128, false));
            device.setStatus(fields.status("状态", vo.getStatus()));
            device.setRemark(fields.text("备注", vo.getRemark(), 500, false));
            String code = device.getDeviceCode();
            if (code != null) {
                Integer first = firstRowOfCode.putIfAbsent(MesLookups.codeKey(code), row.rowNumber());
                if (first != null) {
                    fields.error("设备编码 " + code + " 与第 " + first + " 行重复");
                } else if (existing.containsKey(MesLookups.codeKey(code))) {
                    fields.error("设备编码 " + code + " 已存在");
                }
            }
            resolveImportLocation(fields, vo, device, locations);
            if (fields.ok()) {
                devices.add(device);
            }
        }
        errors.throwIfAny();
        UniqueCodes.write(() -> deviceMapper.insert(devices), IMPORT_DUPLICATE);
        return devices.size();
    }

    /**
     * 导入时按编码查到的位置
     */
    private record ImportLocations(Map<String, MesFactory> factories, Map<String, MesWorkshop> workshops,
                                   Map<String, MesLine> lines, Map<String, MesStation> stations) {
    }

    /**
     * 按编码校验所属位置（规则与 {@link #resolveLocation(MesDevice)} 相同）：按填写的最低一级带出上级，
     * 同时填了上级时必须一致；编码不存在、层级不一致时记入该行的错误
     */
    private static void resolveImportLocation(ImportFields fields, MesDeviceImportVo vo, MesDevice device, ImportLocations found) {
        String factoryCode = fields.text("所属工厂编码", vo.getFactoryCode(), 64, false);
        String workshopCode = fields.text("所属车间编码", vo.getWorkshopCode(), 64, false);
        String lineCode = fields.text("所属产线编码", vo.getLineCode(), 64, false);
        String stationCode = fields.text("所属工位编码", vo.getStationCode(), 64, false);
        MesFactory factory = lookup(fields, "所属工厂", factoryCode, found.factories());
        MesWorkshop workshop = lookup(fields, "所属车间", workshopCode, found.workshops());
        MesLine line = lookup(fields, "所属产线", lineCode, found.lines());
        MesStation station = lookup(fields, "所属工位", stationCode, found.stations());
        if (!fields.ok()) {
            return;
        }
        Long factoryId = factory == null ? null : factory.getFactoryId();
        Long workshopId = workshop == null ? null : workshop.getWorkshopId();
        Long lineId = line == null ? null : line.getLineId();
        if (station != null) {
            check(fields, lineId, station.getLineId(), "所属工位 " + stationCode + " 不属于产线 " + lineCode);
            check(fields, workshopId, station.getWorkshopId(), "所属工位 " + stationCode + " 不属于车间 " + workshopCode);
            check(fields, factoryId, station.getFactoryId(), "所属工位 " + stationCode + " 不属于工厂 " + factoryCode);
            device.setStationId(station.getStationId());
            lineId = station.getLineId();
            workshopId = station.getWorkshopId();
            factoryId = station.getFactoryId();
        } else if (line != null) {
            check(fields, workshopId, line.getWorkshopId(), "所属产线 " + lineCode + " 不属于车间 " + workshopCode);
            check(fields, factoryId, line.getFactoryId(), "所属产线 " + lineCode + " 不属于工厂 " + factoryCode);
            workshopId = line.getWorkshopId();
            factoryId = line.getFactoryId();
        } else if (workshop != null) {
            check(fields, factoryId, workshop.getFactoryId(), "所属车间 " + workshopCode + " 不属于工厂 " + factoryCode);
            factoryId = workshop.getFactoryId();
        }
        if (factoryId == null) {
            fields.error("请填写所属工厂编码，或者填写所属的车间、产线、工位编码");
        }
        device.setFactoryId(factoryId);
        device.setWorkshopId(workshopId);
        device.setLineId(lineId);
    }

    private static <T> T lookup(ImportFields fields, String field, String code, Map<String, T> found) {
        if (code == null) {
            return null;
        }
        T entity = found.get(MesLookups.codeKey(code));
        if (entity == null) {
            fields.error(field + "编码 " + code + " 不存在");
        }
        return entity;
    }

    private static void check(ImportFields fields, Long given, Long actual, String message) {
        if (given != null && !given.equals(actual)) {
            fields.error(message);
        }
    }

    private static List<String> codes(List<ImportRow<MesDeviceImportVo>> rows, Function<MesDeviceImportVo, String> code) {
        return rows.stream().map(row -> ImportFields.blankToNull(code.apply(row.data()))).toList();
    }

}
