package org.tuoyu.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.equipment.domain.MesMaintenanceOrder;
import org.tuoyu.mes.equipment.domain.MesMaintenancePlan;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceCompleteBo;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenanceOrderBo;
import org.tuoyu.mes.equipment.domain.bo.MesMaintenancePlanBo;
import org.tuoyu.mes.equipment.domain.vo.MesMaintenanceOrderVo;
import org.tuoyu.mes.equipment.domain.vo.MesMaintenancePlanVo;
import org.tuoyu.mes.equipment.mapper.MesMaintenanceOrderMapper;
import org.tuoyu.mes.equipment.mapper.MesMaintenancePlanMapper;
import org.tuoyu.mes.masterdata.domain.MesDevice;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.system.api.RemoteUserService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("保养计划与保养工单：到期生成（同一周期只生成一次）、状态流转与计划推进")
class MaintenanceServiceTest {

    static {
        MesTestSupport.init();
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private static final Clock CLOCK = Clock.fixed(TODAY.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant(),
        ZoneId.systemDefault());

    private final MesMaintenancePlanMapper planMapper = MesTestSupport.mapper(MesMaintenancePlanMapper.class);
    private final MesMaintenanceOrderMapper orderMapper = MesTestSupport.mapper(MesMaintenanceOrderMapper.class);
    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);

    private final MaintenanceOrderGenerator generator = new MaintenanceOrderGenerator(planMapper, orderMapper,
        codeService, statusLogService);
    private final MesMaintenancePlanServiceImpl planService = new MesMaintenancePlanServiceImpl(planMapper, orderMapper,
        deviceMapper, generator);
    private final MesMaintenanceOrderServiceImpl orderService = new MesMaintenanceOrderServiceImpl(orderMapper,
        planMapper, deviceMapper, statusLogService, mock(RemoteUserService.class));

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        previous = MesTestSupport.loginAs(8L);
        planService.setClock(CLOCK);
        orderService.setClock(CLOCK);
        when(codeService.next(CodeRules.MAINTENANCE_ORDER)).thenReturn("BY001");
        when(orderMapper.insert(any(MesMaintenanceOrder.class))).thenAnswer(invocation -> {
            invocation.<MesMaintenanceOrder>getArgument(0).setOrderId(700L);
            return 1;
        });
        MesDevice device = new MesDevice();
        device.setDeviceId(30L);
        device.setDeviceName("冲压机");
        when(deviceMapper.selectById(30L)).thenReturn(device);
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.restore(previous);
    }

    private static MesMaintenancePlan plan(LocalDate next, LocalDate generateFrom, LocalDate generated) {
        MesMaintenancePlan plan = new MesMaintenancePlan();
        plan.setPlanId(60L);
        plan.setPlanName("月度保养");
        plan.setDeviceId(30L);
        plan.setMaintenanceContent("润滑、紧固");
        plan.setCycleDays(30);
        plan.setLeadDays(3);
        plan.setNextDueDate(next);
        plan.setGenerateFromDate(generateFrom);
        plan.setGeneratedDueDate(generated);
        plan.setStatus("0");
        plan.setCreateBy(5L);
        plan.setCreateDept(103L);
        return plan;
    }

    private static MesMaintenanceOrder order(String status) {
        MesMaintenanceOrder order = new MesMaintenanceOrder();
        order.setOrderId(700L);
        order.setOrderCode("BY001");
        order.setPlanId(60L);
        order.setDeviceId(30L);
        order.setDueDate(TODAY.plusDays(2));
        order.setOrderStatus(status);
        return order;
    }

    private static String sqlSet(ArgumentCaptor<Wrapper<MesMaintenancePlan>> captor) {
        return ((LambdaUpdateWrapper<?>) captor.getValue()).getSqlSet();
    }

    @Test
    @DisplayName("生成：先以条件更新占用本周期，再插入工单；工单归属计划的创建部门与创建者")
    void generate() {
        when(planMapper.selectById(60L)).thenReturn(plan(TODAY.plusDays(2), TODAY.minusDays(1), null));
        when(planMapper.update(any(), any())).thenReturn(1);

        assertThat(generator.generate(60L, TODAY)).isTrue();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Wrapper<MesMaintenancePlan>> claim = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(planMapper).update(any(), claim.capture());
        assertThat(sqlSet(claim)).contains("generated_due_date").contains("generate_from_date");
        String where = claim.getValue().getSqlSegment();
        assertThat(where).contains("next_due_date").contains("generated_due_date IS NULL").contains("generated_due_date <");
        ArgumentCaptor<MesMaintenanceOrder> inserted = ArgumentCaptor.forClass(MesMaintenanceOrder.class);
        verify(orderMapper).insert(inserted.capture());
        MesMaintenanceOrder order = inserted.getValue();
        assertThat(order.getOrderCode()).isEqualTo("BY001");
        assertThat(order.getDueDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(order.getOrderStatus()).isEqualTo("pending");
        assertThat(order.getMaintenanceContent()).isEqualTo("润滑、紧固");
        assertThat(order.getCreateBy()).isEqualTo(5L);
        assertThat(order.getCreateDept()).isEqualTo(103L);
        verify(statusLogService).record(eq(BizType.MAINTENANCE_ORDER), eq(700L), eq("BY001"), eq("generate"), isNull(),
            eq("pending"), anyString());
    }

    @Test
    @DisplayName("生成：其他实例已占用（影响 0 行）、未到生成日期、已停用或本周期已生成时不生成")
    void generateSkipped() {
        when(planMapper.selectById(60L)).thenReturn(plan(TODAY.plusDays(2), TODAY, null));
        when(planMapper.update(any(), any())).thenReturn(0);
        assertThat(generator.generate(60L, TODAY)).isFalse();

        when(planMapper.selectById(60L)).thenReturn(plan(TODAY.plusDays(5), TODAY.plusDays(2), null));
        assertThat(generator.generate(60L, TODAY)).isFalse();

        when(planMapper.selectById(60L)).thenReturn(plan(TODAY.plusDays(2), null, TODAY.plusDays(2)));
        assertThat(generator.generate(60L, TODAY)).isFalse();

        MesMaintenancePlan disabled = plan(TODAY, TODAY, null);
        disabled.setStatus("1");
        when(planMapper.selectById(60L)).thenReturn(disabled);
        assertThat(generator.generate(60L, TODAY)).isFalse();
        assertThat(generator.generate(61L, TODAY)).isFalse();

        verify(orderMapper, never()).insert(any(MesMaintenanceOrder.class));
        verify(planMapper, times(1)).update(any(), any());
    }

    @Test
    @DisplayName("按钮生成：当前租户中已到生成日期的计划逐个生成，返回张数")
    void generateDue() {
        MesMaintenancePlan due = plan(TODAY.plusDays(2), TODAY.minusDays(1), null);
        when(planMapper.selectList(any(Wrapper.class))).thenReturn(List.of(due));
        when(planMapper.selectById(60L)).thenReturn(due);
        when(planMapper.update(any(), any())).thenReturn(1);

        assertThat(planService.generateDue()).isEqualTo(1);
    }

    @Test
    @DisplayName("新增计划：下次到期日期默认为上次保养日期加周期（没有时为今天），生成日期为到期日期减提前天数")
    void insertPlan() {
        MesMaintenancePlanBo bo = new MesMaintenancePlanBo();
        bo.setPlanName(" 月度保养 ");
        bo.setDeviceId(30L);
        bo.setMaintenanceContent("润滑");
        bo.setCycleDays(30);
        bo.setLeadDays(5);
        bo.setLastMaintenanceDate(TODAY.minusDays(10));
        planService.insertByBo(bo);

        ArgumentCaptor<MesMaintenancePlan> captor = ArgumentCaptor.forClass(MesMaintenancePlan.class);
        verify(planMapper).insert(captor.capture());
        assertThat(captor.getValue().getNextDueDate()).isEqualTo(TODAY.plusDays(20));
        assertThat(captor.getValue().getGenerateFromDate()).isEqualTo(TODAY.plusDays(15));
        assertThat(captor.getValue().getPlanName()).isEqualTo("月度保养");
        assertThat(captor.getValue().getStatus()).isEqualTo("0");

        bo.setLastMaintenanceDate(null);
        bo.setLeadDays(null);
        planService.insertByBo(bo);
        verify(planMapper, times(2)).insert(captor.capture());
        assertThat(captor.getValue().getNextDueDate()).isEqualTo(TODAY);
        assertThat(captor.getValue().getLeadDays()).isZero();

        bo.setLeadDays(30);
        assertThatThrownBy(() -> planService.insertByBo(bo)).hasMessageContaining("提前生成天数应小于周期");
        bo.setDeviceId(99L);
        assertThatThrownBy(() -> planService.insertByBo(bo)).hasMessageContaining("设备不存在");
    }

    @Test
    @DisplayName("修改计划：有未完成的工单时不能改下次到期日期；改了到期日期时重新生成；以读到的到期日期为条件")
    void updatePlan() {
        MesMaintenancePlan stored = plan(TODAY.plusDays(2), null, TODAY.plusDays(2));
        when(planMapper.selectById(60L)).thenReturn(stored);
        MesMaintenancePlanBo bo = new MesMaintenancePlanBo();
        bo.setPlanId(60L);
        bo.setPlanName("月度保养");
        bo.setDeviceId(30L);
        bo.setMaintenanceContent("润滑");
        bo.setCycleDays(30);
        bo.setLeadDays(3);
        bo.setNextDueDate(TODAY.plusDays(9));
        when(orderMapper.exists(any())).thenReturn(true);
        assertThatThrownBy(() -> planService.updateByBo(bo)).hasMessageContaining("有未完成的保养工单");

        when(orderMapper.exists(any())).thenReturn(false);
        when(planMapper.update(any(), any())).thenReturn(1);
        planService.updateByBo(bo);

        when(planMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> planService.updateByBo(bo)).hasMessageContaining("保养计划已变化");

        assertThat(MesMaintenancePlanServiceImpl.generateFrom(TODAY.plusDays(9), 3, null)).isEqualTo(TODAY.plusDays(6));
        assertThat(MesMaintenancePlanServiceImpl.generateFrom(TODAY.plusDays(2), 3, TODAY.plusDays(2))).isNull();
        assertThat(MesMaintenancePlanServiceImpl.generateFrom(TODAY.plusDays(32), 3, TODAY.plusDays(2)))
            .isEqualTo(TODAY.plusDays(29));
    }

    @Test
    @DisplayName("删除计划：有未完成的工单时拒绝；查询带逾期标记")
    void deleteAndQueryPlan() {
        when(planMapper.selectByIds(any())).thenReturn(List.of(plan(TODAY.minusDays(1), TODAY, null)));
        when(orderMapper.exists(any())).thenReturn(true);
        assertThatThrownBy(() -> planService.deleteByIds(List.of(60L))).hasMessageContaining("未完成的保养工单");
        when(orderMapper.exists(any())).thenReturn(false);
        planService.deleteByIds(List.of(60L));
        verify(planMapper).deleteByIds(any());
        assertThatThrownBy(() -> planService.deleteByIds(List.of(60L, 61L))).hasMessageContaining("保养计划不存在");

        when(planMapper.selectById(60L)).thenReturn(plan(TODAY.minusDays(1), TODAY, null));
        MesMaintenancePlanVo vo = planService.queryById(60L);
        assertThat(vo.getOverdue()).isTrue();
        assertThat(planService.queryPageList(new MesMaintenancePlanBo(), new PageParam(1, 10)).getItems()).isEmpty();
    }

    @Test
    @DisplayName("开始保养：待保养 → 保养中，保养人为当前用户；其他状态不能开始")
    void start() {
        when(orderMapper.selectById(700L)).thenReturn(order("pending"));
        when(orderMapper.update(any(), any())).thenReturn(1);
        orderService.start(700L);
        ArgumentCaptor<MesMaintenanceOrder> change = ArgumentCaptor.forClass(MesMaintenanceOrder.class);
        verify(orderMapper).update(change.capture(), any());
        assertThat(change.getValue().getOrderStatus()).isEqualTo("in_progress");
        assertThat(change.getValue().getExecutorUserId()).isEqualTo(8L);
        verify(statusLogService).record(BizType.MAINTENANCE_ORDER, 700L, "BY001", "start", "pending", "in_progress", null);

        when(orderMapper.selectById(700L)).thenReturn(order("completed"));
        assertThatThrownBy(() -> orderService.start(700L)).hasMessageContaining("不能开始保养");
        when(orderMapper.selectById(700L)).thenReturn(order("pending"));
        when(orderMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> orderService.start(700L)).hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("完成保养：保养中 → 已完成；计划的上次保养日期为今天，下次到期日期为今天加周期")
    void complete() {
        when(orderMapper.selectById(700L)).thenReturn(order("in_progress"));
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(planMapper.selectById(60L)).thenReturn(plan(TODAY.plusDays(2), null, TODAY.plusDays(2)));
        when(planMapper.update(any(), any())).thenReturn(1);
        MesMaintenanceCompleteBo bo = new MesMaintenanceCompleteBo();
        bo.setActualContent(" 更换润滑油 ");
        bo.setWorkMinutes(45);
        bo.setSpareParts("润滑油 2L");

        orderService.complete(700L, bo);

        ArgumentCaptor<MesMaintenanceOrder> change = ArgumentCaptor.forClass(MesMaintenanceOrder.class);
        verify(orderMapper).update(change.capture(), any());
        assertThat(change.getValue().getOrderStatus()).isEqualTo("completed");
        assertThat(change.getValue().getActualContent()).isEqualTo("更换润滑油");
        assertThat(change.getValue().getWorkMinutes()).isEqualTo(45);
        assertThat(change.getValue().getFinishTime()).isEqualTo(LocalDateTime.now(CLOCK));
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Wrapper<MesMaintenancePlan>> advance = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(planMapper).update(any(), advance.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) advance.getValue();
        assertThat(wrapper.getSqlSet()).contains("last_maintenance_date").contains("next_due_date")
            .contains("generate_from_date");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TODAY, TODAY.plusDays(30), TODAY.plusDays(27));
        // 以计划的下次到期日期仍是本单的到期日期为条件
        assertThat(wrapper.getSqlSegment()).contains("next_due_date =");
        verify(statusLogService).record(BizType.MAINTENANCE_ORDER, 700L, "BY001", "complete", "in_progress", "completed",
            null);

        when(orderMapper.selectById(700L)).thenReturn(order("pending"));
        assertThatThrownBy(() -> orderService.complete(700L, bo)).hasMessageContaining("不能完成");
    }

    @Test
    @DisplayName("完成保养：计划已被调整时不推进，并在履历中说明")
    void completeWithoutAdvancing() {
        when(orderMapper.selectById(700L)).thenReturn(order("in_progress"));
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(planMapper.selectById(60L)).thenReturn(plan(TODAY.plusDays(9), TODAY, null));
        when(planMapper.update(any(), any())).thenReturn(0);
        MesMaintenanceCompleteBo bo = new MesMaintenanceCompleteBo();
        bo.setActualContent("更换润滑油");
        bo.setWorkMinutes(45);

        orderService.complete(700L, bo);

        verify(statusLogService).record(eq(BizType.MAINTENANCE_ORDER), eq(700L), eq("BY001"), eq("complete"),
            eq("in_progress"), eq("completed"), eq("保养计划的下次到期日期已调整，未按本单推进"));
    }

    @Test
    @DisplayName("取消：待保养、保养中 → 已取消，原因必填；跳过本周期，下次到期日期为本单到期日期加周期")
    void cancel() {
        assertThatThrownBy(() -> orderService.cancel(700L, "")).hasMessageContaining("取消原因");
        when(orderMapper.selectById(700L)).thenReturn(order("in_progress"));
        when(orderMapper.update(any(), any())).thenReturn(1);
        when(planMapper.selectById(60L)).thenReturn(plan(TODAY.plusDays(2), null, TODAY.plusDays(2)));
        when(planMapper.update(any(), any())).thenReturn(1);

        orderService.cancel(700L, "设备停用");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Wrapper<MesMaintenancePlan>> advance = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(planMapper).update(any(), advance.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) advance.getValue();
        assertThat(wrapper.getSqlSet()).doesNotContain("last_maintenance_date");
        assertThat(wrapper.getParamNameValuePairs().values()).contains(TODAY.plusDays(32));
        verify(statusLogService).record(BizType.MAINTENANCE_ORDER, 700L, "BY001", "cancel", "in_progress", "cancelled",
            "设备停用");

        when(orderMapper.selectById(700L)).thenReturn(order("cancelled"));
        assertThatThrownBy(() -> orderService.cancel(700L, "再次")).hasMessageContaining("不能取消");
    }

    @Test
    @DisplayName("工单查询：不在数据权限内按不存在处理；状态条件必须有效；带逾期标记")
    void queryOrders() {
        assertThatThrownBy(() -> orderService.queryById(701L)).hasMessageContaining("保养工单不存在");
        MesMaintenanceOrder overdue = order("pending");
        overdue.setDueDate(TODAY.minusDays(1));
        when(orderMapper.selectById(700L)).thenReturn(overdue);
        MesMaintenanceOrderVo vo = orderService.queryById(700L);
        assertThat(vo.getOverdue()).isTrue();
        orderService.logs(700L);
        verify(statusLogService).list(BizType.MAINTENANCE_ORDER, 700L);

        MesMaintenanceOrderBo bo = new MesMaintenanceOrderBo();
        bo.setOrderStatus("pending,in_progress");
        bo.setBeginDueDate(TODAY.minusDays(30));
        assertThat(orderService.queryPageList(bo, new PageParam(1, 10)).getItems()).isEmpty();
        assertThat(orderService.countList(bo)).isZero();
        when(orderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(order("completed")));
        assertThat(orderService.queryList(bo)).hasSize(1);
        bo.setOrderStatus("done");
        assertThatThrownBy(() -> orderService.queryList(bo)).hasMessageContaining("工单状态不正确");
    }

}
