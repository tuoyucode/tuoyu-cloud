package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.equipment.domain.MesMaintenanceOrder;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;
import org.tuoyu.mes.equipment.enums.MaintenanceOrderStatus;
import org.tuoyu.mes.equipment.mapper.MesMaintenanceOrderMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;

import java.time.LocalDate;

/**
 * 按保养计划生成到期的保养工单（在当前租户中执行；页面上的按钮与定时任务共用）。
 * <p>
 * 多实例安全：生成前用一条带条件的更新“占用”本周期——把计划的 {@code generated_due_date} 推进到本次的到期日期、
 * 清空 {@code generate_from_date}，条件是计划仍启用、下次到期日期未变、已到生成日期、且 {@code generated_due_date} 早于本次到期日期。
 * 多个实例（或按钮与定时任务）同时处理同一计划时，数据库的行锁让它们依次执行，后执行的条件已不成立、影响 0 行，
 * 所以同一计划的同一到期日期只生成一张工单。占用与插入工单在同一事务中，插入失败时一起回滚，下次还会生成。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Component
public class MaintenanceOrderGenerator {

    private final MesMaintenancePlanMapper planMapper;
    private final MesMaintenanceOrderMapper orderMapper;
    private final IMesCodeService codeService;
    private final IMesStatusLogService statusLogService;

    /**
     * 计划已到生成日期时生成本周期的保养工单
     *
     * @param planId 保养计划
     * @param today  今天
     * @return 是否生成了工单（没到日期、已停用、本周期已生成或被其他实例抢先时为 false）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean generate(Long planId, LocalDate today) {
        MesMaintenancePlan plan = planMapper.selectById(planId);
        if (plan == null || !SystemConstants.NORMAL.equals(plan.getStatus()) || plan.getGenerateFromDate() == null
            || plan.getGenerateFromDate().isAfter(today) || plan.getNextDueDate() == null) {
            return false;
        }
        LocalDate due = plan.getNextDueDate();
        int claimed = planMapper.update(new MesMaintenancePlan(), Wrappers.<MesMaintenancePlan>lambdaUpdate()
            .set(MesMaintenancePlan::getGeneratedDueDate, due)
            .set(MesMaintenancePlan::getGenerateFromDate, null)
            .eq(MesMaintenancePlan::getPlanId, planId)
            .eq(MesMaintenancePlan::getStatus, SystemConstants.NORMAL)
            .eq(MesMaintenancePlan::getNextDueDate, due)
            .le(MesMaintenancePlan::getGenerateFromDate, today)
            .and(generated -> generated.isNull(MesMaintenancePlan::getGeneratedDueDate)
                .or().lt(MesMaintenancePlan::getGeneratedDueDate, due)));
        if (claimed == 0) {
            return false;
        }
        MesMaintenanceOrder order = new MesMaintenanceOrder();
        order.setOrderCode(codeService.next(CodeRules.MAINTENANCE_ORDER));
        order.setPlanId(planId);
        order.setDeviceId(plan.getDeviceId());
        order.setDueDate(due);
        order.setMaintenanceContent(plan.getMaintenanceContent());
        order.setOrderStatus(MaintenanceOrderStatus.PENDING.code());
        // 工单归属计划的创建部门与创建者：无论由谁点按钮、还是由定时任务生成，同一计划的工单都由同样的人在数据权限内看到
        order.setCreateDept(plan.getCreateDept());
        order.setCreateBy(plan.getCreateBy());
        orderMapper.insert(order);
        statusLogService.record(BizType.MAINTENANCE_ORDER, order.getOrderId(), order.getOrderCode(), "generate", null,
            MaintenanceOrderStatus.PENDING.code(), "保养计划“" + plan.getPlanName() + "”到期（" + due + "）");
        return true;
    }

}
