package org.tuoyu.mes.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesBomItem;
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.material.domain.MesMaterialRequest;
import org.tuoyu.mes.material.domain.MesMaterialRequestItem;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestCompleteBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestGenerateBo;
import org.tuoyu.mes.material.domain.bo.MesMaterialRequestItemBo;
import org.tuoyu.mes.material.domain.vo.MesMaterialRequestVo;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("领退料单：按 BOM 生成、状态流转与退料上限")
class MesMaterialRequestServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesMaterialRequestMapper requestMapper = MesTestSupport.mapper(MesMaterialRequestMapper.class);
    private final MesMaterialRequestItemMapper itemMapper = MesTestSupport.mapper(MesMaterialRequestItemMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesWarehouseMapper warehouseMapper = MesTestSupport.mapper(MesWarehouseMapper.class);
    private final MesLocationMapper locationMapper = MesTestSupport.mapper(MesLocationMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesBomMapper bomMapper = MesTestSupport.mapper(MesBomMapper.class);
    private final MesBomItemMapper bomItemMapper = MesTestSupport.mapper(MesBomItemMapper.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);

    private final MesMaterialRequestServiceImpl service = new MesMaterialRequestServiceImpl(requestMapper, itemMapper,
        workorderMapper, warehouseMapper, locationMapper, materialMapper, bomMapper, bomItemMapper, codeService,
        statusLogService);

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        previous = MesTestSupport.loginAs(7L);
        when(codeService.next(anyString())).thenAnswer(invocation -> invocation.getArgument(0) + "-0001");
        when(requestMapper.insert(any(MesMaterialRequest.class))).thenAnswer(invocation -> {
            invocation.<MesMaterialRequest>getArgument(0).setRequestId(100L);
            return 1;
        });
        when(warehouseMapper.selectById(1L)).thenReturn(warehouse(1L, "0"));
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.restore(previous);
    }

    private static MesWorkorder workorder(String status) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(10L);
        workorder.setWorkorderCode("WO001");
        workorder.setMaterialId(50L);
        workorder.setPlanQty(new BigDecimal("100"));
        workorder.setWorkorderStatus(status);
        return workorder;
    }

    private static MesWarehouse warehouse(Long id, String status) {
        MesWarehouse warehouse = new MesWarehouse();
        warehouse.setWarehouseId(id);
        warehouse.setWarehouseName("原料仓");
        warehouse.setStatus(status);
        return warehouse;
    }

    private static MesMaterial material(Long id, String status) {
        MesMaterial material = new MesMaterial();
        material.setMaterialId(id);
        material.setMaterialCode("M" + id);
        material.setMaterialName("物料" + id);
        material.setUnit("个");
        material.setStatus(status);
        return material;
    }

    private static MesMaterialRequest request(String type, String status) {
        MesMaterialRequest request = new MesMaterialRequest();
        request.setRequestId(100L);
        request.setRequestCode("LL001");
        request.setRequestType(type);
        request.setWorkorderId(10L);
        request.setWarehouseId(1L);
        request.setRequestStatus(status);
        return request;
    }

    private static MesMaterialRequestItem item(Long itemId, Long requestId, Long materialId, String actual) {
        MesMaterialRequestItem item = new MesMaterialRequestItem();
        item.setItemId(itemId);
        item.setRequestId(requestId);
        item.setMaterialId(materialId);
        item.setRequestQty(new BigDecimal("10"));
        item.setActualQty(new BigDecimal(actual));
        return item;
    }

    private static MesMaterialRequestItemBo itemBo(Long materialId, String qty) {
        MesMaterialRequestItemBo bo = new MesMaterialRequestItemBo();
        bo.setMaterialId(materialId);
        bo.setRequestQty(new BigDecimal(qty));
        return bo;
    }

    private static MesMaterialRequestBo requestBo(String type, MesMaterialRequestItemBo... items) {
        MesMaterialRequestBo bo = new MesMaterialRequestBo();
        bo.setRequestType(type);
        bo.setWorkorderId(10L);
        bo.setWarehouseId(1L);
        bo.setItems(new ArrayList<>(List.of(items)));
        return bo;
    }

    private static MesMaterialRequestCompleteBo completeBo(Long itemId, String actual) {
        MesMaterialRequestCompleteBo.Item input = new MesMaterialRequestCompleteBo.Item();
        input.setItemId(itemId);
        input.setActualQty(new BigDecimal(actual));
        MesMaterialRequestCompleteBo bo = new MesMaterialRequestCompleteBo();
        bo.setItems(List.of(input));
        return bo;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Collection<MesMaterialRequestItem>> itemsCaptor() {
        return ArgumentCaptor.forClass(Collection.class);
    }

    @Test
    @DisplayName("BOM 需求 = 用量 × 计划数 ÷ 基准数量 × (1 + 损耗率)，保留 6 位小数")
    void bomRequirementFormula() {
        assertThat(MesMaterialRequestServiceImpl.bomRequirement(new BigDecimal("2"), new BigDecimal("100"),
            new BigDecimal("10"), new BigDecimal("0.05"))).isEqualByComparingTo("21");
        assertThat(MesMaterialRequestServiceImpl.bomRequirement(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("3"), null))
            .isEqualTo(new BigDecimal("0.333333"));
        // 基准数量缺失或不大于 0 时按 1 计算
        assertThat(MesMaterialRequestServiceImpl.bomRequirement(new BigDecimal("1.5"), new BigDecimal("4"), null, BigDecimal.ZERO))
            .isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("按工单的 BOM 生成领料草稿：需求数同时写入计划数与申请数，相同子件合并")
    void generateByBom() {
        MesWorkorder workorder = workorder("released");
        workorder.setBomId(20L);
        when(workorderMapper.selectById(10L)).thenReturn(workorder);
        MesBom bom = new MesBom();
        bom.setBomId(20L);
        bom.setBomCode("BOM01");
        bom.setBaseQty(new BigDecimal("10"));
        when(bomMapper.selectById(20L)).thenReturn(bom);
        MesBomItem a = new MesBomItem();
        a.setMaterialId(60L);
        a.setQty(new BigDecimal("2"));
        a.setLossRate(new BigDecimal("0.05"));
        MesBomItem b = new MesBomItem();
        b.setMaterialId(61L);
        b.setQty(new BigDecimal("1"));
        b.setLossRate(BigDecimal.ZERO);
        MesBomItem c = new MesBomItem();
        c.setMaterialId(61L);
        c.setQty(new BigDecimal("0.5"));
        c.setLossRate(BigDecimal.ZERO);
        when(bomItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(a, b, c));
        MesMaterialRequestGenerateBo bo = new MesMaterialRequestGenerateBo();
        bo.setWorkorderId(10L);
        bo.setWarehouseId(1L);

        Long id = service.generateByBom(bo);

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<MesMaterialRequest> header = ArgumentCaptor.forClass(MesMaterialRequest.class);
        verify(requestMapper).insert(header.capture());
        assertThat(header.getValue().getRequestType()).isEqualTo("issue");
        assertThat(header.getValue().getRequestStatus()).isEqualTo("draft");
        assertThat(header.getValue().getRequestCode()).isEqualTo(CodeRules.MATERIAL_ISSUE + "-0001");
        ArgumentCaptor<Collection<MesMaterialRequestItem>> items = itemsCaptor();
        verify(itemMapper).insert(items.capture());
        List<MesMaterialRequestItem> saved = new ArrayList<>(items.getValue());
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getMaterialId()).isEqualTo(60L);
        assertThat(saved.get(0).getPlanQty()).isEqualByComparingTo("21");
        assertThat(saved.get(0).getRequestQty()).isEqualByComparingTo("21");
        assertThat(saved.get(1).getMaterialId()).isEqualTo(61L);
        assertThat(saved.get(1).getRequestQty()).isEqualByComparingTo("15");
        assertThat(saved).allSatisfy(item -> assertThat(item.getRequestId()).isEqualTo(100L));
        verify(statusLogService).record(eq(BizType.MATERIAL_REQUEST), eq(100L), anyString(), eq("create"), isNull(),
            eq("draft"), anyString());
    }

    @Test
    @DisplayName("工单没有指定 BOM 时取产品的默认 BOM，没有默认 BOM 时拒绝生成")
    void generateWithoutBom() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("in_progress"));
        when(bomMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        MesMaterialRequestGenerateBo bo = new MesMaterialRequestGenerateBo();
        bo.setWorkorderId(10L);
        bo.setWarehouseId(1L);

        assertThatThrownBy(() -> service.generateByBom(bo)).isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有物料清单");
        verify(requestMapper, never()).insert(any(MesMaterialRequest.class));
    }

    @Test
    @DisplayName("草稿工单不能领料")
    void generateRejectsDraftWorkorder() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("draft"));
        MesMaterialRequestGenerateBo bo = new MesMaterialRequestGenerateBo();
        bo.setWorkorderId(10L);
        bo.setWarehouseId(1L);

        assertThatThrownBy(() -> service.generateByBom(bo)).hasMessageContaining("不能领料");
    }

    @Test
    @DisplayName("新增领料单：生成单号、保存明细并记录履历")
    void insertIssue() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("released"));
        when(materialMapper.selectByIds(any())).thenReturn(List.of(material(60L, "0"), material(61L, "0")));
        MesMaterialRequestBo bo = requestBo("supplement", itemBo(60L, "5"), itemBo(61L, "2.5"));

        service.insertByBo(bo);

        assertThat(bo.getRequestId()).isEqualTo(100L);
        verify(codeService).next(CodeRules.MATERIAL_SUPPLEMENT);
        ArgumentCaptor<Collection<MesMaterialRequestItem>> items = itemsCaptor();
        verify(itemMapper).insert(items.capture());
        assertThat(items.getValue()).hasSize(2).allSatisfy(item -> {
            assertThat(item.getActualQty()).isEqualByComparingTo("0");
            assertThat(item.getPlanQty()).isNull();
        });
    }

    @Test
    @DisplayName("已完工的工单只能退料，不能领料")
    void completedWorkorderAllowsOnlyReturn() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("completed"));
        when(materialMapper.selectByIds(any())).thenReturn(List.of(material(60L, "1")));

        assertThatThrownBy(() -> service.insertByBo(requestBo("issue", itemBo(60L, "1"))))
            .hasMessageContaining("不能领料");
        // 退料允许已完工的工单，也允许已停用的物料
        service.insertByBo(requestBo("return", itemBo(60L, "1")));
        verify(codeService).next(CodeRules.MATERIAL_RETURN);
    }

    @Test
    @DisplayName("明细物料重复、仓库停用、库位不属于仓库时拒绝")
    void rejectsInvalidItems() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("in_progress"));
        when(materialMapper.selectByIds(any())).thenReturn(List.of(material(60L, "0")));

        assertThatThrownBy(() -> service.insertByBo(requestBo("issue", itemBo(60L, "1"), itemBo(60L, "2"))))
            .hasMessageContaining("重复");

        when(warehouseMapper.selectById(2L)).thenReturn(warehouse(2L, "1"));
        MesMaterialRequestBo disabled = requestBo("issue", itemBo(60L, "1"));
        disabled.setWarehouseId(2L);
        assertThatThrownBy(() -> service.insertByBo(disabled)).hasMessageContaining("已停用");

        MesLocation location = new MesLocation();
        location.setLocationId(9L);
        location.setWarehouseId(3L);
        location.setLocationName("A-01");
        when(locationMapper.selectByIds(any())).thenReturn(List.of(location));
        MesMaterialRequestItemBo withLocation = itemBo(60L, "1");
        withLocation.setLocationId(9L);
        assertThatThrownBy(() -> service.insertByBo(requestBo("issue", withLocation))).hasMessageContaining("不属于仓库");
        verify(requestMapper, never()).insert(any(MesMaterialRequest.class));
    }

    @Test
    @DisplayName("修改草稿：整体替换明细并保留按 BOM 计算的需求数；非草稿不能修改")
    void updateDraftOnly() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("released"));
        when(materialMapper.selectByIds(any())).thenReturn(List.of(material(60L, "0")));
        when(requestMapper.selectById(100L)).thenReturn(request("issue", "draft"));
        when(requestMapper.update(any(), any())).thenReturn(1);
        MesMaterialRequestItem old = item(1L, 100L, 60L, "0");
        old.setPlanQty(new BigDecimal("21"));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(old));
        MesMaterialRequestBo bo = requestBo(null, itemBo(60L, "25"));
        bo.setRequestId(100L);

        service.updateByBo(bo);

        ArgumentCaptor<Collection<MesMaterialRequestItem>> items = itemsCaptor();
        verify(itemMapper).insert(items.capture());
        MesMaterialRequestItem saved = items.getValue().iterator().next();
        assertThat(saved.getPlanQty()).isEqualByComparingTo("21");
        assertThat(saved.getRequestQty()).isEqualByComparingTo("25");

        when(requestMapper.selectById(100L)).thenReturn(request("issue", "submitted"));
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不能修改");

        when(requestMapper.selectById(100L)).thenReturn(request("issue", "draft"));
        bo.setRequestType("return");
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("单据类型不能修改");
    }

    @Test
    @DisplayName("提交：草稿 → 已提交，记录履历；状态已变化时提示刷新")
    void submit() {
        when(requestMapper.selectById(100L)).thenReturn(request("issue", "draft"));
        when(requestMapper.update(any(), any())).thenReturn(1);

        service.submit(100L);

        ArgumentCaptor<MesMaterialRequest> change = ArgumentCaptor.forClass(MesMaterialRequest.class);
        verify(requestMapper).update(change.capture(), any());
        assertThat(change.getValue().getRequestStatus()).isEqualTo("submitted");
        assertThat(change.getValue().getSubmitTime()).isNotNull();
        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "LL001", "submit", "draft", "submitted", null);

        when(requestMapper.update(any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.submit(100L)).hasMessageContaining("状态已变化");
    }

    @Test
    @DisplayName("完成领料：写入实发数、执行人与完成时间")
    void completeIssue() {
        when(requestMapper.selectById(100L)).thenReturn(request("issue", "submitted"));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(1L, 100L, 60L, "0")));
        when(requestMapper.update(any(), any())).thenReturn(1);

        service.complete(100L, completeBo(1L, "9.5"));

        ArgumentCaptor<MesMaterialRequest> change = ArgumentCaptor.forClass(MesMaterialRequest.class);
        verify(requestMapper).update(change.capture(), any());
        assertThat(change.getValue().getRequestStatus()).isEqualTo("completed");
        assertThat(change.getValue().getOperatorUserId()).isEqualTo(7L);
        assertThat(change.getValue().getCompleteTime()).isNotNull();
        ArgumentCaptor<Collection<MesMaterialRequestItem>> items = itemsCaptor();
        verify(itemMapper).updateById(items.capture());
        assertThat(items.getValue().iterator().next().getActualQty()).isEqualByComparingTo("9.5");
    }

    @Test
    @DisplayName("完成时必须填写全部明细，且明细必须属于该单据")
    void completeRequiresAllItems() {
        when(requestMapper.selectById(100L)).thenReturn(request("issue", "submitted"));
        when(itemMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(item(1L, 100L, 60L, "0"), item(2L, 100L, 61L, "0")));

        assertThatThrownBy(() -> service.complete(100L, completeBo(1L, "1"))).hasMessageContaining("全部明细");
        assertThatThrownBy(() -> service.complete(100L, completeBo(3L, "1"))).hasMessageContaining("不属于");
    }

    @Test
    @DisplayName("退料不能超过该工单该物料已完成的（领料 + 补料 − 退料）实发数")
    void returnLimit() {
        when(requestMapper.selectById(100L)).thenReturn(request("return", "submitted"));
        // 本单退 60 号物料
        when(itemMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(item(1L, 100L, 60L, "0")))
            // 已完成的单据明细：领料 10、补料 3、退料 4，净实发 9
            .thenReturn(List.of(item(11L, 201L, 60L, "10"), item(12L, 202L, 60L, "3"), item(13L, 203L, 60L, "4")));
        when(requestMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            request(201L, "issue"), request(202L, "supplement"), request(203L, "return")));
        when(materialMapper.selectByIds(any())).thenReturn(List.of(material(60L, "0")));

        assertThatThrownBy(() -> service.complete(100L, completeBo(1L, "9.5")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("最多还能退 9");
        verify(requestMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("退料数在可退范围内时完成")
    void returnWithinLimit() {
        when(requestMapper.selectById(100L)).thenReturn(request("return", "submitted"));
        when(itemMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(item(1L, 100L, 60L, "0")))
            .thenReturn(List.of(item(11L, 201L, 60L, "10")));
        when(requestMapper.selectList(any(Wrapper.class))).thenReturn(List.of(request(201L, "issue")));
        when(requestMapper.update(any(), any())).thenReturn(1);

        service.complete(100L, completeBo(1L, "10"));

        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "LL001", "complete", "submitted", "completed", null);
    }

    private static MesMaterialRequest request(Long id, String type) {
        MesMaterialRequest request = request(type, "completed");
        request.setRequestId(id);
        return request;
    }

    @Test
    @DisplayName("取消：草稿与已提交可以取消，已完成不能取消")
    void cancel() {
        when(requestMapper.selectById(100L)).thenReturn(request("issue", "submitted"));
        when(requestMapper.update(any(), any())).thenReturn(1);

        service.cancel(100L);
        verify(statusLogService).record(BizType.MATERIAL_REQUEST, 100L, "LL001", "cancel", "submitted", "cancelled", null);

        when(requestMapper.selectById(100L)).thenReturn(request("issue", "completed"));
        assertThatThrownBy(() -> service.cancel(100L)).hasMessageContaining("不能取消");
    }

    @Test
    @DisplayName("只能删除草稿")
    void deleteDraftOnly() {
        when(requestMapper.selectByIds(any())).thenReturn(List.of(request("issue", "submitted")));
        assertThatThrownBy(() -> service.deleteByIds(List.of(100L))).hasMessageContaining("不能删除");

        when(requestMapper.selectByIds(any())).thenReturn(List.of(request("issue", "draft")));
        when(requestMapper.delete(any(Wrapper.class))).thenReturn(1);
        service.deleteByIds(List.of(100L));
        verify(itemMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("详情带工单编号、仓库名称与明细的物料信息")
    void queryDetail() {
        when(requestMapper.selectById(100L)).thenReturn(request("issue", "draft"));
        when(workorderMapper.selectByIds(any())).thenReturn(List.of(workorder("released")));
        when(warehouseMapper.selectByIds(any())).thenReturn(List.of(warehouse(1L, "0")));
        when(materialMapper.selectByIds(any())).thenReturn(List.of(material(50L, "0"), material(60L, "0")));
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(item(1L, 100L, 60L, "0")));

        MesMaterialRequestVo vo = service.queryById(100L);

        assertThat(vo.getWorkorderCode()).isEqualTo("WO001");
        assertThat(vo.getProductName()).isEqualTo("物料50");
        assertThat(vo.getWarehouseName()).isEqualTo("原料仓");
        assertThat(vo.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMaterialCode()).isEqualTo("M60");
            assertThat(item.getUnit()).isEqualTo("个");
        });
        assertThatThrownBy(() -> service.logs(999L)).hasMessageContaining("不存在");
    }

}
