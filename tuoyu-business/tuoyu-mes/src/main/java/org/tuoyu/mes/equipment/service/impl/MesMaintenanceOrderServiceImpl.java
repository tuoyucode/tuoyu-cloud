package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.common.MesLookups;
import org.tuoyu.mes.common.StateGuard;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.equipment.domain.MesMaintenanceOrder;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceCompleteBo;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceOrderBo;
import org.tuoyu.mes.equipment.domain.vo.MesMaintenanceOrderVo;
import org.tuoyu.mes.equipment.enums.MaintenanceOrderStatus;
import org.tuoyu.mes.equipment.mapper.MesMaintenanceOrderMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;
import org.tuoyu.mes.equipment.service.IMesMaintenanceOrderService;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.material.support.SafeLookup;
import org.tuoyu.mes.material.support.UserNicknames;
import org.tuoyu.system.api.RemoteUserService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 保养工单
 * <p>
 * 列表、导出与按 ID 的查看、操作都限定在当前用户的数据权限内（按创建部门与创建者，生成时取自保养计划），
 * 不在其内的工单按不存在处理。状态动作用 {@link StateGuard} 校验，并以原状态为条件更新，并发操作时只有一个成功。
 * <p>
 * 完成或取消时推进保养计划：以计划的下次到期日期仍是本单的到期日期为条件，计划已被修改（或已被推进）时不再改动它。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesMaintenanceOrderServiceImpl implements IMesMaintenanceOrderService {

    private static final String OBJECT = "保养工单";

    private final MesMaintenanceOrderMapper orderMapper;
    private final MesMaintenancePlanMapper planMapper;
    private final MesDeviceMapper deviceMapper;
    private final IMesStatusLogService statusLogService;
    private final RemoteUserService userService;

    private Clock clock = Clock.systemDefaultZone();

    /**
     * 测试中替换时钟
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MesMaintenanceOrderVo queryById(Long orderId) {
        MesMaintenanceOrderVo vo = BeanMapper.map(requireOrder(orderId), MesMaintenanceOrderVo.class);
        fill(List.of(vo));
        return vo;
    }

    @Override
    public PageResult<MesMaintenanceOrderVo> queryPageList(MesMaintenanceOrderBo bo, PageParam pageParam) {
        PageResult<MesMaintenanceOrderVo> result = PageResults.of(orderMapper.selectPageInScope(
            pageParam.toPage(MesMaintenanceOrder.class), buildQueryWrapper(bo, !pageParam.hasSort())),
            MesMaintenanceOrderVo.class);
        fill(result.getItems());
        return result;
    }

    @Override
    public List<MesMaintenanceOrderVo> queryList(MesMaintenanceOrderBo bo) {
        List<MesMaintenanceOrderVo> vos = BeanMapper.mapList(orderMapper.selectListInScope(buildQueryWrapper(bo, true)),
            MesMaintenanceOrderVo.class);
        fill(vos);
        Map<Long, String> names = UserNicknames.of(userService, MesLookups.ids(vos, MesMaintenanceOrderVo::getExecutorUserId));
        vos.forEach(vo -> vo.setExecutorName(SafeLookup.get(names, vo.getExecutorUserId())));
        return vos;
    }

    @Override
    public long countList(MesMaintenanceOrderBo bo) {
        return orderMapper.countInScope(buildQueryWrapper(bo, false));
    }

    private LambdaQueryWrapper<MesMaintenanceOrder> buildQueryWrapper(MesMaintenanceOrderBo bo, boolean defaultOrder) {
        List<String> statuses = StringUtils.isBlank(bo.getOrderStatus()) ? List.of()
            : StringUtils.str2List(bo.getOrderStatus(), StringUtils.SEPARATOR, true, true);
        for (String status : statuses) {
            CodeEnum.of(MaintenanceOrderStatus.class, status, "工单状态");
        }
        return QueryBuilder.lambda(MesMaintenanceOrder.class)
            .likeIfText(MesMaintenanceOrder::getOrderCode, bo.getOrderCode())
            .eqIfPresent(MesMaintenanceOrder::getDeviceId, bo.getDeviceId())
            .eqIfPresent(MesMaintenanceOrder::getPlanId, bo.getPlanId())
            .inIfNotEmpty(MesMaintenanceOrder::getOrderStatus, statuses)
            .geIfPresent(MesMaintenanceOrder::getDueDate, bo.getBeginDueDate())
            .leIfPresent(MesMaintenanceOrder::getDueDate, bo.getEndDueDate())
            .orderByDesc(defaultOrder, MesMaintenanceOrder::getDueDate)
            .orderByDesc(defaultOrder, MesMaintenanceOrder::getOrderId)
            .build();
    }

    /**
     * 设备、计划名称；未完成且已过到期日期时标记为逾期
     */
    private void fill(List<MesMaintenanceOrderVo> vos) {
        if (vos.isEmpty()) {
            return;
        }
        Map<Long, MesDevice> devices = MesLookups.byIds(deviceMapper,
            MesLookups.ids(vos, MesMaintenanceOrderVo::getDeviceId), MesDevice::getDeviceId);
        Map<Long, MesMaintenancePlan> plans = MesLookups.byIds(planMapper,
            MesLookups.ids(vos, MesMaintenanceOrderVo::getPlanId), MesMaintenancePlan::getPlanId);
        LocalDate today = LocalDate.now(clock);
        for (MesMaintenanceOrderVo vo : vos) {
            MesDevice device = SafeLookup.get(devices, vo.getDeviceId());
            if (device != null) {
                vo.setDeviceCode(device.getDeviceCode());
                vo.setDeviceName(device.getDeviceName());
            }
            MesMaintenancePlan plan = SafeLookup.get(plans, vo.getPlanId());
            vo.setPlanName(plan == null ? null : plan.getPlanName());
            vo.setOverdue(MesMaintenancePlanServiceImpl.OPEN_STATUSES.contains(vo.getOrderStatus())
                && vo.getDueDate() != null && vo.getDueDate().isBefore(today));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(Long orderId) {
        MesMaintenanceOrder order = requireOrder(orderId);
        StateGuard.require(OBJECT, MaintenanceOrderStatus.class, order.getOrderStatus(), "开始保养",
            MaintenanceOrderStatus.PENDING);
        MesMaintenanceOrder change = new MesMaintenanceOrder();
        change.setExecutorUserId(UserContext.userId());
        change.setStartTime(LocalDateTime.now(clock));
        changeStatus(order, MaintenanceOrderStatus.PENDING, MaintenanceOrderStatus.IN_PROGRESS, change);
        statusLogService.record(BizType.MAINTENANCE_ORDER, order.getOrderId(), order.getOrderCode(), "start",
            MaintenanceOrderStatus.PENDING.code(), MaintenanceOrderStatus.IN_PROGRESS.code(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long orderId, MesMaintenanceCompleteBo bo) {
        MesMaintenanceOrder order = requireOrder(orderId);
        StateGuard.require(OBJECT, MaintenanceOrderStatus.class, order.getOrderStatus(), "完成",
            MaintenanceOrderStatus.IN_PROGRESS);
        LocalDateTime now = LocalDateTime.now(clock);
        MesMaintenanceOrder change = new MesMaintenanceOrder();
        change.setFinishTime(now);
        change.setActualContent(bo.getActualContent().trim());
        change.setWorkMinutes(bo.getWorkMinutes());
        change.setSpareParts(StringUtils.trimToNull(bo.getSpareParts()));
        change.setRemark(StringUtils.trimToNull(bo.getRemark()));
        changeStatus(order, MaintenanceOrderStatus.IN_PROGRESS, MaintenanceOrderStatus.COMPLETED, change);
        LocalDate finished = now.toLocalDate();
        boolean advanced = advancePlan(order, finished, plan -> finished.plusDays(plan.getCycleDays()));
        statusLogService.record(BizType.MAINTENANCE_ORDER, order.getOrderId(), order.getOrderCode(), "complete",
            MaintenanceOrderStatus.IN_PROGRESS.code(), MaintenanceOrderStatus.COMPLETED.code(),
            advanced ? null : "保养计划的下次到期日期已调整，未按本单推进");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId, String reason) {
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("请填写取消原因");
        }
        MesMaintenanceOrder order = requireOrder(orderId);
        MaintenanceOrderStatus from = StateGuard.require(OBJECT, MaintenanceOrderStatus.class, order.getOrderStatus(),
            "取消", MaintenanceOrderStatus.PENDING, MaintenanceOrderStatus.IN_PROGRESS);
        changeStatus(order, from, MaintenanceOrderStatus.CANCELLED, new MesMaintenanceOrder());
        // 跳过本周期：上次保养日期不变，下次到期日期为本单到期日期加周期
        advancePlan(order, null, plan -> order.getDueDate().plusDays(plan.getCycleDays()));
        statusLogService.record(BizType.MAINTENANCE_ORDER, order.getOrderId(), order.getOrderCode(), "cancel",
            from.code(), MaintenanceOrderStatus.CANCELLED.code(), reason.trim());
    }

    /**
     * 推进保养计划的下次到期日期并重新计算生成日期，条件是计划的下次到期日期仍是本单的到期日期
     *
     * @param order       保养工单
     * @param lastDate    新的上次保养日期，为空时不改
     * @param nextDueDate 按计划计算新的下次到期日期
     * @return 是否推进了计划（计划已删除或已被修改时为 false）
     */
    private boolean advancePlan(MesMaintenanceOrder order, LocalDate lastDate,
                                Function<MesMaintenancePlan, LocalDate> nextDueDate) {
        MesMaintenancePlan plan = planMapper.selectById(order.getPlanId());
        if (plan == null || plan.getCycleDays() == null) {
            return false;
        }
        LocalDate next = nextDueDate.apply(plan);
        int leadDays = plan.getLeadDays() == null ? 0 : plan.getLeadDays();
        int rows = planMapper.update(new MesMaintenancePlan(), Wrappers.<MesMaintenancePlan>lambdaUpdate()
            .set(lastDate != null, MesMaintenancePlan::getLastMaintenanceDate, lastDate)
            .set(MesMaintenancePlan::getNextDueDate, next)
            .set(MesMaintenancePlan::getGenerateFromDate,
                MesMaintenancePlanServiceImpl.generateFrom(next, leadDays, plan.getGeneratedDueDate()))
            .eq(MesMaintenancePlan::getPlanId, plan.getPlanId())
            .eq(MesMaintenancePlan::getNextDueDate, order.getDueDate()));
        return rows > 0;
    }

    /**
     * 带原状态条件更新状态，影响 0 行说明状态已被其他操作改变
     */
    private void changeStatus(MesMaintenanceOrder order, MaintenanceOrderStatus from, MaintenanceOrderStatus to,
                              MesMaintenanceOrder change) {
        change.setOrderStatus(to.code());
        int rows = orderMapper.update(change, Wrappers.<MesMaintenanceOrder>lambdaUpdate()
            .eq(MesMaintenanceOrder::getOrderId, order.getOrderId())
            .eq(MesMaintenanceOrder::getOrderStatus, from.code()));
        if (rows == 0) {
            throw new ServiceException("保养工单的状态已变化，请刷新后重试");
        }
    }

    @Override
    public List<MesStatusLogVo> logs(Long orderId) {
        requireOrder(orderId);
        return statusLogService.list(BizType.MAINTENANCE_ORDER, orderId);
    }

    /**
     * 按 ID 取当前用户数据权限内的保养工单，不存在或不在数据权限内时抛出业务异常
     */
    private MesMaintenanceOrder requireOrder(Long orderId) {
        MesMaintenanceOrder order = orderId == null ? null : orderMapper.selectByIdInScope(orderId);
        if (order == null) {
            throw new ServiceException("保养工单不存在");
        }
        return order;
    }

}
