package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.andon.mapper.MesAndonEventMapper;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesFactory;
import org.tuoyu.mes.masterdata.domain.MesWorkshop;
import org.tuoyu.mes.masterdata.domain.bo.MesWorkshopBo;
import org.tuoyu.mes.masterdata.domain.vo.MesWorkshopVo;
import org.tuoyu.mes.masterdata.mapper.MesDeviceMapper;
import org.tuoyu.mes.masterdata.mapper.MesFactoryMapper;
import org.tuoyu.mes.masterdata.mapper.MesLineMapper;
import org.tuoyu.mes.masterdata.mapper.MesTeamMapper;
import org.tuoyu.mes.masterdata.mapper.MesWorkshopMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("车间：工厂校验、换工厂限制、删除前校验与下拉选项")
class MesWorkshopServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesWorkshopMapper workshopMapper = MesTestSupport.mapper(MesWorkshopMapper.class);
    private final MesFactoryMapper factoryMapper = MesTestSupport.mapper(MesFactoryMapper.class);
    private final MesLineMapper lineMapper = MesTestSupport.mapper(MesLineMapper.class);
    private final MesTeamMapper teamMapper = MesTestSupport.mapper(MesTeamMapper.class);
    private final MesDeviceMapper deviceMapper = MesTestSupport.mapper(MesDeviceMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesAndonEventMapper andonEventMapper = MesTestSupport.mapper(MesAndonEventMapper.class);

    private final MesWorkshopServiceImpl service = new MesWorkshopServiceImpl(workshopMapper, factoryMapper, lineMapper,
        teamMapper, deviceMapper, workorderMapper, andonEventMapper);

    static MesFactory factory(long id, String name) {
        MesFactory factory = new MesFactory();
        factory.setFactoryId(id);
        factory.setFactoryName(name);
        return factory;
    }

    static MesWorkshop workshop(long id, long factoryId) {
        MesWorkshop workshop = new MesWorkshop();
        workshop.setWorkshopId(id);
        workshop.setFactoryId(factoryId);
        workshop.setWorkshopCode("W" + id);
        workshop.setWorkshopName("车间" + id);
        return workshop;
    }

    private static MesWorkshopBo bo(Long factoryId) {
        MesWorkshopBo bo = new MesWorkshopBo();
        bo.setFactoryId(factoryId);
        bo.setWorkshopCode("W01");
        bo.setWorkshopName("装配车间");
        return bo;
    }

    @Test
    @DisplayName("新增时补齐状态与排序，并回填主键")
    void insertFillsDefaults() {
        when(factoryMapper.selectById(1L)).thenReturn(factory(1L, "一厂"));
        when(workshopMapper.insert(any(MesWorkshop.class))).thenAnswer(invocation -> {
            invocation.<MesWorkshop>getArgument(0).setWorkshopId(5L);
            return 1;
        });
        MesWorkshopBo bo = bo(1L);

        service.insertByBo(bo);

        ArgumentCaptor<MesWorkshop> captor = ArgumentCaptor.forClass(MesWorkshop.class);
        verify(workshopMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
        assertThat(captor.getValue().getSortNo()).isZero();
        assertThat(bo.getWorkshopId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("所属工厂不存在时拒绝新增")
    void rejectsMissingFactory() {
        assertThatThrownBy(() -> service.insertByBo(bo(1L))).hasMessageContaining("工厂不存在");
        verify(workshopMapper, never()).insert(any(MesWorkshop.class));
    }

    @Test
    @DisplayName("编码已存在时拒绝新增")
    void rejectsDuplicateCode() {
        when(workshopMapper.exists(any(Wrapper.class))).thenReturn(true);

        assertThatThrownBy(() -> service.insertByBo(bo(1L))).hasMessageContaining("W01");
    }

    @Test
    @DisplayName("车间下已有产线时不能更换工厂")
    void rejectsFactoryChangeWithChildren() {
        when(workshopMapper.selectById(5L)).thenReturn(workshop(5L, 1L));
        when(factoryMapper.selectById(2L)).thenReturn(factory(2L, "二厂"));
        when(lineMapper.exists(any(Wrapper.class))).thenReturn(true);
        MesWorkshopBo bo = bo(2L);
        bo.setWorkshopId(5L);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不能更换所属工厂");
        verify(workshopMapper, never()).updateById(any(MesWorkshop.class));
    }

    @Test
    @DisplayName("工厂不变时可以修改，即使车间下已有产线")
    void updatesWithoutFactoryChange() {
        when(workshopMapper.selectById(5L)).thenReturn(workshop(5L, 1L));
        when(factoryMapper.selectById(1L)).thenReturn(factory(1L, "一厂"));
        when(lineMapper.exists(any(Wrapper.class))).thenReturn(true);
        when(workshopMapper.updateById(any(MesWorkshop.class))).thenReturn(1);
        MesWorkshopBo bo = bo(1L);
        bo.setWorkshopId(5L);

        service.updateByBo(bo);

        verify(workshopMapper).updateById(any(MesWorkshop.class));
    }

    @Test
    @DisplayName("修改不存在的车间时提示不存在")
    void updateMissing() {
        MesWorkshopBo bo = bo(1L);
        bo.setWorkshopId(5L);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("删除前检查产线、班组、设备与工单")
    void deleteChecks() {
        when(teamMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(5L))).hasMessageContaining("班组");

        when(teamMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(5L))).hasMessageContaining("工单");

        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(andonEventMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(5L))).hasMessageContaining("安灯");
        verify(workshopMapper, never()).deleteByIds(anyCollection());
    }

    @Test
    @DisplayName("没有下级时删除成功；车间不存在时提示")
    void deleteSucceeds() {
        assertThatThrownBy(() -> service.deleteByIds(List.of(5L))).hasMessageContaining("不存在");

        when(workshopMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteByIds(List.of(5L));
        verify(workshopMapper, times(2)).deleteByIds(anyCollection());
    }

    @Test
    @DisplayName("下拉选项的上级为工厂")
    void optionsCarryFactory() {
        when(workshopMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workshop(5L, 1L)));

        List<MesOptionVo> options = service.options(1L);

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.getId()).isEqualTo(5L);
            assertThat(option.getCode()).isEqualTo("W5");
            assertThat(option.getParentId()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("列表批量填充工厂名称")
    void pageFillsFactoryName() {
        when(workshopMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(workshop(5L, 1L)));
        when(factoryMapper.selectByIds(anyCollection())).thenReturn(List.of(factory(1L, "一厂")));

        PageResult<MesWorkshopVo> page = service.queryPageList(new MesWorkshopBo(), new PageParam());

        assertThat(page.getItems()).singleElement()
            .extracting(MesWorkshopVo::getFactoryName).isEqualTo("一厂");
        when(workshopMapper.selectById(5L)).thenReturn(workshop(5L, 1L));
        assertThat(service.queryById(5L).getFactoryName()).isEqualTo("一厂");
    }

    @Test
    @DisplayName("导出列表与统计")
    void listAndCount() {
        when(workshopMapper.selectList(any(Wrapper.class))).thenReturn(List.of(workshop(5L, 1L)));

        assertThat(service.queryList(new MesWorkshopBo())).hasSize(1);
        assertThat(service.countList(new MesWorkshopBo())).isZero();
    }

}
