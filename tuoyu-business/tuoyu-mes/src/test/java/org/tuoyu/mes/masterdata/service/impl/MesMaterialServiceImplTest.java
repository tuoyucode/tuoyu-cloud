package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.bo.MesMaterialBo;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("物料：类型与追溯方式校验、被引用时的限制与下拉选项")
class MesMaterialServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesBomMapper bomMapper = MesTestSupport.mapper(MesBomMapper.class);
    private final MesBomItemMapper bomItemMapper = MesTestSupport.mapper(MesBomItemMapper.class);
    private final MesRouteMapper routeMapper = MesTestSupport.mapper(MesRouteMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesMaterialRequestItemMapper requestItemMapper =
        MesTestSupport.mapper(MesMaterialRequestItemMapper.class);

    private final MesMaterialServiceImpl service = new MesMaterialServiceImpl(materialMapper, bomMapper, bomItemMapper,
        routeMapper, workorderMapper, requestItemMapper);

    static MesMaterial material(long id, String type) {
        MesMaterial material = new MesMaterial();
        material.setMaterialId(id);
        material.setMaterialCode("M" + id);
        material.setMaterialName("物料" + id);
        material.setMaterialType(type);
        material.setUnit("个");
        return material;
    }

    private static MesMaterialBo bo(String type) {
        MesMaterialBo bo = new MesMaterialBo();
        bo.setMaterialCode("M01");
        bo.setMaterialName("螺丝");
        bo.setMaterialType(type);
        bo.setUnit("个");
        return bo;
    }

    @Test
    @DisplayName("新增时追溯方式默认为不追溯")
    void insertDefaultsTraceMode() {
        service.insertByBo(bo("raw"));

        ArgumentCaptor<MesMaterial> captor = ArgumentCaptor.forClass(MesMaterial.class);
        verify(materialMapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceMode()).isEqualTo("none");
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
    }

    @Test
    @DisplayName("物料类型或追溯方式无效时拒绝")
    void rejectsInvalidCodes() {
        assertThatThrownBy(() -> service.insertByBo(bo("unknown"))).hasMessageContaining("物料类型");

        MesMaterialBo bo = bo("raw");
        bo.setTraceMode("qr");
        assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("追溯方式");
        verify(materialMapper, never()).insert(any(MesMaterial.class));
    }

    @Test
    @DisplayName("编码已存在时拒绝")
    void rejectsDuplicateCode() {
        when(materialMapper.exists(any(Wrapper.class))).thenReturn(true);

        assertThatThrownBy(() -> service.insertByBo(bo("raw"))).hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("已作为产品建立物料清单的物料，类型不能换成原材料")
    void rejectsTypeChangeWhenUsedAsProduct() {
        when(materialMapper.selectById(1L)).thenReturn(material(1L, "product"));
        when(bomMapper.exists(any(Wrapper.class))).thenReturn(true);
        MesMaterialBo bo = bo("raw");
        bo.setMaterialId(1L);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("成品或半成品");
        verify(materialMapper, never()).updateById(any(MesMaterial.class));
    }

    @Test
    @DisplayName("修改物料：保持产品类型时不检查引用")
    void updateProduct() {
        when(materialMapper.selectById(1L)).thenReturn(material(1L, "product"));
        when(materialMapper.updateById(any(MesMaterial.class))).thenReturn(1);
        MesMaterialBo bo = bo("semi");
        bo.setMaterialId(1L);

        service.updateByBo(bo);

        verify(bomMapper, never()).exists(any(Wrapper.class));
        verify(materialMapper).updateById(any(MesMaterial.class));
    }

    @Test
    @DisplayName("被物料清单、工单引用的物料不能删除")
    void deleteChecks() {
        when(bomItemMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(1L))).hasMessageContaining("子件");

        when(bomItemMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(1L))).hasMessageContaining("工单");
        verify(materialMapper, never()).deleteByIds(anyCollection());

        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(materialMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteByIds(List.of(1L));
        verify(materialMapper).deleteByIds(anyCollection());
    }

    @Test
    @DisplayName("下拉选项按类型与关键字过滤，最多 200 条")
    @SuppressWarnings("unchecked")
    void optionsFilter() {
        when(materialMapper.selectList(any(Wrapper.class))).thenReturn(List.of(material(1L, "product")));

        List<MesOptionVo> options = service.options("product, semi", "M");

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.getId()).isEqualTo(1L);
            assertThat(option.getParentId()).isNull();
        });
        ArgumentCaptor<Wrapper<MesMaterial>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(materialMapper).selectList(captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertThat(sql).contains("material_type IN").contains("material_code LIKE").contains("limit 200");
    }

    @Test
    @DisplayName("详情、列表与统计")
    void queries() {
        assertThatThrownBy(() -> service.queryById(1L)).hasMessageContaining("不存在");
        when(materialMapper.selectById(1L)).thenReturn(material(1L, "raw"));
        when(materialMapper.selectList(any(Wrapper.class))).thenReturn(List.of(material(1L, "raw")));

        assertThat(service.queryById(1L).getMaterialCode()).isEqualTo("M1");
        assertThat(service.queryList(new MesMaterialBo())).hasSize(1);
        assertThat(service.queryPageList(new MesMaterialBo(), new PageParam()).getItems()).isEmpty();
        assertThat(service.countList(new MesMaterialBo())).isZero();
    }

}
