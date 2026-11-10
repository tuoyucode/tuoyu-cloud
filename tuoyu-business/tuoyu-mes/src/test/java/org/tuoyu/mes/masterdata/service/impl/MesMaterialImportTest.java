package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.excel.core.ExcelRowLimits;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.domain.vo.MesDeviceImportVo;
import org.tuoyu.mes.masterdata.domain.vo.MesMaterialImportVo;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.material.mapper.MesMaterialRequestItemMapper;
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

@DisplayName("物料导入：整体校验、逐行错误、名称转换与行数上限")
class MesMaterialImportTest {

    static {
        MesTestSupport.init();
    }

    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);

    private final MesMaterialServiceImpl service = new MesMaterialServiceImpl(materialMapper,
        MesTestSupport.mapper(MesBomMapper.class), MesTestSupport.mapper(MesBomItemMapper.class),
        MesTestSupport.mapper(MesRouteMapper.class), MesTestSupport.mapper(MesWorkorderMapper.class),
        MesTestSupport.mapper(MesMaterialRequestItemMapper.class));

    static MesMaterialImportVo row(String code, String type, String unit) {
        MesMaterialImportVo row = new MesMaterialImportVo();
        row.setMaterialCode(code);
        row.setMaterialName("物料" + code);
        row.setMaterialType(type);
        row.setUnit(unit);
        return row;
    }

    @SuppressWarnings("unchecked")
    private List<MesMaterial> inserted() {
        ArgumentCaptor<Collection<MesMaterial>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(materialMapper).insert(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    @Test
    @DisplayName("全部合格时一次写入：中文名称转换为编码，未填的追溯方式、状态、排序号取默认值")
    void importsValidRows() {
        MesMaterialImportVo first = row("M001", "成品", "台");
        first.setTraceMode("按序列号");
        first.setStatus("停用");
        first.setSortNo("3");
        MesMaterialImportVo second = row("M002", "原材料", "kg");

        int count = service.importData(ExcelFiles.xlsx(MesMaterialImportVo.class, List.of(first, second)));

        assertThat(count).isEqualTo(2);
        List<MesMaterial> materials = inserted();
        assertThat(materials).extracting(MesMaterial::getMaterialCode).containsExactly("M001", "M002");
        assertThat(materials.get(0)).satisfies(m -> {
            assertThat(m.getMaterialType()).isEqualTo("product");
            assertThat(m.getTraceMode()).isEqualTo("sn");
            assertThat(m.getStatus()).isEqualTo("1");
            assertThat(m.getSortNo()).isEqualTo(3);
        });
        assertThat(materials.get(1)).satisfies(m -> {
            assertThat(m.getMaterialType()).isEqualTo("raw");
            assertThat(m.getTraceMode()).isEqualTo("none");
            assertThat(m.getStatus()).isEqualTo("0");
            assertThat(m.getSortNo()).isZero();
        });
    }

    @Test
    @DisplayName("编码在文件内重复时拒绝，提示与哪一行重复（不区分大小写），一行都不写入")
    void rejectsDuplicateInFile() {
        List<MesMaterialImportVo> rows = List.of(row("M001", "成品", "台"), row("M002", "成品", "台"),
            row("m001", "成品", "台"));

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesMaterialImportVo.class, rows)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("没有写入任何数据")
            .hasMessageContaining("第 4 行：物料编码 m001 与第 2 行重复");
        verify(materialMapper, never()).insert(anyCollection());
    }

    @Test
    @DisplayName("编码与库中已有的物料重复时拒绝")
    void rejectsExistingCode() {
        MesMaterial existing = new MesMaterial();
        existing.setMaterialCode("M001");
        when(materialMapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesMaterialImportVo.class,
            List.of(row("M002", "成品", "台"), row("M001", "成品", "台")))))
            .hasMessageContaining("第 3 行：物料编码 M001 已存在");
        verify(materialMapper, never()).insert(anyCollection());
    }

    @Test
    @DisplayName("逐行列出全部错误：必填、名称不正确、数字格式")
    void listsAllErrors() {
        MesMaterialImportVo badType = row("M001", "成品X", "台");
        MesMaterialImportVo noUnit = row("M002", "成品", null);
        MesMaterialImportVo badSort = row("M003", "辅料", "个");
        badSort.setSortNo("第一");
        badSort.setTraceMode("全程");

        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesMaterialImportVo.class,
            List.of(badType, noUnit, badSort))))
            .hasMessageContaining("共 4 处错误")
            .hasMessageContaining("第 2 行：物料类型“成品X”不正确，应为：成品、半成品、原材料、辅料")
            .hasMessageContaining("第 3 行：计量单位不能为空")
            .hasMessageContaining("第 4 行：追溯方式“全程”不正确")
            .hasMessageContaining("第 4 行：排序号“第一”不是数字");
        verify(materialMapper, never()).insert(anyCollection());
    }

    @Test
    @DisplayName("超过导入行数上限时直接拒绝，一行都不处理")
    void rejectsTooManyRows() {
        ExcelRowLimits.configure(ExcelRowLimits.DEFAULT_MAX_EXPORT_ROWS, 2);
        try {
            assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesMaterialImportVo.class,
                List.of(row("M1", "成品", "台"), row("M2", "成品", "台"), row("M3", "成品", "台")))))
                .hasMessageContaining("导入数据超过 2 行");
        } finally {
            ExcelRowLimits.configure(ExcelRowLimits.DEFAULT_MAX_EXPORT_ROWS, ExcelRowLimits.DEFAULT_MAX_IMPORT_ROWS);
        }
        verify(materialMapper, never()).selectList(any(Wrapper.class));
        verify(materialMapper, never()).insert(anyCollection());
    }

    @Test
    @DisplayName("没有数据或用错了模板时提示")
    void rejectsEmptyOrWrongTemplate() {
        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesMaterialImportVo.class, List.of())))
            .hasMessageContaining("导入文件中没有数据");

        MesDeviceImportVo device = new MesDeviceImportVo();
        device.setDeviceCode("D1");
        assertThatThrownBy(() -> service.importData(ExcelFiles.xlsx(MesDeviceImportVo.class, List.of(device))))
            .hasMessageContaining("缺少列：物料编码");
    }

}
