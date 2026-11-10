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
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.equipment.domain.MesMaintenanceOrder;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenancePlanBo;
import org.tuoyu.mes.equipment.domain.vo.MesMaintenancePlanVo;
import org.tuoyu.mes.equipment.enums.MaintenanceOrderStatus;
import org.tuoyu.mes.equipment.mapper.MesMaintenanceOrderMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;
import org.tuoyu.mes.equipment.service.IMesMaintenancePlanService;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.material.support.SafeLookup;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 保养计划（基础数据，不加数据权限）
 * <p>
 * {@code generate_from_date}（下次到期日期减提前天数）是生成本周期工单的起始日期，本周期已生成时为空；
 * {@code generated_due_date} 记录已生成工单的到期日期，同一到期日期只生成一次（见 {@link MaintenanceOrderGenerator}）。
 * 完成或取消工单时由保养工单推进下次到期日期，并重新计算生成日期。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesMaintenancePlanServiceImpl implements IMesMaintenancePlanService {

    /**
     * 未完成（待保养、保养中）的保养工单状态
     */
    static final List<String> OPEN_STATUSES = List.of(MaintenanceOrderStatus.PENDING.code(),
        MaintenanceOrderStatus.IN_PROGRESS.code());

    private final MesMaintenancePlanMapper planMapper;
    private final MesMaintenanceOrderMapper orderMapper;
    private final MesDeviceMapper deviceMapper;
    private final MaintenanceOrderGenerator generator;

    private Clock clock = Clock.systemDefaultZone();

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MesMaintenancePlanVo queryById(Long planId) {
        MesMaintenancePlanVo vo = BeanMapper.map(requirePlan(planId), MesMaintenancePlanVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesMaintenancePlanVo> queryPageList(MesMaintenancePlanBo bo, PageParam pageParam) {
        PageResult<MesMaintenancePlanVo> result = PageResults.of(
            planMapper.selectPage(pageParam.toPage(MesMaintenancePlan.class), buildQueryWrapper(bo, !pageParam.hasSort())),
            MesMaintenancePlanVo.class);
        fill(result.getItems());
        return result;
    }

    private LambdaQueryWrapper<MesMaintenancePlan> buildQueryWrapper(MesMaintenancePlanBo bo, boolean defaultOrder) {
        return QueryBuilder.lambda(MesMaintenancePlan.class)
            .likeIfText(MesMaintenancePlan::getPlanName, bo.getPlanName())
            .eqIfPresent(MesMaintenancePlan::getDeviceId, bo.getDeviceId())
            .eqIfText(MesMaintenancePlan::getStatus, bo.getStatus())
            .orderByAsc(defaultOrder, MesMaintenancePlan::getNextDueDate)
            .orderByAsc(defaultOrder, MesMaintenancePlan::getPlanId)
            .build();
    }

    /**
     * 设备编码与名称；启用的计划下次到期日期早于今天时标记为逾期
     */
    private void fill(List<MesMaintenancePlanVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesDevice> devices = MesLookups.byIds(deviceMapper,
            MesLookups.ids(vos, MesMaintenancePlanVo::getDeviceId), MesDevice::getDeviceId);
        LocalDate today = LocalDate.now(clock);
        for (MesMaintenancePlanVo vo : vos) {
            MesDevice device = SafeLookup.get(devices, vo.getDeviceId());
            if (device != null) {
                vo.setDeviceCode(device.getDeviceCode());
                vo.setDeviceName(device.getDeviceName());
            }
            vo.setOverdue(SystemConstants.NORMAL.equals(vo.getStatus()) && vo.getNextDueDate() != null
                && vo.getNextDueDate().isBefore(today));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesMaintenancePlanBo bo) {
        requireDevice(bo.getDeviceId());
        int leadDays = leadDays(bo);
        LocalDate next = bo.getNextDueDate();
        if (next == null) {
            next = bo.getLastMaintenanceDate() != null ? bo.getLastMaintenanceDate().plusDays(bo.getCycleDays())
                : LocalDate.now(clock);
        }
        MesMaintenancePlan plan = BeanMapper.map(bo, MesMaintenancePlan.class);
        plan.setPlanId(null);
        plan.setPlanName(bo.getPlanName().trim());
        plan.setLeadDays(leadDays);
        plan.setNextDueDate(next);
        plan.setGenerateFromDate(next.minusDays(leadDays));
        plan.setGeneratedDueDate(null);
        plan.setStatus(StringUtils.blankToDefault(bo.getStatus(), SystemConstants.NORMAL));
        planMapper.insert(plan);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesMaintenancePlanBo bo) {
        MesMaintenancePlan plan = requirePlan(bo.getPlanId());
        requireDevice(bo.getDeviceId());
        int leadDays = leadDays(bo);
        LocalDate next = bo.getNextDueDate();
        boolean dueChanged = !Objects.equals(next, plan.getNextDueDate());
        if (dueChanged && hasOpenOrders(List.of(plan.getPlanId()))) {
            throw new ServiceException("保养计划“" + plan.getPlanName()
                + "”有未完成的保养工单（待保养或保养中），完成或取消后再修改下次到期日期");
        }
        // 改了下次到期日期：按新日期重新生成；没改：本周期已生成过工单时保持不再生成
        LocalDate generated = dueChanged ? null : plan.getGeneratedDueDate();
        int rows = planMapper.update(new MesMaintenancePlan(), Wrappers.<MesMaintenancePlan>lambdaUpdate()
            .set(MesMaintenancePlan::getPlanName, bo.getPlanName().trim())
            .set(MesMaintenancePlan::getDeviceId, bo.getDeviceId())
            .set(MesMaintenancePlan::getMaintenanceContent, bo.getMaintenanceContent())
            .set(MesMaintenancePlan::getCycleDays, bo.getCycleDays())
            .set(MesMaintenancePlan::getLeadDays, leadDays)
            .set(MesMaintenancePlan::getLastMaintenanceDate, bo.getLastMaintenanceDate())
            .set(MesMaintenancePlan::getNextDueDate, next)
            .set(MesMaintenancePlan::getGeneratedDueDate, generated)
            .set(MesMaintenancePlan::getGenerateFromDate, generateFrom(next, leadDays, generated))
            .set(MesMaintenancePlan::getStatus, StringUtils.blankToDefault(bo.getStatus(), SystemConstants.NORMAL))
            .set(MesMaintenancePlan::getRemark, bo.getRemark())
            .eq(MesMaintenancePlan::getPlanId, plan.getPlanId())
            // 读取后计划可能刚被完成或取消工单推进过，以读到的到期日期为条件，避免覆盖
            .eq(MesMaintenancePlan::getNextDueDate, plan.getNextDueDate()));
        if (rows == 0) {
            throw new ServiceException("保养计划已变化（可能刚完成或取消了保养工单），请刷新后重试");
        }
    }

    /**
     * 生成本周期工单的起始日期：下次到期日期减提前天数；这个到期日期已经生成过工单时为空
     */
    static LocalDate generateFrom(LocalDate next, int leadDays, LocalDate generatedDue) {
        if (generatedDue != null && !generatedDue.isBefore(next)) {
            return null;
        }
        return next.minusDays(leadDays);
    }

    /**
     * 提前生成天数：为空时为 0，必须小于周期（否则完成一次保养后会立即生成下一张工单）
     */
    private static int leadDays(MesMaintenancePlanBo bo) {
        int leadDays = bo.getLeadDays() == null ? 0 : bo.getLeadDays();
        if (bo.getCycleDays() != null && leadDays >= bo.getCycleDays()) {
            throw new ServiceException("提前生成天数应小于周期天数");
        }
        return leadDays;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        Set<Long> distinct = new HashSet<>(ids);
        List<MesMaintenancePlan> plans = planMapper.selectByIds(distinct);
        if (plans.size() != distinct.size()) {
            throw new ServiceException("保养计划不存在");
        }
        if (hasOpenOrders(distinct)) {
            throw new ServiceException("保养计划有未完成的保养工单（待保养或保养中），完成或取消后再删除");
        }
        planMapper.deleteByIds(distinct);
    }

    private boolean hasOpenOrders(Collection<Long> planIds) {
        return orderMapper.exists(QueryBuilder.lambda(MesMaintenanceOrder.class)
            .in(MesMaintenanceOrder::getPlanId, planIds)
            .in(MesMaintenanceOrder::getOrderStatus, OPEN_STATUSES)
            .build());
    }

    @Override
    public int generateDue() {
        LocalDate today = LocalDate.now(clock);
        List<MesMaintenancePlan> due = planMapper.selectList(QueryBuilder.lambda(MesMaintenancePlan.class)
            .select(MesMaintenancePlan::getPlanId)
            .eq(MesMaintenancePlan::getStatus, SystemConstants.NORMAL)
            .isNotNull(MesMaintenancePlan::getGenerateFromDate)
            .le(MesMaintenancePlan::getGenerateFromDate, today)
            .orderByAsc(MesMaintenancePlan::getPlanId)
            .build());
        int generated = 0;
        for (MesMaintenancePlan plan : due) {
            // 每个计划在单独的事务中生成：后面的计划失败时，已生成的工单不随之回滚
            if (generator.generate(plan.getPlanId(), today)) {
                generated++;
            }
        }
        return generated;
    }

    private void requireDevice(Long deviceId) {
        if (deviceId == null || deviceMapper.selectById(deviceId) == null) {
            throw new ServiceException("设备不存在");
        }
    }

    private MesMaintenancePlan requirePlan(Long planId) {
        MesMaintenancePlan plan = planId == null ? null : planMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("保养计划不存在");
        }
        return plan;
    }

}
