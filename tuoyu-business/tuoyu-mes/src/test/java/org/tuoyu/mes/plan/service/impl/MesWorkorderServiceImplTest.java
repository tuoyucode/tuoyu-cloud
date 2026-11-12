package org.tuoyu.mes.plan.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesLine;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesRoute;
import org.tuoyu.mes.masterdata.domain.MesRouteOperation;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteOperationMapper;
import org.tuoyu.mes.masterdata.mapper.MesStationMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.domain.bo.MesWorkorderBo;
import org.tuoyu.mes.plan.domain.vo.MesWorkorderVo;
import org.tuoyu.mes.plan.mapper.MesOperationTaskMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.production.mapper.MesProductionReportMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.mes.trace.service.IMesSnService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("生产工单：草稿校验、下达生成任务与状态流转")
class MesWorkorderServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesOperationTaskMapper taskMapper = MesTestSupport.mapper(MesOperationTaskMapper.class);
    private final MesProductionReportMapper reportMapper = MesTestSupport.mapper(MesProductionReportMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesBomMapper bomMapper = MesTestSupport.mapper(MesBomMapper.class);
    private final MesRouteMapper routeMapper = MesTestSupport.mapper(MesRouteMapper.class);
    private final MesRouteOperationMapper routeOperationMapper = MesTestSupport.mapper(MesRouteOperationMapper.class);
    private final MesFactoryMapper factoryMapper = MesTestSupport.mapper(MesFactoryMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesStationMapper stationMapper = MesTestSupport.mapper(MesStationMapper.class);
    private final MesTeamMapper teamMapper = MesTestSupport.mapper(MesTeamMapper.class);
    private final IMesCodeService codeService = Mockito.mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = Mockito.mock(IMesStatusLogService.class);
    private final IMesSnService snService = Mockito.mock(IMesSnService.class);

    private final MesOperationTaskAssembler assembler = new MesOperationTaskAssembler(workorderMapper, taskMapper,
        materialMapper, workshopMapper, lineMapper, stationMapper, teamMapper);

    private final MesWorkorderServiceImpl service = new MesWorkorderServiceImpl(workorderMapper, taskMapper,
        reportMapper, materialMapper, bomMapper, routeMapper, routeOperationMapper, factoryMapper, workshopMapper,
        lineMapper, codeService, statusLogService, assembler, snService);

    // ---------------------------------------------------------------- 测试数据

    private static MesMaterial material(String type, String status) {
        MesMaterial material = new MesMaterial();
        material.setMaterialId(1L);
        material.setMaterialCode("P001");
        material.setMaterialName("控制器");
        material.setMaterialType(type);
        material.setUnit("台");
        material.setStatus(status);
        return material;
    }

    private static MesRoute route(Long materialId) {
        MesRoute route = new MesRoute();
        route.setRouteId(2L);
        route.setMaterialId(materialId);
        route.setRouteName("控制器装配");
        route.setRouteVersion("V1");
        route.setStatus("0");
        return route;
    }

    private static MesWorkshop workshop() {
        MesWorkshop workshop = new MesWorkshop();
        workshop.setWorkshopId(4L);
        workshop.setFactoryId(3L);
        workshop.setWorkshopName("总装车间");
        return workshop;
    }

    private static MesWorkorderBo bo() {
        MesWorkorderBo bo = new MesWorkorderBo();
        bo.setMaterialId(1L);
        bo.setRouteId(2L);
        bo.setWorkshopId(4L);
        bo.setPlanQty(new BigDecimal("100"));
        return bo;
    }

    static MesWorkorder workorder(String status) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(100L);
        workorder.setWorkorderCode("WO001");
        workorder.setMaterialId(1L);
        workorder.setRouteId(2L);
        workorder.setFactoryId(3L);
        workorder.setWorkshopId(4L);
        workorder.setLineId(5L);
        workorder.setPlanQty(new BigDecimal("100"));
        workorder.setCompletedQty(BigDecimal.ZERO);
        workorder.setDefectQty(BigDecimal.ZERO);
        workorder.setWorkorderStatus(status);
        return workorder;
    }

    private static MesOperationTask task(long id, int seqNo, String status) {
        MesOperationTask task = new MesOperationTask();
        task.setTaskId(id);
        task.setTaskCode("OT" + id);
        task.setWorkorderId(100L);
        task.setSeqNo(seqNo);
        task.setTaskStatus(status);
        return task;
    }

    private static MesStatusLogVo log(String action, String from, String to) {
        MesStatusLogVo log = new MesStatusLogVo();
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        return log;
    }

    /**
     * 按 ID 读取、按数据权限读取与加锁读取都返回该工单；不在数据权限内的情形在各测试中单独设置
     */
    private void givenWorkorder(MesWorkorder workorder) {
        when(workorderMapper.selectById(workorder.getWorkorderId())).thenReturn(workorder);
        doReturn(workorder).when(workorderMapper).selectByIdInScope(workorder.getWorkorderId());
        doReturn(workorder).when(workorderMapper).selectByIdForUpdate(workorder.getWorkorderId());
    }

    private void masterdataReady() {
        when(materialMapper.selectById(1L)).thenReturn(material("product", "0"));
        when(routeMapper.selectById(2L)).thenReturn(route(1L));
        when(workshopMapper.selectById(4L)).thenReturn(workshop());
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<MesWorkorder> capturedWorkorderUpdate() {
        ArgumentCaptor<Wrapper<MesWorkorder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(workorderMapper).update(isNull(), captor.capture());
        return (LambdaUpdateWrapper<MesWorkorder>) captor.getValue();
    }

    // ---------------------------------------------------------------- 新增与修改

    @Nested
    @DisplayName("新增与修改")
    class Draft {

        @Test
        @DisplayName("新增时生成编号、带出工厂、取默认物料清单，状态为草稿并记录履历")
        void insertDraft() {
            masterdataReady();
            MesBom bom = new MesBom();
            bom.setBomId(7L);
            when(bomMapper.selectOne(any(Wrapper.class))).thenReturn(bom);
            when(codeService.next(CodeRules.WORKORDER)).thenReturn("WO202611090001");
            when(workorderMapper.insert(any(MesWorkorder.class))).thenAnswer(invocation -> {
                invocation.<MesWorkorder>getArgument(0).setWorkorderId(100L);
                return 1;
            });
            MesWorkorderBo bo = bo();

            service.insertByBo(bo);

            ArgumentCaptor<MesWorkorder> captor = ArgumentCaptor.forClass(MesWorkorder.class);
            verify(workorderMapper).insert(captor.capture());
            MesWorkorder saved = captor.getValue();
            assertThat(saved.getWorkorderCode()).isEqualTo("WO202611090001");
            assertThat(saved.getFactoryId()).isEqualTo(3L);
            assertThat(saved.getBomId()).isEqualTo(7L);
            assertThat(saved.getWorkorderStatus()).isEqualTo("draft");
            assertThat(saved.getSourceType()).isEqualTo("manual");
            assertThat(saved.getPriority()).isZero();
            assertThat(saved.getCompletedQty()).isEqualByComparingTo("0");
            assertThat(saved.getStockedQty()).as("入库数量不由工单写入").isNull();
            assertThat(bo.getWorkorderId()).isEqualTo(100L);
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO202611090001", "create", null, "draft", null);
        }

        @Test
        @DisplayName("原材料不能创建工单")
        void rejectsRawMaterial() {
            masterdataReady();
            when(materialMapper.selectById(1L)).thenReturn(material("raw", "0"));

            assertThatThrownBy(() -> service.insertByBo(bo())).hasMessageContaining("不是成品或半成品");
            verify(workorderMapper, never()).insert(any(MesWorkorder.class));
        }

        @Test
        @DisplayName("停用的产品不能创建工单")
        void rejectsDisabledProduct() {
            masterdataReady();
            when(materialMapper.selectById(1L)).thenReturn(material("semi", "1"));

            assertThatThrownBy(() -> service.insertByBo(bo())).hasMessageContaining("已停用");
        }

        @Test
        @DisplayName("工艺路线必须属于该产品")
        void rejectsRouteOfOtherProduct() {
            masterdataReady();
            when(routeMapper.selectById(2L)).thenReturn(route(99L));

            assertThatThrownBy(() -> service.insertByBo(bo())).hasMessageContaining("工艺路线不存在或不属于该产品");
        }

        @Test
        @DisplayName("指定的物料清单必须属于该产品")
        void rejectsBomOfOtherProduct() {
            masterdataReady();
            MesBom bom = new MesBom();
            bom.setBomId(7L);
            bom.setMaterialId(99L);
            when(bomMapper.selectById(7L)).thenReturn(bom);
            MesWorkorderBo bo = bo();
            bo.setBomId(7L);

            assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("物料清单");
        }

        @Test
        @DisplayName("新增、修改时显式选择的物料清单必须是正常状态")
        void rejectsDisabledBom() {
            masterdataReady();
            MesBom bom = new MesBom();
            bom.setBomId(7L);
            bom.setMaterialId(1L);
            bom.setBomCode("BOM-P001");
            bom.setBomVersion("V1");
            bom.setStatus("1");
            when(bomMapper.selectById(7L)).thenReturn(bom);
            MesWorkorderBo bo = bo();
            bo.setBomId(7L);

            assertThatThrownBy(() -> service.insertByBo(bo)).hasMessage("物料清单“BOM-P001 V1”已停用");
            verify(workorderMapper, never()).insert(any(MesWorkorder.class));

            givenWorkorder(workorder("draft"));
            bo.setWorkorderId(100L);
            assertThatThrownBy(() -> service.updateByBo(bo)).hasMessage("物料清单“BOM-P001 V1”已停用");
            verify(workorderMapper, never()).update(any(MesWorkorder.class), any(Wrapper.class));

            bom.setStatus("0");
            when(workorderMapper.insert(any(MesWorkorder.class))).thenReturn(1);
            bo.setWorkorderId(null);
            service.insertByBo(bo);
            verify(workorderMapper).insert(any(MesWorkorder.class));
        }

        @Test
        @DisplayName("产线必须属于所选车间")
        void rejectsLineOfOtherWorkshop() {
            masterdataReady();
            MesLine line = new MesLine();
            line.setLineId(5L);
            line.setWorkshopId(99L);
            when(lineMapper.selectById(5L)).thenReturn(line);
            MesWorkorderBo bo = bo();
            bo.setLineId(5L);

            assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("产线不存在或不属于所选车间");
        }

        @Test
        @DisplayName("计划数量必须大于 0，计划完成时间不能早于开始时间")
        void rejectsInvalidPlan() {
            MesWorkorderBo zero = bo();
            zero.setPlanQty(BigDecimal.ZERO);
            assertThatThrownBy(() -> service.insertByBo(zero)).hasMessageContaining("计划数量必须大于 0");

            MesWorkorderBo reversed = bo();
            reversed.setPlanStartTime(LocalDateTime.of(2026, 11, 10, 8, 0));
            reversed.setPlanEndTime(LocalDateTime.of(2026, 11, 9, 8, 0));
            assertThatThrownBy(() -> service.insertByBo(reversed)).hasMessageContaining("计划完成时间");

            MesWorkorderBo badSource = bo();
            badSource.setSourceType("mail");
            assertThatThrownBy(() -> service.insertByBo(badSource)).hasMessageContaining("工单来源");
        }

        @Test
        @DisplayName("只有草稿可以修改；修改时带草稿状态条件")
        void updateOnlyDraft() {
            givenWorkorder(workorder("released"));
            MesWorkorderBo bo = bo();
            bo.setWorkorderId(100L);

            assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不能修改");

            givenWorkorder(workorder("draft"));
            masterdataReady();
            when(workorderMapper.update(any(MesWorkorder.class), any(Wrapper.class))).thenReturn(1);
            service.updateByBo(bo);
            ArgumentCaptor<MesWorkorder> captor = ArgumentCaptor.forClass(MesWorkorder.class);
            verify(workorderMapper).update(captor.capture(), any(Wrapper.class));
            assertThat(captor.getValue().getFactoryId()).isEqualTo(3L);
            assertThat(captor.getValue().getWorkorderStatus()).as("修改不改状态").isNull();
            assertThat(captor.getValue().getStockedQty()).isNull();

            when(workorderMapper.update(any(MesWorkorder.class), any(Wrapper.class))).thenReturn(0);
            assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("状态已变化");
        }

        @Test
        @DisplayName("只有草稿可以删除")
        void deleteOnlyDraft() {
            when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workorder("released")));
            assertThatThrownBy(() -> service.deleteByIds(List.of(100L))).hasMessageContaining("不能删除");
            verify(workorderMapper, never()).delete(any(Wrapper.class));

            when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workorder("draft")));
            when(workorderMapper.delete(any(Wrapper.class))).thenReturn(1);
            service.deleteByIds(List.of(100L));
            verify(workorderMapper).delete(any(Wrapper.class));
        }

        @Test
        @DisplayName("删除不存在的工单时提示不存在")
        void deleteMissing() {
            assertThatThrownBy(() -> service.deleteByIds(List.of(1L))).hasMessageContaining("不存在");
        }

        @Test
        @DisplayName("删除时按数据权限查询，其中任何一个不在范围内（查不到）都整批拒绝")
        void deleteOutOfScope() {
            when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workorder("draft")));

            assertThatThrownBy(() -> service.deleteByIds(List.of(100L, 101L))).hasMessageContaining("工单不存在");

            verify(workorderMapper).selectByIdsInScope(any());
            verify(workorderMapper, never()).delete(any(Wrapper.class));
        }

        @Test
        @DisplayName("修改不在数据权限内的工单按不存在处理")
        void updateOutOfScope() {
            givenWorkorder(workorder("draft"));
            doReturn(null).when(workorderMapper).selectByIdInScope(100L);
            MesWorkorderBo bo = bo();
            bo.setWorkorderId(100L);

            assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("工单不存在");
            verify(workorderMapper, never()).update(any(), any(Wrapper.class));
        }

    }

    // ---------------------------------------------------------------- 下达

    @Nested
    @DisplayName("下达")
    class Release {

        @Test
        @DisplayName("下达时生成批次号，并按工序顺序生成待派工的工序任务")
        @SuppressWarnings("unchecked")
        void releaseCreatesTasks() {
            MesWorkorder workorder = workorder("draft");
            givenWorkorder(workorder);
            when(routeMapper.selectById(2L)).thenReturn(route(1L));
            MesRouteOperation cut = new MesRouteOperation();
            cut.setOperationId(11L);
            cut.setSeqNo(10);
            cut.setOperationCode("OP10");
            cut.setOperationName("下料");
            cut.setStandardMinutes(new BigDecimal("1.5"));
            cut.setFirstInspection("Y");
            MesRouteOperation assemble = new MesRouteOperation();
            assemble.setOperationId(12L);
            assemble.setSeqNo(20);
            assemble.setOperationCode("OP20");
            assemble.setOperationName("装配");
            when(routeOperationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(cut, assemble));
            when(codeService.next(CodeRules.BATCH)).thenReturn("B261109001");
            when(codeService.next(CodeRules.TASK, 2)).thenReturn(List.of("OT1", "OT2"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            service.release(100L);

            assertThat(capturedWorkorderUpdate().getSqlSet()).contains("workorder_status", "batch_code");
            ArgumentCaptor<Collection<MesOperationTask>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(taskMapper).insert(captor.capture());
            List<MesOperationTask> tasks = new ArrayList<>(captor.getValue());
            assertThat(tasks).extracting(MesOperationTask::getTaskCode).containsExactly("OT1", "OT2");
            assertThat(tasks).extracting(MesOperationTask::getSeqNo).containsExactly(10, 20);
            assertThat(tasks).allSatisfy(t -> {
                assertThat(t.getTaskStatus()).isEqualTo("pending");
                assertThat(t.getPlanQty()).isEqualByComparingTo("100");
                assertThat(t.getWorkshopId()).isEqualTo(4L);
                assertThat(t.getLineId()).isEqualTo(5L);
                assertThat(t.getMaterialId()).isEqualTo(1L);
                assertThat(t.getQualifiedQty()).isEqualByComparingTo("0");
            });
            assertThat(tasks.get(0).getFirstInspection()).isEqualTo("Y");
            assertThat(tasks.get(0).getStandardMinutes()).isEqualByComparingTo("1.5");
            assertThat(tasks.get(1).getFirstInspection()).isEqualTo("N");
            assertThat(tasks.get(1).getStandardMinutes()).isEqualByComparingTo("0");
            verify(statusLogService).record(eq(BizType.WORKORDER), eq(100L), eq("WO001"), eq("release"), eq("draft"),
                eq("released"), anyString());
        }

        @Test
        @DisplayName("工艺路线没有工序时拒绝下达")
        void rejectsRouteWithoutOperations() {
            givenWorkorder(workorder("draft"));
            when(routeMapper.selectById(2L)).thenReturn(route(1L));

            assertThatThrownBy(() -> service.release(100L)).hasMessageContaining("没有工序");
            verify(workorderMapper, never()).update(any(), any(Wrapper.class));
        }

        @Test
        @DisplayName("只有草稿可以下达；状态被并发改变时拒绝")
        void releaseOnlyDraft() {
            givenWorkorder(workorder("closed"));
            assertThatThrownBy(() -> service.release(100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已关闭").hasMessageContaining("不能下达");

            givenWorkorder(workorder("draft"));
            when(routeMapper.selectById(2L)).thenReturn(route(1L));
            when(routeOperationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new MesRouteOperation()));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
            assertThatThrownBy(() -> service.release(100L)).hasMessageContaining("状态已变化");
        }

    }

    // ---------------------------------------------------------------- 暂停、恢复、完工、关闭、取消

    @Nested
    @DisplayName("状态动作")
    class Actions {

        @Test
        @DisplayName("暂停必须填写原因，只能从已下达或生产中暂停，并记录原状态")
        void pause() {
            assertThatThrownBy(() -> service.pause(100L, " ")).hasMessageContaining("暂停原因");

            givenWorkorder(workorder("draft"));
            assertThatThrownBy(() -> service.pause(100L, "缺料")).hasMessageContaining("不能暂停");

            givenWorkorder(workorder("in_progress"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            service.pause(100L, "缺料");
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "pause", "in_progress", "paused", "缺料");
        }

        @Test
        @DisplayName("恢复到最近一次暂停前的状态")
        void resumeToStatusBeforePause() {
            givenWorkorder(workorder("paused"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(statusLogService.list(BizType.WORKORDER, 100L)).thenReturn(List.of(
                log("pause", "released", "paused"), log("resume", "paused", "released"),
                log("start", "released", "in_progress"), log("pause", "in_progress", "paused")));

            service.resume(100L);

            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "resume", "paused", "in_progress", null);
            verify(reportMapper, never()).exists(any(Wrapper.class));
        }

        @Test
        @DisplayName("取不到暂停记录时：有报工恢复为生产中，否则恢复为已下达")
        void resumeFallback() {
            givenWorkorder(workorder("paused"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(reportMapper.exists(any(Wrapper.class))).thenReturn(true);
            service.resume(100L);
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "resume", "paused", "in_progress", null);

            when(reportMapper.exists(any(Wrapper.class))).thenReturn(false);
            service.resume(100L);
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "resume", "paused", "released", null);
        }

        @Test
        @DisplayName("只有已暂停的工单可以恢复")
        void resumeOnlyPaused() {
            givenWorkorder(workorder("released"));
            assertThatThrownBy(() -> service.resume(100L)).hasMessageContaining("不能恢复");
        }

        @Test
        @DisplayName("完工：只有生产中可以完工，写实际完成时间，未完成的工序任务一并完成")
        void complete() {
            givenWorkorder(workorder("released"));
            assertThatThrownBy(() -> service.complete(100L)).hasMessageContaining("不能完工");

            givenWorkorder(workorder("in_progress"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(task(1L, 10, "in_progress"), task(2L, 20, "dispatched")));
            service.complete(100L);

            assertThat(capturedWorkorderUpdate().getSqlSet()).contains("actual_end_time");
            ArgumentCaptor<Wrapper<MesOperationTask>> taskUpdate = ArgumentCaptor.forClass(Wrapper.class);
            verify(taskMapper).update(isNull(), taskUpdate.capture());
            assertThat(((LambdaUpdateWrapper<MesOperationTask>) taskUpdate.getValue()).getSqlSet())
                .contains("task_status", "actual_end_time");
            verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "complete", "in_progress", "completed",
                "工单完工");
            verify(statusLogService).record(BizType.OPERATION_TASK, 2L, "OT2", "complete", "dispatched", "completed",
                "工单完工");
            verify(snService).completeByWorkorder(100L);
        }

        @Test
        @DisplayName("全部工序完成后自动完工：不要求完工数量达到计划数量（有不良时少于计划数），已生成的序列号改为已完工")
        void completeIfFinished() {
            MesWorkorder workorder = workorder("in_progress");
            workorder.setCompletedQty(new BigDecimal("8"));
            givenWorkorder(workorder);
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(task(1L, 10, "completed"), task(2L, 20, "completed")));

            assertThat(service.completeIfFinished(100L)).isTrue();

            LambdaUpdateWrapper<MesWorkorder> update = capturedWorkorderUpdate();
            assertThat(update.getSqlSet()).contains("workorder_status", "actual_end_time");
            assertThat(update.getSqlSegment()).contains("workorder_status =").doesNotContain("completed_qty");
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "auto_complete", "in_progress",
                "completed", null);
            verify(taskMapper, never()).update(any(), any(Wrapper.class));
            verify(snService).completeByWorkorder(100L);
        }

        @Test
        @DisplayName("还有未完成的工序、工单不在生产中或状态已被并发改变时不完工，工单保持原状、不记履历")
        void completeIfFinishedNotReached() {
            givenWorkorder(workorder("in_progress"));
            when(taskMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(task(1L, 10, "completed"), task(2L, 20, "in_progress")));
            assertThat(service.completeIfFinished(100L)).as("第 2 道还在生产中").isFalse();
            verify(workorderMapper, never()).update(any(), any(Wrapper.class));

            givenWorkorder(workorder("paused"));
            when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task(1L, 10, "completed")));
            assertThat(service.completeIfFinished(100L)).as("工单已暂停").isFalse();
            verify(workorderMapper, never()).update(any(), any(Wrapper.class));

            givenWorkorder(workorder("in_progress"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
            assertThat(service.completeIfFinished(100L)).as("状态已被并发改变").isFalse();

            verify(statusLogService, never()).record(any(), anyLong(), anyString(), anyString(), any(), any(), any());
            verify(taskMapper, never()).update(any(), any(Wrapper.class));
            verify(snService, never()).completeByWorkorder(anyLong());
            assertThat(service.completeIfFinished(999L)).as("工单不存在").isFalse();
        }

        @Test
        @DisplayName("自动完工的加锁顺序：工单 → 工序任务")
        void completeIfFinishedLockOrder() {
            givenWorkorder(workorder("in_progress"));
            when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task(1L, 10, "completed")));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            service.completeIfFinished(100L);

            InOrder order = inOrder(workorderMapper, taskMapper, snService);
            order.verify(workorderMapper).selectByIdForUpdate(100L);
            order.verify(taskMapper).selectByWorkorderForUpdate(100L);
            order.verify(workorderMapper).update(isNull(), any(Wrapper.class));
            order.verify(snService).completeByWorkorder(100L);
        }

        @Test
        @DisplayName("状态动作先锁定工单行，再校验数据权限；不在范围内按不存在处理")
        void actionsLockAndCheckScope() {
            MesWorkorder workorder = workorder("in_progress");
            givenWorkorder(workorder);
            doReturn(null).when(workorderMapper).selectByIdInScope(100L);

            assertThatThrownBy(() -> service.pause(100L, "缺料")).hasMessageContaining("工单不存在");
            assertThatThrownBy(() -> service.complete(100L)).hasMessageContaining("工单不存在");
            assertThatThrownBy(() -> service.release(100L)).hasMessageContaining("工单不存在");
            assertThatThrownBy(() -> service.queryById(100L)).hasMessageContaining("工单不存在");
            assertThatThrownBy(() -> service.logs(100L)).hasMessageContaining("工单不存在");
            verify(workorderMapper, never()).update(any(), any(Wrapper.class));
            verify(statusLogService, never()).list(any(), anyLong());

            doReturn(workorder).when(workorderMapper).selectByIdInScope(100L);
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            clearInvocations(workorderMapper);
            service.pause(100L, "缺料");
            InOrder order = inOrder(workorderMapper);
            order.verify(workorderMapper).selectByIdForUpdate(100L);
            order.verify(workorderMapper).selectByIdInScope(100L);
            order.verify(workorderMapper).update(isNull(), any(Wrapper.class));
        }

        @Test
        @DisplayName("关闭：只有已完工可以关闭，工序任务全部关闭")
        void close() {
            givenWorkorder(workorder("in_progress"));
            assertThatThrownBy(() -> service.close(100L, null)).hasMessageContaining("不能关闭");

            givenWorkorder(workorder("completed"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task(1L, 10, "completed")));
            service.close(100L, null);
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "close", "completed", "closed", null);
            verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "close", "completed", "closed", "工单关闭");
        }

        @Test
        @DisplayName("取消：必须填写原因；已有报工的工单不能取消；取消后工序任务全部关闭")
        void cancel() {
            assertThatThrownBy(() -> service.cancel(100L, null)).hasMessageContaining("取消原因");

            givenWorkorder(workorder("in_progress"));
            assertThatThrownBy(() -> service.cancel(100L, "订单取消")).hasMessageContaining("不能取消");

            givenWorkorder(workorder("released"));
            when(reportMapper.exists(any(Wrapper.class))).thenReturn(true);
            assertThatThrownBy(() -> service.cancel(100L, "订单取消")).hasMessageContaining("已有报工");

            when(reportMapper.exists(any(Wrapper.class))).thenReturn(false);
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
            when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task(1L, 10, "pending")));
            service.cancel(100L, "订单取消");
            verify(statusLogService).record(BizType.WORKORDER, 100L, "WO001", "cancel", "released", "cancelled",
                "订单取消");
            verify(statusLogService).record(BizType.OPERATION_TASK, 1L, "OT1", "close", "pending", "closed", "工单取消");
        }

        @Test
        @DisplayName("草稿取消时没有工序任务，不更新任务")
        void cancelDraft() {
            givenWorkorder(workorder("draft"));
            when(workorderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

            service.cancel(100L, "录错");

            verify(taskMapper, never()).update(any(), any(Wrapper.class));
        }

        @Test
        @DisplayName("工单不存在时提示不存在")
        void missing() {
            assertThatThrownBy(() -> service.release(1L)).hasMessageContaining("工单不存在");
            assertThatThrownBy(() -> service.queryById(null)).hasMessageContaining("工单不存在");
        }

    }

    // ---------------------------------------------------------------- 查询

    @Nested
    @DisplayName("查询")
    class Queries {

        @Test
        @DisplayName("列表批量填充产品、车间、产线、工艺路线名称与完成率")
        @SuppressWarnings("unchecked")
        void pageFillsNames() {
            MesWorkorder workorder = workorder("in_progress");
            workorder.setCompletedQty(new BigDecimal("45.5"));
            workorder.setBomId(7L);
            when(workorderMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(workorder));
            when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material("product", "0")));
            when(workshopMapper.selectByIds(anyCollection())).thenReturn(List.of(workshop()));
            MesLine line = new MesLine();
            line.setLineId(5L);
            line.setLineName("一线");
            when(lineMapper.selectByIds(anyCollection())).thenReturn(List.of(line));
            MesFactory factory = new MesFactory();
            factory.setFactoryId(3L);
            factory.setFactoryName("一厂");
            when(factoryMapper.selectByIds(anyCollection())).thenReturn(List.of(factory));
            MesBom bom = new MesBom();
            bom.setBomId(7L);
            bom.setBomCode("BOM-P001");
            bom.setBomVersion("V2");
            when(bomMapper.selectByIds(anyCollection())).thenReturn(List.of(bom));
            when(routeMapper.selectByIds(anyCollection())).thenReturn(List.of(route(1L)));
            MesWorkorderBo query = new MesWorkorderBo();
            query.setWorkorderStatus("released,in_progress");

            PageResult<MesWorkorderVo> page = service.queryPageList(query, new PageParam(1, 10));

            verify(workorderMapper).selectPageInScope(any(), any(Wrapper.class));
            MesWorkorderVo vo = page.getItems().get(0);
            assertThat(vo.getMaterialName()).isEqualTo("控制器");
            assertThat(vo.getUnit()).isEqualTo("台");
            assertThat(vo.getWorkshopName()).isEqualTo("总装车间");
            assertThat(vo.getLineName()).isEqualTo("一线");
            assertThat(vo.getFactoryName()).isEqualTo("一厂");
            assertThat(vo.getBomName()).isEqualTo("BOM-P001 V2");
            assertThat(vo.getRouteName()).isEqualTo("控制器装配 V1");
            assertThat(vo.getCompletionRate()).isEqualByComparingTo("45.50");
        }

        @Test
        @DisplayName("详情带工序任务，并算出每道工序还能报工的数量")
        void detailWithTasks() {
            MesWorkorder workorder = workorder("in_progress");
            givenWorkorder(workorder);
            when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder));
            MesOperationTask first = task(1L, 10, "in_progress");
            first.setQualifiedQty(new BigDecimal("60"));
            first.setDefectQty(new BigDecimal("5"));
            MesOperationTask second = task(2L, 20, "in_progress");
            second.setQualifiedQty(new BigDecimal("40"));
            second.setDefectQty(BigDecimal.ZERO);
            when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));

            MesWorkorderVo vo = service.queryById(100L);

            assertThat(vo.getTasks()).hasSize(2);
            assertThat(vo.getTasks().get(0).getAvailableQty()).isEqualByComparingTo("35");
            assertThat(vo.getTasks().get(0).getLastOperation()).isFalse();
            assertThat(vo.getTasks().get(1).getAvailableQty()).isEqualByComparingTo("20");
            assertThat(vo.getTasks().get(1).getLastOperation()).isTrue();
            assertThat(vo.getTasks().get(1).getWorkorderCode()).isEqualTo("WO001");
        }

        @Test
        @DisplayName("工单选项：名称为产品名称，上级为产品")
        void options() {
            when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workorder("released")));
            when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material("product", "0")));

            List<MesOptionVo> options = service.options("WO", "released,in_progress");

            verify(workorderMapper).selectListInScope(any(Wrapper.class));
            assertThat(options).singleElement().satisfies(option -> {
                assertThat(option.getId()).isEqualTo(100L);
                assertThat(option.getCode()).isEqualTo("WO001");
                assertThat(option.getName()).isEqualTo("控制器");
                assertThat(option.getParentId()).isEqualTo(1L);
            });
        }

        @Test
        @DisplayName("导出前统计与导出列表")
        void exportQueries() {
            when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workorder("draft")));
            givenWorkorder(workorder("draft"));

            assertThat(service.queryList(new MesWorkorderBo())).hasSize(1);
            assertThat(service.countList(new MesWorkorderBo())).isZero();
            service.logs(100L);
            verify(statusLogService, times(1)).list(BizType.WORKORDER, 100L);
            verify(workorderMapper).selectListInScope(any(Wrapper.class));
            verify(workorderMapper).selectPageInScope(any(), any(Wrapper.class));
        }

        @Test
        @DisplayName("完成率保留两位小数，计划数为 0 时为 0")
        void completionRate() {
            assertThat(MesWorkorderServiceImpl.completionRate(new BigDecimal("1"), new BigDecimal("3")))
                .isEqualByComparingTo("33.33");
            assertThat(MesWorkorderServiceImpl.completionRate(null, new BigDecimal("3"))).isEqualByComparingTo("0");
            assertThat(MesWorkorderServiceImpl.completionRate(BigDecimal.ONE, BigDecimal.ZERO)).isEqualByComparingTo("0");
        }

    }

}
