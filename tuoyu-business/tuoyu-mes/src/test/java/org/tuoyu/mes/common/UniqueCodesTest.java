package org.tuoyu.mes.common;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.masterdata.domain.MesLocation;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("编码唯一：不区分大小写的查询条件与撞上唯一索引时的提示")
class UniqueCodesTest {

    static {
        MesTestSupport.init();
    }

    private static MesMaterial material(long id, String code) {
        MesMaterial material = new MesMaterial();
        material.setMaterialId(id);
        material.setMaterialCode(code);
        return material;
    }

    @Test
    @DisplayName("编码相同的条件写成 lower(列) = lower(值)，与唯一索引的表达式一致；值作为参数传入")
    void sameCodeUsesLowerOnBothSides() {
        LambdaQueryWrapper<MesLocation> wrapper = QueryBuilder.lambda(MesLocation.class)
            .eq(MesLocation::getWarehouseId, 7L)
            .func(UniqueCodes.sameCode(MesLocation::getLocationCode, "A-01'"))
            .build();

        assertThat(wrapper.getSqlSegment()).contains("lower(location_code) = lower(#{").doesNotContain("A-01");
        assertThat(wrapper.getParamNameValuePairs()).containsValues(7L, "A-01'");
    }

    @Test
    @DisplayName("按编码批量查询：lower(列) in (lower(值), ...)，查回的编码大小写不同也能按小写的键对上")
    @SuppressWarnings("unchecked")
    void byCodesIgnoresCase() {
        MesMaterialMapper mapper = MesTestSupport.mapper(MesMaterialMapper.class);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(material(1L, "m001")));

        Map<String, MesMaterial> found = MesLookups.byCodes(mapper, MesMaterial.class, MesMaterial::getMaterialCode,
            List.of("M001", "M002"));

        assertThat(found).containsOnlyKeys("m001");
        assertThat(found.get(MesLookups.codeKey("M001")).getMaterialId()).isEqualTo(1L);
        ArgumentCaptor<Wrapper<MesMaterial>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("lower(material_code) in (lower(#{").contains("), lower(#{");
    }

    @Test
    @DisplayName("写入撞上唯一索引时转为带编码的业务提示，其他异常原样抛出")
    void writeTranslatesDuplicateKey() {
        assertThat(UniqueCodes.write(() -> 1, "物料编码“M001”已存在")).isEqualTo(1);
        assertThatThrownBy(() -> UniqueCodes.write(() -> {
            throw new DuplicateKeyException("Duplicate entry 'default-m001' for key 'uk_material_code'");
        }, "物料编码“M001”已存在")).isInstanceOf(ServiceException.class).hasMessage("物料编码“M001”已存在");
        assertThatThrownBy(() -> UniqueCodes.write(() -> {
            throw new IllegalStateException("连接断开");
        }, "物料编码“M001”已存在")).isInstanceOf(IllegalStateException.class);
    }

}
