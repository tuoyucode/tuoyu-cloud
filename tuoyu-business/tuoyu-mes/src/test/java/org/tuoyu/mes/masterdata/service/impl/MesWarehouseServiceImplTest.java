package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesWarehouse;
import org.tuoyu.mes.masterdata.domain.bo.MesLocationBo;
import org.tuoyu.mes.masterdata.domain.bo.MesWarehouseBo;
import org.tuoyu.mes.masterdata.domain.vo.MesLocationVo;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLocationMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestMapper;
import org.tuoyu.mes.material.mapper.MesStockInMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("仓库与库位：类型校验、库位编码在仓库内唯一与删除前校验")
class MesWarehouseServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesWarehouseMapper warehouseMapper = MesTestSupport.mapper(MesWarehouseMapper.class);
    private final MesLocationMapper locationMapper = MesTestSupport.mapper(MesLocationMapper.class);
    private final MesFactoryMapper factoryMapper = MesTestSupport.mapper(MesFactoryMapper.class);
    private final MesMaterialRequestMapper requestMapper = MesTestSupport.mapper(MesMaterialRequestMapper.class);
    private final MesMaterialRequestItemMapper requestItemMapper =
        MesTestSupport.mapper(MesMaterialRequestItemMapper.class);
    private final MesStockInMapper stockInMapper = MesTestSupport.mapper(MesStockInMapper.class);

    private final MesWarehouseServiceImpl service = new MesWarehouseServiceImpl(warehouseMapper, locationMapper,
        factoryMapper, requestMapper, requestItemMapper, stockInMapper);

    private static MesWarehouseBo warehouseBo(String type) {
        MesWarehouseBo bo = new MesWarehouseBo();
        bo.setFactoryId(1L);
        bo.setWarehouseCode("WH01");
        bo.setWarehouseName("原料仓");
        bo.setWarehouseType(type);
        return bo;
    }

    private static MesWarehouse warehouse(long id) {
        MesWarehouse warehouse = new MesWarehouse();
        warehouse.setWarehouseId(id);
        warehouse.setFactoryId(1L);
        warehouse.setWarehouseCode("WH" + id);
        warehouse.setWarehouseName("仓库" + id);
        warehouse.setWarehouseType("raw");
        return warehouse;
    }

    private static MesLocation location(long id, long warehouseId) {
        MesLocation location = new MesLocation();
        location.setLocationId(id);
        location.setWarehouseId(warehouseId);
        location.setLocationCode("A-" + id);
        location.setLocationName("货架" + id);
        return location;
    }

    private static MesLocationBo locationBo() {
        MesLocationBo bo = new MesLocationBo();
        bo.setWarehouseId(60L);
        bo.setLocationCode("A-01");
        bo.setLocationName("一号货架");
        return bo;
    }

    @Test
    @DisplayName("仓库类型无效或工厂不存在时拒绝；新增时补齐默认值")
    void warehouseValidation() {
        assertThatThrownBy(() -> service.insertByBo(warehouseBo("raw"))).hasMessageContaining("工厂不存在");

        when(factoryMapper.selectById(1L)).thenReturn(MesWorkshopServiceImplTest.factory(1L, "一厂"));
        assertThatThrownBy(() -> service.insertByBo(warehouseBo("cold"))).hasMessageContaining("仓库类型");

        service.insertByBo(warehouseBo("finished"));
        ArgumentCaptor<MesWarehouse> captor = ArgumentCaptor.forClass(MesWarehouse.class);
        verify(warehouseMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
    }

    @Test
    @DisplayName("修改仓库；仓库不存在时提示")
    void updateWarehouse() {
        when(factoryMapper.selectById(1L)).thenReturn(MesWorkshopServiceImplTest.factory(1L, "一厂"));
        MesWarehouseBo bo = warehouseBo("raw");
        bo.setWarehouseId(60L);
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不存在");

        when(warehouseMapper.updateById(any(MesWarehouse.class))).thenReturn(1);
        service.updateByBo(bo);
    }

    @Test
    @DisplayName("仓库下还有库位或已有入库记录时不能删除")
    void warehouseDeleteChecks() {
        when(locationMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(60L))).hasMessageContaining("库位");

        when(locationMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(stockInMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(60L))).hasMessageContaining("入库");
        verify(warehouseMapper, never()).deleteByIds(anyCollection());

        when(stockInMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(warehouseMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteByIds(List.of(60L));
    }

    @Test
    @DisplayName("库位编码在同一仓库中重复时拒绝；所属仓库必须存在")
    void locationValidation() {
        assertThatThrownBy(() -> service.insertLocation(locationBo())).hasMessageContaining("仓库不存在");

        when(warehouseMapper.selectById(60L)).thenReturn(warehouse(60L));
        when(locationMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.insertLocation(locationBo())).hasMessageContaining("在该仓库中已存在");

        when(locationMapper.exists(any(Wrapper.class))).thenReturn(false);
        service.insertLocation(locationBo());
        verify(locationMapper).insert(any(MesLocation.class));
    }

    @Test
    @DisplayName("修改与删除库位")
    void locationUpdateAndDelete() {
        when(warehouseMapper.selectById(60L)).thenReturn(warehouse(60L));
        MesLocationBo bo = locationBo();
        bo.setLocationId(70L);
        assertThatThrownBy(() -> service.updateLocation(bo)).hasMessageContaining("不存在");
        when(locationMapper.updateById(any(MesLocation.class))).thenReturn(1);
        service.updateLocation(bo);

        when(requestItemMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteLocations(List.of(70L))).hasMessageContaining("领退料");
        when(requestItemMapper.exists(any(Wrapper.class))).thenReturn(false);
        assertThatThrownBy(() -> service.deleteLocations(List.of(70L))).hasMessageContaining("不存在");
        when(locationMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteLocations(List.of(70L));
    }

    @Test
    @DisplayName("库位列表带仓库名称；下拉选项的上级为仓库")
    void locationQueries() {
        when(locationMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(location(70L, 60L)));
        when(locationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(location(70L, 60L)));
        when(locationMapper.selectById(70L)).thenReturn(location(70L, 60L));
        when(warehouseMapper.selectByIds(anyCollection())).thenReturn(List.of(warehouse(60L)));

        MesLocationBo query = new MesLocationBo();
        query.setWarehouseId(60L);
        assertThat(service.queryLocationPageList(query, new PageParam()).getItems())
            .singleElement().extracting(MesLocationVo::getWarehouseName).isEqualTo("仓库60");
        assertThat(service.queryLocationById(70L).getLocationCode()).isEqualTo("A-70");
        assertThat(service.locationOptions(60L)).singleElement().extracting("parentId").isEqualTo(60L);
    }

    @Test
    @DisplayName("仓库查询与下拉选项")
    void warehouseQueries() {
        when(warehouseMapper.selectById(60L)).thenReturn(warehouse(60L));
        when(warehouseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(warehouse(60L)));
        when(factoryMapper.selectByIds(anyCollection()))
            .thenReturn(List.of(MesWorkshopServiceImplTest.factory(1L, "一厂")));

        assertThat(service.queryById(60L).getFactoryName()).isEqualTo("一厂");
        assertThat(service.queryList(new MesWarehouseBo())).hasSize(1);
        assertThat(service.queryPageList(new MesWarehouseBo(), new PageParam()).getItems()).isEmpty();
        assertThat(service.countList(new MesWarehouseBo())).isZero();
        assertThat(service.options(1L, "raw")).singleElement().extracting("parentId").isEqualTo(1L);
    }

}
