package org.tuoyu.mes.material.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.material.domain.MesStockIn;
import org.tuoyu.mes.material.domain.bo.MesStockInBo;
import org.tuoyu.mes.material.domain.vo.MesStockInVo;
import org.tuoyu.mes.material.mapper.MesStockInMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("完工入库：入库上限、成品仓校验与已入库数量累加")
class MesStockInServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesStockInMapper stockInMapper = MesTestSupport.mapper(MesStockInMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesWarehouseMapper warehouseMapper = MesTestSupport.mapper(MesWarehouseMapper.class);
    private final MesLocationMapper locationMapper = MesTestSupport.mapper(MesLocationMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);

    private final MesStockInServiceImpl service = new MesStockInServiceImpl(stockInMapper, workorderMapper,
        warehouseMapper, locationMapper, materialMapper, codeService, statusLogService);

    private UserContextProvider previous;

    @BeforeEach
    void setUp() {
        previous = MesTestSupport.loginAs(7L);
        when(codeService.next(CodeRules.STOCK_IN)).thenReturn("RK001");
        when(stockInMapper.insert(any(MesStockIn.class))).thenAnswer(invocation -> {
            invocation.<MesStockIn>getArgument(0).setStockInId(300L);
            return 1;
        });
        when(warehouseMapper.selectById(1L)).thenReturn(warehouse(1L, "finished"));
    }

    @AfterEach
    void tearDown() {
        MesTestSupport.restore(previous);
    }

    private static MesWorkorder workorder(String status, String completed) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(10L);
        workorder.setWorkorderCode("WO001");
        workorder.setMaterialId(50L);
        workorder.setBatchCode("B261109001");
        workorder.setPlanQty(new BigDecimal("100"));
        workorder.setCompletedQty(new BigDecimal(completed));
        workorder.setWorkorderStatus(status);
        return workorder;
    }

    private static MesWarehouse warehouse(Long id, String type) {
        MesWarehouse warehouse = new MesWarehouse();
        warehouse.setWarehouseId(id);
        warehouse.setWarehouseName("成品仓");
        warehouse.setWarehouseType(type);
        warehouse.setStatus("0");
        return warehouse;
    }

    private static MesStockInBo bo(String qty) {
        MesStockInBo bo = new MesStockInBo();
        bo.setWorkorderId(10L);
        bo.setWarehouseId(1L);
        bo.setQty(new BigDecimal(qty));
        return bo;
    }

    private static MesStockIn stockIn(String status, String qty) {
        MesStockIn stockIn = new MesStockIn();
        stockIn.setStockInId(300L);
        stockIn.setStockInCode("RK001");
        stockIn.setWorkorderId(10L);
        stockIn.setMaterialId(50L);
        stockIn.setWarehouseId(1L);
        stockIn.setQty(new BigDecimal(qty));
        stockIn.setStockInStatus(status);
        return stockIn;
    }

    @Test
    @DisplayName("新增入库单：产品与批次号取自工单，状态为已提交")
    void insert() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("in_progress", "50"));
        when(stockInMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stockIn("submitted", "20")));

        service.insertByBo(bo("30"));

        ArgumentCaptor<MesStockIn> captor = ArgumentCaptor.forClass(MesStockIn.class);
        verify(stockInMapper).insert(captor.capture());
        MesStockIn saved = captor.getValue();
        assertThat(saved.getStockInCode()).isEqualTo("RK001");
        assertThat(saved.getMaterialId()).isEqualTo(50L);
        assertThat(saved.getBatchCode()).isEqualTo("B261109001");
        assertThat(saved.getStockInStatus()).isEqualTo("submitted");
        verify(statusLogService).record(BizType.STOCK_IN, 300L, "RK001", "create", null, "submitted", null);
    }

    @Test
    @DisplayName("已提交与已入库的合计加本次数量不能超过工单完工数")
    void rejectsOverCompleted() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("completed", "50"));
        when(stockInMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(stockIn("submitted", "20"), stockIn("completed", "25")));

        assertThatThrownBy(() -> service.insertByBo(bo("5.5"))).hasMessageContaining("最多还能入库 5");
        verify(stockInMapper, never()).insert(any(MesStockIn.class));
    }

    @Test
    @DisplayName("只有生产中、已完工的工单可以入库，只能入成品仓")
    void rejectsWrongStateOrWarehouse() {
        when(workorderMapper.selectById(10L)).thenReturn(workorder("released", "50"));
        assertThatThrownBy(() -> service.insertByBo(bo("1"))).hasMessageContaining("不能入库");

        when(workorderMapper.selectById(10L)).thenReturn(workorder("in_progress", "50"));
        when(warehouseMapper.selectById(1L)).thenReturn(warehouse(1L, "raw"));
        assertThatThrownBy(() -> service.insertByBo(bo("1"))).hasMessageContaining("只能入成品仓");
    }

    @Test
    @DisplayName("入库完成：状态变为已入库，并按条件累加工单的已入库数量")
    void complete() {
        when(stockInMapper.selectById(300L)).thenReturn(stockIn("submitted", "30"));
        when(stockInMapper.update(any(), any())).thenReturn(1);
        when(workorderMapper.update(isNull(), any())).thenReturn(1);

        service.complete(300L);

        ArgumentCaptor<MesStockIn> change = ArgumentCaptor.forClass(MesStockIn.class);
        verify(stockInMapper).update(change.capture(), any());
        assertThat(change.getValue().getStockInStatus()).isEqualTo("completed");
        assertThat(change.getValue().getOperatorUserId()).isEqualTo(7L);
        assertThat(change.getValue().getCompleteTime()).isNotNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<MesWorkorder>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(workorderMapper).update(isNull(), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSet()).contains("stocked_qty = stocked_qty +");
        assertThat(wrapper.getValue().getSqlSegment()).contains("completed_qty");
        verify(statusLogService).record(BizType.STOCK_IN, 300L, "RK001", "complete", "submitted", "completed", null);
    }

    @Test
    @DisplayName("累加后超过完工数量时拒绝完成")
    void completeRejectsWhenExceedsCompleted() {
        when(stockInMapper.selectById(300L)).thenReturn(stockIn("submitted", "30"));
        when(stockInMapper.update(any(), any())).thenReturn(1);
        when(workorderMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.complete(300L)).hasMessageContaining("超过完工数量");
    }

    @Test
    @DisplayName("已入库的单据不能再次入库或取消")
    void rejectsCompleted() {
        when(stockInMapper.selectById(300L)).thenReturn(stockIn("completed", "30"));

        assertThatThrownBy(() -> service.complete(300L)).hasMessageContaining("不能入库");
        assertThatThrownBy(() -> service.cancel(300L)).hasMessageContaining("不能取消");
        verify(workorderMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("取消已提交的入库单")
    void cancel() {
        when(stockInMapper.selectById(300L)).thenReturn(stockIn("submitted", "30"));
        when(stockInMapper.update(any(), any())).thenReturn(1);

        service.cancel(300L);

        verify(statusLogService).record(BizType.STOCK_IN, 300L, "RK001", "cancel", "submitted", "cancelled", null);
    }

    @Test
    @DisplayName("列表带工单编号、产品与仓库名称")
    void list() {
        when(stockInMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(stockIn("submitted", "30")));
        when(workorderMapper.selectByIds(any())).thenReturn(List.of(workorder("in_progress", "50")));
        MesMaterial product = new MesMaterial();
        product.setMaterialId(50L);
        product.setMaterialCode("P01");
        product.setMaterialName("产品");
        when(materialMapper.selectByIds(any())).thenReturn(List.of(product));
        when(warehouseMapper.selectByIds(any())).thenReturn(List.of(warehouse(1L, "finished")));
        when(workorderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workorder("in_progress", "50")));
        MesStockInBo query = new MesStockInBo();
        query.setWorkorderCode("WO");

        PageResult<MesStockInVo> result = service.queryPageList(query, new PageParam(1, 10));

        assertThat(result.getItems()).singleElement().satisfies(vo -> {
            assertThat(vo.getWorkorderCode()).isEqualTo("WO001");
            assertThat(vo.getMaterialName()).isEqualTo("产品");
            assertThat(vo.getWarehouseName()).isEqualTo("成品仓");
        });
        when(stockInMapper.selectById(300L)).thenReturn(stockIn("completed", "30"));
        assertThat(service.queryById(300L).getStockInCode()).isEqualTo("RK001");
        verify(codeService, never()).next(anyString());
    }

}
