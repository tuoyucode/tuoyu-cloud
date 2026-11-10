package org.tuoyu.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.code.CodeRules;
import org.tuoyu.mes.common.code.IMesCodeService;
import org.tuoyu.mes.common.enums.BizType;
import org.tuoyu.mes.common.statuslog.IMesStatusLogService;
import org.tuoyu.mes.masterdata.domain.MesMaterial;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.plan.domain.MesWorkorder;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;
import org.tuoyu.mes.trace.domain.MesSn;
import org.tuoyu.mes.trace.domain.bo.MesSnBo;
import org.tuoyu.mes.trace.domain.bo.MesSnGenerateBo;
import org.tuoyu.mes.trace.domain.vo.MesSnVo;
import org.tuoyu.mes.trace.mapper.MesSnMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("产品序列号：生成条件、数量上限与报废")
class MesSnServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesSnMapper snMapper = MesTestSupport.mapper(MesSnMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final IMesCodeService codeService = mock(IMesCodeService.class);
    private final IMesStatusLogService statusLogService = mock(IMesStatusLogService.class);

    private final MesSnServiceImpl service = new MesSnServiceImpl(snMapper, workorderMapper, materialMapper, codeService,
        statusLogService);

    private final UserContextProvider previous = MesTestSupport.loginAs(7L);

    @AfterEach
    void restore() {
        MesTestSupport.restore(previous);
    }

    private static MesWorkorder workorder(String status, String planQty) {
        MesWorkorder workorder = new MesWorkorder();
        workorder.setWorkorderId(1L);
        workorder.setWorkorderCode("WO001");
        workorder.setMaterialId(10L);
        workorder.setBatchCode("B261108001");
        workorder.setPlanQty(new BigDecimal(planQty));
        workorder.setWorkorderStatus(status);
        return workorder;
    }

    private static MesMaterial product(String traceMode) {
        MesMaterial material = new MesMaterial();
        material.setMaterialId(10L);
        material.setMaterialCode("P01");
        material.setMaterialName("控制器");
        material.setTraceMode(traceMode);
        return material;
    }

    private static MesSnGenerateBo generate(int count) {
        MesSnGenerateBo bo = new MesSnGenerateBo();
        bo.setWorkorderId(1L);
        bo.setCount(count);
        return bo;
    }

    private static MesSn sn(String status) {
        MesSn sn = new MesSn();
        sn.setSnId(5L);
        sn.setSnCode("SN261108000001");
        sn.setWorkorderId(1L);
        sn.setMaterialId(10L);
        sn.setSnStatus(status);
        return sn;
    }

    @Test
    @DisplayName("按工单批量生成：编号来自 SN 规则，带工单的产品与批次号，状态为已生成")
    @SuppressWarnings("unchecked")
    void generates() {
        when(workorderMapper.selectOne(any(Wrapper.class))).thenReturn(workorder("in_progress", "10"));
        when(materialMapper.selectById(10L)).thenReturn(product("sn"));
        when(snMapper.selectCount(any(Wrapper.class))).thenReturn(8L);
        when(codeService.next(CodeRules.SN, 2)).thenReturn(List.of("SN1", "SN2"));

        service.generate(generate(2));

        ArgumentCaptor<Collection<MesSn>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(snMapper).insert(captor.capture());
        List<MesSn> rows = new ArrayList<>(captor.getValue());
        assertThat(rows).extracting(MesSn::getSnCode).containsExactly("SN1", "SN2");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getWorkorderId()).isEqualTo(1L);
            assertThat(row.getMaterialId()).isEqualTo(10L);
            assertThat(row.getBatchCode()).isEqualTo("B261108001");
            assertThat(row.getSnStatus()).isEqualTo("created");
        });
    }

    @Test
    @DisplayName("未报废的序列号加本次数量超过计划数时拒绝，并提示还能生成多少")
    void rejectsOverPlan() {
        when(workorderMapper.selectOne(any(Wrapper.class))).thenReturn(workorder("released", "10"));
        when(materialMapper.selectById(10L)).thenReturn(product("sn"));
        when(snMapper.selectCount(any(Wrapper.class))).thenReturn(8L);

        assertThatThrownBy(() -> service.generate(generate(3))).hasMessageContaining("最多还能生成 2 个");
        verify(codeService, never()).next(anyString(), anyInt());
    }

    @Test
    @DisplayName("数量必须在 1 ~ 10000 之间")
    void rejectsCount() {
        assertThatThrownBy(() -> service.generate(generate(0))).hasMessageContaining("1 ~ 10000");
        assertThatThrownBy(() -> service.generate(generate(10001))).hasMessageContaining("1 ~ 10000");
    }

    @Test
    @DisplayName("草稿工单不能生成序列号")
    void rejectsDraftWorkorder() {
        when(workorderMapper.selectOne(any(Wrapper.class))).thenReturn(workorder("draft", "10"));

        assertThatThrownBy(() -> service.generate(generate(1))).hasMessageContaining("草稿").hasMessageContaining("生成序列号");
    }

    @Test
    @DisplayName("产品不按序列号追溯时拒绝，并提示在物料中设置")
    void rejectsNonSnProduct() {
        when(workorderMapper.selectOne(any(Wrapper.class))).thenReturn(workorder("paused", "10"));
        when(materialMapper.selectById(10L)).thenReturn(product("batch"));

        assertThatThrownBy(() -> service.generate(generate(1))).hasMessageContaining("按序列号").hasMessageContaining("物料");
    }

    @Test
    @DisplayName("工单不存在时拒绝")
    void rejectsMissingWorkorder() {
        assertThatThrownBy(() -> service.generate(generate(1))).hasMessageContaining("工单不存在");
    }

    @Test
    @DisplayName("报废：带原状态条件更新并记录履历")
    void scraps() {
        when(snMapper.selectById(5L)).thenReturn(sn("completed"));
        when(snMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        service.scrap(5L, "外观损坏");

        verify(statusLogService).record(BizType.SN, 5L, "SN261108000001", "scrap", "completed", "scrapped", "外观损坏");
    }

    @Test
    @DisplayName("已报废的序列号不能再报废")
    void rejectsScrapTwice() {
        when(snMapper.selectById(5L)).thenReturn(sn("scrapped"));

        assertThatThrownBy(() -> service.scrap(5L, "重复")).hasMessageContaining("已报废");
    }

    @Test
    @DisplayName("状态已被其他操作改变时提示刷新")
    void scrapConcurrent() {
        when(snMapper.selectById(5L)).thenReturn(sn("created"));

        assertThatThrownBy(() -> service.scrap(5L, "损坏")).hasMessageContaining("状态已变化");
        verify(statusLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("报废必须填写原因")
    void scrapNeedsReason() {
        assertThatThrownBy(() -> service.scrap(5L, " ")).hasMessageContaining("原因");
    }

    @Test
    @DisplayName("列表批量填充工单编号与产品名称")
    @SuppressWarnings("unchecked")
    void listFillsNames() {
        when(snMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(sn("created")));
        when(workorderMapper.selectByIds(anyCollection())).thenReturn(List.of(workorder("released", "10")));
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(product("sn")));
        MesSnBo bo = new MesSnBo();
        bo.setWorkorderCode("WO");

        PageResult<MesSnVo> page = service.queryPageList(bo, new PageParam(1, 10));

        assertThat(page.getItems()).singleElement().satisfies(vo -> {
            assertThat(vo.getWorkorderCode()).isEqualTo("WO001");
            assertThat(vo.getMaterialName()).isEqualTo("控制器");
        });
    }

    @Test
    @DisplayName("履历只查存在的序列号")
    void logs() {
        assertThatThrownBy(() -> service.logs(5L)).hasMessageContaining("不存在");

        when(snMapper.selectById(5L)).thenReturn(sn("scrapped"));
        service.logs(5L);
        verify(statusLogService).list(eq(BizType.SN), eq(5L));
    }

}
