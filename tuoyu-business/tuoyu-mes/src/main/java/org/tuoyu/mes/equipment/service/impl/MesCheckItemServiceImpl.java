package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.equipment.domain.MesCheckItem;
import org.tuoyu.mes.equipment.domain.bo.MesCheckItemBo;
import org.tuoyu.mes.equipment.domain.vo.MesCheckItemVo;
import org.tuoyu.mes.equipment.enums.CheckResultType;
import org.tuoyu.mes.equipment.mapper.MesCheckItemMapper;
import org.tuoyu.mes.equipment.service.IMesCheckItemService;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.material.support.SafeLookup;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 点检项目（基础数据，不加数据权限）
 * <p>
 * 适用范围二选一：指定设备，或者按设备类型匹配（未指定设备时）。数值型项目至少有上限或下限之一，下限不大于上限；
 * 判定型项目不保存上下限与单位。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesCheckItemServiceImpl implements IMesCheckItemService {

    static final String YES = "Y";

    private final MesCheckItemMapper itemMapper;
    private final MesDeviceMapper deviceMapper;

    @Override
    public MesCheckItemVo queryById(Long checkItemId) {
        MesCheckItemVo vo = BeanMapper.map(requireItem(checkItemId), MesCheckItemVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesCheckItemVo> queryPageList(MesCheckItemBo bo, PageParam pageParam) {
        PageResult<MesCheckItemVo> result = PageResults.of(
            itemMapper.selectPage(pageParam.toPage(MesCheckItem.class), buildQueryWrapper(bo, !pageParam.hasSort())),
            MesCheckItemVo.class);
        fill(result.getItems());
        return result;
    }

    private LambdaQueryWrapper<MesCheckItem> buildQueryWrapper(MesCheckItemBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesCheckItem.class)
            .likeIfText(MesCheckItem::getItemName, bo.getItemName())
            .eqIfPresent(MesCheckItem::getDeviceId, bo.getDeviceId())
            .eqIfText(MesCheckItem::getDeviceType, bo.getDeviceType())
            .eqIfText(MesCheckItem::getResultType, bo.getResultType())
            .eqIfText(MesCheckItem::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesCheckItem::getSortNo)
            .orderByAsc(defaultOrder, MesCheckItem::getCheckItemId)
            .build();
    }

    /**
     * 适用设备的编码与名称
     */
    private void fill(List<MesCheckItemVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesDevice> devices = MesLookups.byIds(deviceMapper,
            MesLookups.ids(vos, MesCheckItemVo::getDeviceId), MesDevice::getDeviceId);
        for (MesCheckItemVo vo : vos) {
            MesDevice device = SafeLookup.get(devices, vo.getDeviceId());
            if (device != null) {
                vo.setDeviceCode(device.getDeviceCode());
                vo.setDeviceName(device.getDeviceName());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesCheckItemBo bo) {
        MesCheckItem item = normalize(bo);
        item.setCheckItemId(null);
        itemMapper.insert(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesCheckItemBo bo) {
        requireItem(bo.getCheckItemId());
        MesCheckItem item = normalize(bo);
        // 适用设备、上下限、单位等可以清空，逐项写入（按实体更新会跳过空值）
        int rows = itemMapper.update(new MesCheckItem(), Wrappers.<MesCheckItem>lambdaUpdate()
            .set(MesCheckItem::getItemName, item.getItemName())
            .set(MesCheckItem::getDeviceId, item.getDeviceId())
            .set(MesCheckItem::getDeviceType, item.getDeviceType())
            .set(MesCheckItem::getCheckMethod, item.getCheckMethod())
            .set(MesCheckItem::getCheckStandard, item.getCheckStandard())
            .set(MesCheckItem::getResultType, item.getResultType())
            .set(MesCheckItem::getLowerLimit, item.getLowerLimit())
            .set(MesCheckItem::getUpperLimit, item.getUpperLimit())
            .set(MesCheckItem::getUnit, item.getUnit())
            .set(MesCheckItem::getMandatory, item.getMandatory())
            .set(MesCheckItem::getSortNo, item.getSortNo())
            .set(MesCheckItem::getStatus, item.getStatus())
            .set(MesCheckItem::getRemark, item.getRemark())
            .eq(MesCheckItem::getCheckItemId, bo.getCheckItemId()));
        if (rows == 0) {
            throw new ServiceException("点检项目不存在");
        }
    }

    /**
     * 校验并整理为实体：适用范围二选一，数值型的上下限，默认值
     */
    MesCheckItem normalize(MesCheckItemBo bo) {
        CheckResultType type = CodeEnum.of(CheckResultType.class, bo.getResultType(), "结果类型");
        MesCheckItem item = BeanMapper.map(bo, MesCheckItem.class);
        item.setItemName(StringUtils.trim(bo.getItemName()));
        if (bo.getDeviceId() != null) {
            if (deviceMapper.selectById(bo.getDeviceId()) == null) {
                throw new ServiceException("适用设备不存在");
            }
            item.setDeviceType(null);
        } else if (StringUtils.isBlank(bo.getDeviceType())) {
            throw new ServiceException("请选择适用设备，或填写适用的设备类型");
        } else {
            item.setDeviceType(bo.getDeviceType().trim());
        }
        if (type == CheckResultType.NUMERIC) {
            if (bo.getLowerLimit() == null && bo.getUpperLimit() == null) {
                throw new ServiceException("数值型项目至少填写下限或上限之一");
            }
            if (bo.getLowerLimit() != null && bo.getUpperLimit() != null
                && bo.getLowerLimit().compareTo(bo.getUpperLimit()) > 0) {
                throw new ServiceException("下限不能大于上限");
            }
        } else {
            item.setLowerLimit(null);
            item.setUpperLimit(null);
            item.setUnit(null);
        }
        item.setMandatory(StringUtils.blankToDefault(bo.getMandatory(), YES));
        item.setStatus(StringUtils.blankToDefault(bo.getStatus(), SystemConstants.NORMAL));
        item.setSortNo(bo.getSortNo() == null ? 0 : bo.getSortNo());
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        if (itemMapper.selectByIds(distinct).size() != distinct.size()) {
            throw new ServiceException("点检项目不存在");
        }
        itemMapper.deleteByIds(distinct);
    }

    @Override
    public List<String> deviceTypes() {
        return deviceMapper.selectList(QueryBuilder.lambda(MesDevice.class)
                .select(MesDevice::getDeviceType)
                .isNotNull(MesDevice::getDeviceType)
                .groupBy(MesDevice::getDeviceType)
                .build())
            .stream()
            .filter(Objects::nonNull)
            .map(MesDevice::getDeviceType)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .sorted()
            .toList();
    }

    @Override
    public List<MesCheckItemVo> applicable(Long deviceId) {
        MesDevice device = deviceId == null ? null : deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new ServiceException("设备不存在");
        }
        List<MesCheckItemVo> vos = BeanMapper.mapList(applicableItems(device), MesCheckItemVo.class);
        fill(vos);
        return vos;
    }

    @Override
    public List<MesCheckItem> applicableItems(MesDevice device) {
        String deviceType = StringUtils.trimToNull(device.getDeviceType());
        return itemMapper.selectList(QueryBuilder.lambda(MesCheckItem.class)
            .eq(MesCheckItem::getStatus, SystemConstants.NORMAL)
            .and(scope -> scope.eq(MesCheckItem::getDeviceId, device.getDeviceId())
                .or(deviceType != null, byType -> byType.isNull(MesCheckItem::getDeviceId)
                    .eq(MesCheckItem::getDeviceType, deviceType)))
            .orderByAsc(MesCheckItem::getSortNo, MesCheckItem::getCheckItemId)
            .build());
    }

    private MesCheckItem requireItem(Long checkItemId) {
        MesCheckItem item = checkItemId == null ? null : itemMapper.selectById(checkItemId);
        if (item == null) {
            throw new ServiceException("点检项目不存在");
        }
        return item;
    }

}
