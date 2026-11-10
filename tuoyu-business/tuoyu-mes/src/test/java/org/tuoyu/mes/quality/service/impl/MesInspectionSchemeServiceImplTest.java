package org.tuoyu.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.quality.domain.MesInspectionScheme;
import org.tuoyu.mes.quality.domain.bo.MesInspectionSchemeBo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionSchemeVo;
import org.tuoyu.mes.quality.mapper.MesInspectionMapper;
import org.tuoyu.mes.quality.mapper.MesInspectionSchemeMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("检验方案：编码唯一、匹配优先级与删除前校验")
class MesInspectionSchemeServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesInspectionSchemeMapper schemeMapper = MesTestSupport.mapper(MesInspectionSchemeMapper.class);
    private final MesInspectionMapper inspectionMapper = MesTestSupport.mapper(MesInspectionMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);

    private final MesInspectionSchemeServiceImpl service = new MesInspectionSchemeServiceImpl(schemeMapper,
        inspectionMapper, materialMapper);

    private static MesInspectionScheme scheme(Long id, Long materialId, String operationCode) {
        MesInspectionScheme scheme = new MesInspectionScheme();
        scheme.setSchemeId(id);
        scheme.setSchemeCode("S" + id);
        scheme.setSchemeName("方案" + id);
        scheme.setInspectionType("first");
        scheme.setMaterialId(materialId);
        scheme.setOperationCode(operationCode);
        scheme.setStatus("0");
        return scheme;
    }

    private static MesInspectionSchemeBo bo() {
        MesInspectionSchemeBo bo = new MesInspectionSchemeBo();
        bo.setSchemeCode("S01");
        bo.setSchemeName("首检方案");
        bo.setInspectionType("first");
        return bo;
    }

    @Test
    @DisplayName("匹配程度：产品 + 工序 > 只匹配产品 > 通用；指定了不同产品或工序的方案不适用")
    void matchScore() {
        assertThat(MesInspectionSchemeServiceImpl.matchScore(scheme(1L, 50L, "OP10"), 50L, "OP10")).isEqualTo(3);
        assertThat(MesInspectionSchemeServiceImpl.matchScore(scheme(2L, 50L, null), 50L, "OP10")).isEqualTo(2);
        assertThat(MesInspectionSchemeServiceImpl.matchScore(scheme(3L, null, "OP10"), 50L, "OP10")).isEqualTo(1);
        assertThat(MesInspectionSchemeServiceImpl.matchScore(scheme(4L, null, null), 50L, "OP10")).isZero();
        assertThat(MesInspectionSchemeServiceImpl.matchScore(scheme(5L, 51L, null), 50L, "OP10")).isNegative();
        assertThat(MesInspectionSchemeServiceImpl.matchScore(scheme(6L, 50L, "OP20"), 50L, "OP10")).isNegative();
    }

    @Test
    @DisplayName("返回最匹配的方案；同等匹配时取最早建立的")
    void matchBest() {
        when(schemeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            scheme(4L, null, null), scheme(2L, 50L, null), scheme(1L, 50L, null), scheme(6L, 50L, "OP20")));

        MesInspectionSchemeVo vo = service.match("first", 50L, "OP10");

        assertThat(vo.getSchemeId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("产品与工序都匹配的方案优先；没有可用方案时返回 null")
    void matchPrefersOperation() {
        when(schemeMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(scheme(2L, 50L, null), scheme(1L, 50L, "OP10")))
            .thenReturn(List.of(scheme(5L, 51L, null)));

        assertThat(service.match("first", 50L, "OP10").getSchemeId()).isEqualTo(1L);
        assertThat(service.match("first", 50L, "OP10")).isNull();
        assertThat(service.match(null, 50L, "OP10")).isNull();
    }

    @Test
    @DisplayName("新增时检验类型必须有效、编码不能重复，状态默认启用")
    void insert() {
        MesInspectionSchemeBo invalid = bo();
        invalid.setInspectionType("unknown");
        assertThatThrownBy(() -> service.insertByBo(invalid)).hasMessageContaining("检验类型不正确");

        when(schemeMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.insertByBo(bo())).hasMessageContaining("已存在");

        when(schemeMapper.exists(any(Wrapper.class))).thenReturn(false);
        MesInspectionSchemeBo bo = bo();
        bo.setOperationCode("  ");
        service.insertByBo(bo);
        ArgumentCaptor<MesInspectionScheme> captor = ArgumentCaptor.forClass(MesInspectionScheme.class);
        verify(schemeMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
        assertThat(captor.getValue().getOperationCode()).isNull();
    }

    @Test
    @DisplayName("适用产品不存在时拒绝；修改不存在的方案时提示不存在")
    void update() {
        MesInspectionSchemeBo bo = bo();
        bo.setSchemeId(1L);
        bo.setMaterialId(50L);
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("适用产品不存在");

        MesMaterial material = new MesMaterial();
        material.setMaterialId(50L);
        when(materialMapper.selectById(50L)).thenReturn(material);
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不存在");

        when(schemeMapper.update(any(), any())).thenReturn(1);
        service.updateByBo(bo);
    }

    @Test
    @DisplayName("还有待检验的检验单使用时不能删除")
    void delete() {
        when(inspectionMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(1L))).hasMessageContaining("待检验");
        verify(schemeMapper, never()).deleteByIds(any());

        when(inspectionMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(schemeMapper.deleteByIds(any())).thenReturn(1);
        service.deleteByIds(List.of(1L));
    }

    @Test
    @DisplayName("列表与详情带适用产品名称，下拉选项只含编码与名称")
    void queries() {
        MesMaterial material = new MesMaterial();
        material.setMaterialId(50L);
        material.setMaterialCode("P01");
        material.setMaterialName("产品");
        when(materialMapper.selectByIds(any())).thenReturn(List.of(material));
        when(schemeMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(scheme(1L, 50L, null)));
        when(schemeMapper.selectById(1L)).thenReturn(scheme(1L, 50L, null));
        when(schemeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(scheme(1L, 50L, null)));

        PageResult<MesInspectionSchemeVo> page = service.queryPageList(new MesInspectionSchemeBo(), new PageParam(1, 10));
        assertThat(page.getItems()).singleElement().satisfies(vo -> assertThat(vo.getMaterialName()).isEqualTo("产品"));
        assertThat(service.queryById(1L).getMaterialCode()).isEqualTo("P01");
        List<MesOptionVo> options = service.options("first", 50L);
        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.getCode()).isEqualTo("S1");
            assertThat(option.getName()).isEqualTo("方案1");
        });
        assertThatThrownBy(() -> service.queryById(2L)).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("导出：先按相同条件统计行数，列表带适用产品名称")
    void export() {
        when(schemeMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(scheme(1L, 50L, null)));
        when(schemeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(scheme(1L, 50L, null)));
        MesMaterial material = new MesMaterial();
        material.setMaterialId(50L);
        material.setMaterialCode("P01");
        material.setMaterialName("产品");
        when(materialMapper.selectByIds(any())).thenReturn(List.of(material));
        MesInspectionSchemeBo query = new MesInspectionSchemeBo();
        query.setSchemeCode("QS");

        assertThat(service.countList(query)).isZero();
        assertThat(service.queryList(query)).singleElement()
            .satisfies(vo -> assertThat(vo.getMaterialName()).isEqualTo("产品"));
    }

}
