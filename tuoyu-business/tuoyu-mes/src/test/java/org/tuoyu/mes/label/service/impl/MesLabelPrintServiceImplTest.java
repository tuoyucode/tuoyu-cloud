package org.tuoyu.mes.label.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.mes.label.domain.bo.MesSnLabelBo;
import org.tuoyu.mes.label.domain.vo.MesLabelDataVo;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.mes.trace.domain.MesSn;
import org.tuoyu.mes.trace.mapper.MesSnMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("标签打印数据：数量上限、数据权限、不存在的对象整体拒绝、报废与缺少批次号")
class MesLabelPrintServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private final MesSnMapper snMapper = MesTestSupport.mapper(MesSnMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);

    private final MesLabelPrintServiceImpl service = new MesLabelPrintServiceImpl(snMapper, workorderMapper, materialMapper);

    {
        service.setClock(Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE));
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(material()));
    }

    private static MesMaterial material() {
        MesMaterial material = new MesMaterial();
        material.setMaterialId(50L);
        material.setMaterialCode("P-100");
        material.setMaterialName("控制器");
        material.setSpecification("V2");
        material.setUnit("台");
        return material;
    }

    private static MesWorkorder workorder(long id, String batchCode) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(id);
        workorder.setWorkorderCode("WO" + id);
        workorder.setMaterialId(50L);
        workorder.setBatchCode(batchCode);
        workorder.setPlanQty(new BigDecimal("120"));
        return workorder;
    }

    private static MesSn sn(long id, long workorderId, String status) {
        MesSn sn = new MesSn();
        sn.setSnId(id);
        sn.setSnCode("SN" + id);
        sn.setWorkorderId(workorderId);
        sn.setMaterialId(50L);
        sn.setBatchCode("B" + workorderId);
        sn.setSnStatus(status);
        return sn;
    }

    private static MesSnLabelBo snIds(Long... ids) {
        MesSnLabelBo bo = new MesSnLabelBo();
        bo.setSnIds(List.of(ids));
        return bo;
    }

    @Test
    @DisplayName("勾选的序列号按勾选顺序返回，带产品、工单与生产日期（实际开工日期）")
    void snLabelsInSelectedOrder() {
        when(snMapper.selectByIds(anyCollection())).thenReturn(List.of(sn(1, 7, "created"), sn(2, 7, "completed")));
        MesWorkorder workorder = workorder(7, "B7");
        workorder.setActualStartTime(LocalDateTime.of(2026, 11, 3, 9, 0));
        doReturn(List.of(workorder)).when(workorderMapper).selectByIdsInScope(anyCollection());

        List<MesLabelDataVo> labels = service.snLabels(snIds(2L, 1L, 2L));

        assertThat(labels).extracting(MesLabelDataVo::getBarcode).containsExactly("SN2", "SN1");
        MesLabelDataVo first = labels.get(0);
        assertThat(first.getId()).isEqualTo(2L);
        assertThat(first.getSnCode()).isEqualTo("SN2");
        assertThat(first.getBatchCode()).isEqualTo("B7");
        assertThat(first.getWorkorderCode()).isEqualTo("WO7");
        assertThat(first.getMaterialCode()).isEqualTo("P-100");
        assertThat(first.getMaterialName()).isEqualTo("控制器");
        assertThat(first.getSpecification()).isEqualTo("V2");
        assertThat(first.getProductionDate()).isEqualTo(LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("工单不在数据权限内：它的序列号按不存在处理，整体拒绝")
    void snOutsideDataScopeIsRejected() {
        when(snMapper.selectByIds(anyCollection())).thenReturn(List.of(sn(1, 7, "created"), sn(2, 8, "created")));
        doReturn(List.of(workorder(7, "B7"))).when(workorderMapper).selectByIdsInScope(anyCollection());

        assertThatThrownBy(() -> service.snLabels(snIds(1L, 2L))).hasMessageContaining("有 1 个序列号不存在或无权访问");
    }

    @Test
    @DisplayName("有不存在的序列号时整体拒绝")
    void missingSnIsRejected() {
        when(snMapper.selectByIds(anyCollection())).thenReturn(List.of(sn(1, 7, "created")));
        doReturn(List.of(workorder(7, "B7"))).when(workorderMapper).selectByIdsInScope(anyCollection());

        assertThatThrownBy(() -> service.snLabels(snIds(1L, 404L))).hasMessageContaining("不存在或无权访问");
    }

    @Test
    @DisplayName("已报废的序列号不能打印")
    void scrappedSnIsRejected() {
        when(snMapper.selectByIds(anyCollection())).thenReturn(List.of(sn(1, 7, "scrapped")));
        doReturn(List.of(workorder(7, "B7"))).when(workorderMapper).selectByIdsInScope(anyCollection());

        assertThatThrownBy(() -> service.snLabels(snIds(1L))).hasMessageContaining("已报废的序列号不能打印标签：SN1");
    }

    @Test
    @DisplayName("一次最多 500 个；勾选与按工单只能选一种；都没有时提示选择")
    void limitsAndArguments() {
        MesSnLabelBo many = new MesSnLabelBo();
        many.setSnIds(new ArrayList<>(LongStream.rangeClosed(1, 501).boxed().toList()));
        assertThatThrownBy(() -> service.snLabels(many)).hasMessageContaining("一次最多打印 500 个标签");
        verify(snMapper, never()).selectByIds(anyCollection());

        MesSnLabelBo both = snIds(1L);
        both.setWorkorderId(7L);
        assertThatThrownBy(() -> service.snLabels(both)).hasMessageContaining("只能选一种");

        assertThatThrownBy(() -> service.snLabels(new MesSnLabelBo())).hasMessageContaining("请选择");
    }

    @Test
    @DisplayName("按工单打印：工单须在数据权限内；超过 500 个时提示勾选后分批打印；只打印未报废的序列号")
    void snLabelsByWorkorder() {
        MesSnLabelBo bo = new MesSnLabelBo();
        bo.setWorkorderId(7L);
        doReturn(null).when(workorderMapper).selectByIdInScope(7L);
        assertThatThrownBy(() -> service.snLabels(bo)).hasMessageContaining("工单不存在");

        doReturn(workorder(7, "B7")).when(workorderMapper).selectByIdInScope(7L);
        when(snMapper.selectCount(any())).thenReturn(501L);
        assertThatThrownBy(() -> service.snLabels(bo)).hasMessageContaining("有 501 个序列号").hasMessageContaining("分批打印");

        when(snMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.snLabels(bo)).hasMessageContaining("还没有可以打印的序列号");

        when(snMapper.selectCount(any())).thenReturn(2L);
        when(snMapper.selectList(any())).thenReturn(List.of(sn(1, 7, "created"), sn(2, 7, "completed")));
        List<MesLabelDataVo> labels = service.snLabels(bo);
        assertThat(labels).extracting(MesLabelDataVo::getSnCode).containsExactly("SN1", "SN2");
        assertThat(labels.get(0).getProductionDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("批次标签：每个工单一个，条码为批次号，带计划数与单位")
    void batchLabels() {
        doReturn(List.of(workorder(8, "B8"), workorder(7, "B7"))).when(workorderMapper).selectByIdsInScope(anyCollection());

        List<MesLabelDataVo> labels = service.batchLabels(List.of(7L, 8L));

        assertThat(labels).extracting(MesLabelDataVo::getBarcode).containsExactly("B7", "B8");
        assertThat(labels.get(0).getQuantity()).isEqualByComparingTo("120");
        assertThat(labels.get(0).getUnit()).isEqualTo("台");
        assertThat(labels.get(0).getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("批次标签：工单不存在或不在数据权限内、没有批次号、超过上限时拒绝")
    void batchLabelsRejected() {
        doReturn(List.of(workorder(7, "B7"))).when(workorderMapper).selectByIdsInScope(anyCollection());
        assertThatThrownBy(() -> service.batchLabels(List.of(7L, 8L))).hasMessageContaining("有 1 个工单不存在或无权访问");

        doReturn(List.of(workorder(7, " "))).when(workorderMapper).selectByIdsInScope(anyCollection());
        assertThatThrownBy(() -> service.batchLabels(List.of(7L))).hasMessageContaining("没有批次号").hasMessageContaining("WO7");

        assertThatThrownBy(() -> service.batchLabels(LongStream.rangeClosed(1, 501).boxed().toList()))
            .hasMessageContaining("一次最多打印 500 个标签");
        assertThatThrownBy(() -> service.batchLabels(List.of())).hasMessageContaining("请选择");
    }

    @Test
    @DisplayName("提示中最多列出 5 个编号")
    void codesInMessage() {
        assertThat(MesLabelPrintServiceImpl.codes(List.of("A", "B"))).isEqualTo("A、B");
        assertThat(MesLabelPrintServiceImpl.codes(List.of("1", "2", "3", "4", "5", "6"))).isEqualTo("1、2、3、4、5 等 6 个");
    }

}
