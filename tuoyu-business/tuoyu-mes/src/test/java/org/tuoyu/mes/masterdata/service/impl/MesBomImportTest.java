package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesBomItem;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.vo.MesBomImportVo;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.ExcelFiles;
import org.tuoyu.mes.support.MesTestSupport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("物料清单导入：按 BOM 编码分组、按编码引用物料、整体校验")
class MesBomImportTest {

    static {
        MesTestSupport.init();
    }

    private final MesBomMapper bomMapper = MesTestSupport.mapper(MesBomMapper.class);
    private final MesBomItemMapper bomItemMapper = MesTestSupport.mapper(MesBomItemMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);

    private final MesBomServiceImpl service = new MesBomServiceImpl(bomMapper, bomItemMapper, materialMapper,
        MesTestSupport.mapper(MesWorkorderMapper.class));

    {
        when(materialMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            material(1L, "P1", "product"), material(2L, "M1", "raw"), material(3L, "M2", "raw"),
            material(4L, "R1", "raw")));
        when(bomMapper.insert(any(MesBom.class))).thenAnswer(invocation -> {
            invocation.<MesBom>getArgument(0).setBomId(100L);
            return 1;
        });
    }

    static MesMaterial material(long id, String code, String type) {
        MesMaterial material = new MesMaterial();
        material.setMaterialId(id);
        material.setMaterialCode(code);
        material.setMaterialName("物料" + code);
        material.setMaterialType(type);
        return material;
    }

    static MesBomImportVo head(String bomCode, String product, String version, String item, String qty) {
        MesBomImportVo row = new MesBomImportVo();
        row.setBomCode(bomCode);
        row.setProductCode(product);
        row.setBomVersion(version);
        row.setItemCode(item);
        row.setQty(qty);
        return row;
    }

    static MesBomImportVo item(String bomCode, String item, String qty) {
        return head(bomCode, null, null, item, qty);
    }

    @Test
    @DisplayName("合格时写入表头与子件：子件按物料编码换成 ID，后续行的表头留空沿用第一行，默认版本取消同产品的其他默认")
    @SuppressWarnings("unchecked")
    void importsValidBom() {
        MesBomImportVo first = head("B1", "P1", "V1", "M1", "2");
        first.setIsDefault("是");
        first.setLossRate("0.05");
        MesBomImportVo second = item("B1", "M2", "0.5");

        int count = service.importData(ExcelFiles.xlsx(MesBomImportVo.class, List.of(first, second)));

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<MesBom> bom = ArgumentCaptor.forClass(MesBom.class);
        verify(bomMapper).insert(bom.capture());
        assertThat(bom.getValue()).satisfies(b -> {
            assertThat(b.getMaterialId()).isEqualTo(1L);
            assertThat(b.getIsDefault()).isEqualTo("Y");
            assertThat(b.getBaseQty()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(b.getStatus()).isEqualTo("0");
        });
        ArgumentCaptor<Collection<MesBomItem>> items = ArgumentCaptor.forClass(Collection.class);
        verify(bomItemMapper).insert(items.capture());
        List<MesBomItem> saved = new ArrayList<>(items.getValue());
        assertThat(saved).extracting(MesBomItem::getMaterialId).containsExactly(2L, 3L);
        assertThat(saved).extracting(MesBomItem::getBomId).containsOnly(100L);
        assertThat(saved).extracting(MesBomItem::getSortNo).containsExactly(1, 2);
        assertThat(saved.get(0).getLossRate()).isEqualByComparingTo("0.05");
        verify(bomMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("引用的物料不存在、子件是产品本身、子件重复、用量不合格时逐行报出")
    void rejectsBadItems() {
        List<MesBomImportVo> rows = List.of(head("B1", "P1", "V1", "M9", "1"), item("B1", "P1", "1"),
            item("B1", "M1", "1"), item("B1", "M1", "0"));

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesBomImportVo.class, rows)))
            .hasMessageContaining("第 2 行：子件物料编码 M9 不存在")
            .hasMessageContaining("第 3 行：子件不能是产品本身")
            .hasMessageContaining("第 5 行：子件物料 M1 与第 4 行重复")
            .hasMessageContaining("第 5 行：用量必须大于 0");
        verify(bomMapper, never()).insert(any(MesBom.class));
        verify(bomItemMapper, never()).insert(anyCollection());
    }

    @Test
    @DisplayName("产品不存在或不是成品、半成品时拒绝；后续行的表头与第一行不一致时报出")
    void rejectsBadProduct() {
        List<MesBomImportVo> rows = List.of(head("B1", "P9", "V1", "M1", "1"), head("B2", "R1", "V1", "M1", "1"),
            head("B3", "P1", "V1", "M1", "1"), head("B3", "P2", null, "M2", "1"));

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesBomImportVo.class, rows)))
            .hasMessageContaining("第 2 行：产品编码 P9 不存在")
            .hasMessageContaining("第 3 行：物料 R1（物料R1）不是成品或半成品，不能建立物料清单")
            .hasMessageContaining("第 5 行：BOM编码 B3 的产品编码与第 4 行不一致");
        verify(bomMapper, never()).insert(any(MesBom.class));
    }

    @Test
    @DisplayName("BOM 编码已存在、同一产品的版本在库中或文件中重复、文件中有多个默认版本时拒绝")
    void rejectsDuplicates() {
        MesBom existing = new MesBom();
        existing.setBomCode("B1");
        existing.setMaterialId(1L);
        existing.setBomVersion("V0");
        when(bomMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        MesBomImportVo b2 = head("B2", "P1", "V1", "M1", "1");
        b2.setIsDefault("是");
        MesBomImportVo b3 = head("B3", "P1", "v1", "M1", "1");
        MesBomImportVo b4 = head("B4", "P1", "V2", "M1", "1");
        b4.setIsDefault("是");
        List<MesBomImportVo> rows = List.of(head("B1", "P1", "V9", "M1", "1"), head("B5", "P1", "V0", "M1", "1"),
            b2, b3, b4);

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesBomImportVo.class, rows)))
            .hasMessageContaining("第 2 行：BOM编码 B1 已存在")
            .hasMessageContaining("第 3 行：产品 P1 已有版本为“V0”的物料清单")
            .hasMessageContaining("第 5 行：产品 P1 的版本“v1”与第 4 行的物料清单重复")
            .hasMessageContaining("第 6 行：产品 P1 在文件中有多个默认版本（另见第 4 行）");
        verify(bomMapper, never()).insert(any(MesBom.class));
    }

}
