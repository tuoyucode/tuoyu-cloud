package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.mes.masterdata.domain.MesRoute;
import org.tuoyu.mes.masterdata.domain.MesRouteOperation;
import org.tuoyu.mes.masterdata.domain.vo.MesRouteImportVo;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteOperationMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.ExcelFiles;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("工艺路线导入：按路线编码分组、产品按编码引用、工序顺序校验")
class MesRouteImportTest {

    static {
        MesTestSupport.init();
    }

    private final MesRouteMapper routeMapper = MesTestSupport.mapper(MesRouteMapper.class);
    private final MesRouteOperationMapper operationMapper = MesTestSupport.mapper(MesRouteOperationMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);

    private final MesRouteServiceImpl service = new MesRouteServiceImpl(routeMapper, operationMapper, materialMapper,
        MesTestSupport.mapper(MesWorkorderMapper.class));

    {
        when(materialMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
            MesBomImportTest.material(1L, "P1", "product"), MesBomImportTest.material(2L, "R1", "raw")));
        when(routeMapper.insert(any(MesRoute.class))).thenAnswer(invocation -> {
            invocation.<MesRoute>getArgument(0).setRouteId(200L);
            return 1;
        });
    }

    static MesRouteImportVo row(String routeCode, String product, String seq, String operation) {
        MesRouteImportVo row = new MesRouteImportVo();
        row.setRouteCode(routeCode);
        row.setRouteName(product == null ? null : "路线" + routeCode);
        row.setProductCode(product);
        row.setRouteVersion(product == null ? null : "V1");
        row.setSeqNo(seq);
        row.setOperationCode(operation);
        row.setOperationName(operation == null ? null : "工序" + operation);
        return row;
    }

    @Test
    @DisplayName("合格时写入路线与工序：工序按顺序排列，首检、工时取默认值")
    @SuppressWarnings("unchecked")
    void importsValidRoute() {
        MesRouteImportVo first = row("RT1", "P1", "20", "OP20");
        MesRouteImportVo second = row("RT1", null, "10", "OP10");
        second.setFirstInspection("是");
        second.setStandardMinutes("1.5");

        int count = service.importData(ExcelFiles.xlsx(MesRouteImportVo.class, List.of(first, second)));

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<MesRoute> route = ArgumentCaptor.forClass(MesRoute.class);
        verify(routeMapper).insert(route.capture());
        assertThat(route.getValue().getMaterialId()).isEqualTo(1L);
        assertThat(route.getValue().getIsDefault()).isEqualTo("N");
        ArgumentCaptor<Collection<MesRouteOperation>> operations = ArgumentCaptor.forClass(Collection.class);
        verify(operationMapper).insert(operations.capture());
        List<MesRouteOperation> saved = new ArrayList<>(operations.getValue());
        assertThat(saved).extracting(MesRouteOperation::getSeqNo).containsExactly(10, 20);
        assertThat(saved).extracting(MesRouteOperation::getRouteId).containsOnly(200L);
        assertThat(saved).extracting(MesRouteOperation::getFirstInspection).containsExactly("Y", "N");
        assertThat(saved.get(0).getStandardMinutes()).isEqualByComparingTo("1.5");
        assertThat(saved.get(1).getStandardMinutes()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("工序顺序重复、不是正整数、工序名称为空、产品不是成品时逐行报出")
    void rejectsBadRows() {
        MesRouteImportVo noName = row("RT1", null, "30", "OP30");
        noName.setOperationName(null);
        List<MesRouteImportVo> rows = List.of(row("RT1", "P1", "10", "OP10"), row("RT1", null, "10", "OP11"),
            row("RT1", null, "1.5", "OP15"), noName, row("RT2", "R1", "10", "OP10"));

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesRouteImportVo.class, rows)))
            .hasMessageContaining("第 3 行：工序顺序 10 与第 2 行重复")
            .hasMessageContaining("第 4 行：工序顺序“1.5”不是整数")
            .hasMessageContaining("第 5 行：工序名称不能为空")
            .hasMessageContaining("第 6 行：物料 R1（物料R1）不是成品或半成品，不能建立工艺路线");
        verify(routeMapper, never()).insert(any(MesRoute.class));
        verify(operationMapper, never()).insert(anyCollection());
    }

    @Test
    @DisplayName("路线编码已存在、在文件内同一产品版本重复时拒绝")
    void rejectsDuplicates() {
        MesRoute existing = new MesRoute();
        existing.setRouteCode("RT1");
        existing.setMaterialId(9L);
        existing.setRouteVersion("V1");
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesRouteImportVo.class,
            List.of(row("RT1", "P1", "10", "OP10"), row("RT2", "P1", "10", "OP10")))))
            .hasMessageContaining("第 2 行：工艺路线编码 RT1 已存在")
            .hasMessageContaining("第 3 行：产品 P1 的版本“V1”与第 2 行的工艺路线重复");
        verify(routeMapper, never()).insert(any(MesRoute.class));
    }

}
