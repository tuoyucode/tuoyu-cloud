package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.bo.MesFactoryBo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesWarehouseMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("工厂：编码唯一、默认值与删除前校验")
class MesFactoryServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesFactoryMapper factoryMapper = MesTestSupport.mapper(MesFactoryMapper.class);
    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesWarehouseMapper warehouseMapper = MesTestSupport.mapper(MesWarehouseMapper.class);
    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);

    private final MesFactoryServiceImpl service = new MesFactoryServiceImpl(factoryMapper, workshopMapper, warehouseMapper,
        deviceMapper);

    private static MesFactoryBo bo(String code) {
        MesFactoryBo bo = new MesFactoryBo();
        bo.setFactoryCode(code);
        bo.setFactoryName("一厂");
        return bo;
    }

    @Test
    @DisplayName("新增时补齐状态与排序，并回填主键")
    void insertFillsDefaults() {
        when(factoryMapper.insert(any(MesFactory.class))).thenAnswer(invocation -> {
            invocation.<MesFactory>getArgument(0).setFactoryId(9L);
            return 1;
        });
        MesFactoryBo bo = bo("F01");

        service.insertByBo(bo);

        ArgumentCaptor<MesFactory> captor = ArgumentCaptor.forClass(MesFactory.class);
        verify(factoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
        assertThat(captor.getValue().getSortNo()).isZero();
        assertThat(bo.getFactoryId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("编码已存在时拒绝新增")
    void rejectsDuplicateCode() {
        when(factoryMapper.exists(any(Wrapper.class))).thenReturn(true);

        assertThatThrownBy(() -> service.insertByBo(bo("F01")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("F01");
        verify(factoryMapper, never()).insert(any(MesFactory.class));
    }

    @Test
    @DisplayName("修改不存在的工厂时提示不存在")
    void updateMissing() {
        MesFactoryBo bo = bo("F01");
        bo.setFactoryId(1L);
        when(factoryMapper.updateById(any(MesFactory.class))).thenReturn(0);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("工厂下还有车间时不能删除")
    void rejectsDeleteWithWorkshops() {
        when(workshopMapper.exists(any(Wrapper.class))).thenReturn(true);

        assertThatThrownBy(() -> service.deleteByIds(List.of(1L))).hasMessageContaining("车间");
        verify(factoryMapper, never()).deleteByIds(any());
    }

    @Test
    @DisplayName("查询不存在的工厂时提示不存在")
    void queryMissing() {
        assertThatThrownBy(() -> service.queryById(1L)).hasMessageContaining("不存在");
    }

}
