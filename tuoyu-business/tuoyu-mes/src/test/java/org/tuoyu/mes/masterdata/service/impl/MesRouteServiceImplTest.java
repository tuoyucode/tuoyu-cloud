package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.masterdata.domain.MesRoute;
import org.tuoyu.mes.masterdata.domain.MesRouteOperation;
import org.tuoyu.mes.masterdata.domain.bo.MesRouteBo;
import org.tuoyu.mes.masterdata.domain.bo.MesRouteOperationBo;
import org.tuoyu.mes.masterdata.domain.vo.MesRouteVo;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteMapper;
import org.tuoyu.mes.masterdata.mapper.MesRouteOperationMapper;
import org.tuoyu.mes.plan.mapper.MesWorkorderMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.math.BigDecimal;
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

@DisplayName("工艺路线：工序校验、默认版本唯一与已被工单使用时的限制")
class MesRouteServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesRouteMapper routeMapper = MesTestSupport.mapper(MesRouteMapper.class);
    private final MesRouteOperationMapper operationMapper = MesTestSupport.mapper(MesRouteOperationMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);

    private final MesRouteServiceImpl service = new MesRouteServiceImpl(routeMapper, operationMapper, materialMapper,
        workorderMapper);

    private static MesRouteOperationBo op(int seqNo, String code) {
        MesRouteOperationBo op = new MesRouteOperationBo();
        op.setSeqNo(seqNo);
        op.setOperationCode(code);
        op.setOperationName("工序" + code);
        return op;
    }

    private static MesRouteOperation saved(int seqNo, String code) {
        MesRouteOperation op = new MesRouteOperation();
        op.setOperationId((long) seqNo);
        op.setRouteId(30L);
        op.setSeqNo(seqNo);
        op.setOperationCode(code);
        op.setOperationName("工序" + code);
        op.setStandardMinutes(new BigDecimal("0.0000"));
        op.setFirstInspection("N");
        return op;
    }

    private static MesRouteBo bo(MesRouteOperationBo... operations) {
        MesRouteBo bo = new MesRouteBo();
        bo.setMaterialId(1L);
        bo.setRouteCode("R01");
        bo.setRouteName("标准路线");
        bo.setRouteVersion("V1");
        bo.setOperations(new ArrayList<>(List.of(operations)));
        return bo;
    }

    private static MesRoute route(long id, long materialId) {
        MesRoute route = new MesRoute();
        route.setRouteId(id);
        route.setMaterialId(materialId);
        route.setRouteCode("R" + id);
        route.setRouteName("标准路线");
        route.setRouteVersion("V1");
        route.setIsDefault("N");
        return route;
    }

    private void product() {
        when(materialMapper.selectById(1L)).thenReturn(MesMaterialServiceImplTest.material(1L, "product"));
    }

    @Test
    @DisplayName("新增：工序按顺序保存并补齐默认值；设为默认时清除同产品的其他默认版本")
    @SuppressWarnings("unchecked")
    void insertSortsOperations() {
        product();
        when(routeMapper.insert(any(MesRoute.class))).thenAnswer(invocation -> {
            invocation.<MesRoute>getArgument(0).setRouteId(30L);
            return 1;
        });
        MesRouteBo bo = bo(op(20, "B"), op(10, "A"));
        bo.setIsDefault("Y");

        service.insertByBo(bo);

        ArgumentCaptor<Collection<MesRouteOperation>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(operationMapper).insert(captor.capture());
        assertThat(captor.getValue()).extracting(MesRouteOperation::getSeqNo).containsExactly(10, 20);
        assertThat(captor.getValue()).extracting(MesRouteOperation::getRouteId).containsOnly(30L);
        assertThat(captor.getValue()).extracting(MesRouteOperation::getFirstInspection).containsOnly("N");
        verify(routeMapper).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("至少一道工序，工序顺序大于 0 且不重复")
    void rejectsInvalidOperations() {
        product();
        assertThatThrownBy(() -> service.insertByBo(bo())).hasMessageContaining("至少");
        assertThatThrownBy(() -> service.insertByBo(bo(op(10, "A"), op(10, "B")))).hasMessageContaining("重复");
        assertThatThrownBy(() -> service.insertByBo(bo(op(0, "A")))).hasMessageContaining("大于 0");
        verify(routeMapper, never()).insert(any(MesRoute.class));
    }

    @Test
    @DisplayName("产品必须是成品或半成品；编码不能重复")
    void rejectsWrongProductOrCode() {
        when(materialMapper.selectById(1L)).thenReturn(MesMaterialServiceImplTest.material(1L, "aux"));
        assertThatThrownBy(() -> service.insertByBo(bo(op(10, "A")))).hasMessageContaining("不是成品或半成品");

        when(routeMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.insertByBo(bo(op(10, "A")))).hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("已被工单使用的工艺路线，修改工序时拒绝并提示新建版本")
    void rejectsOperationChangeWhenUsed() {
        product();
        when(routeMapper.selectById(30L)).thenReturn(route(30L, 1L));
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        when(operationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(saved(10, "A")));
        MesRouteBo bo = bo(op(10, "A"), op(20, "B"));
        bo.setRouteId(30L);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessage("已有工单使用该工艺路线，请新建版本");
        verify(routeMapper, never()).updateById(any(MesRoute.class));
    }

    @Test
    @DisplayName("已被工单使用的工艺路线，工序不变时只改表头，保留原工序")
    void updatesHeaderWhenUsed() {
        product();
        when(routeMapper.selectById(30L)).thenReturn(route(30L, 1L));
        when(routeMapper.updateById(any(MesRoute.class))).thenReturn(1);
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        when(operationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(saved(10, "A")));
        MesRouteBo bo = bo(op(10, "A"));
        bo.setRouteId(30L);
        bo.setRouteName("标准路线（改名）");

        service.updateByBo(bo);

        verify(routeMapper).updateById(any(MesRoute.class));
        verify(operationMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("已被工单使用的工艺路线不能更换产品")
    void rejectsProductChangeWhenUsed() {
        product();
        when(routeMapper.selectById(30L)).thenReturn(route(30L, 2L));
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        when(operationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(saved(10, "A")));
        MesRouteBo bo = bo(op(10, "A"));
        bo.setRouteId(30L);

        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("新建版本");
    }

    @Test
    @DisplayName("未被使用的工艺路线修改时整体替换工序")
    void replacesOperationsWhenUnused() {
        product();
        when(routeMapper.selectById(30L)).thenReturn(route(30L, 1L));
        when(routeMapper.updateById(any(MesRoute.class))).thenReturn(1);
        MesRouteBo bo = bo(op(10, "A"), op(20, "B"));
        bo.setRouteId(30L);

        service.updateByBo(bo);

        verify(operationMapper).delete(any(Wrapper.class));
        verify(operationMapper).insert(anyCollection());
    }

    @Test
    @DisplayName("比较工序时忽略标准工时的小数位与空的工位类型")
    void sameOperationsIgnoresScale() {
        MesRouteOperation a = saved(10, "A");
        MesRouteOperation b = saved(10, "A");
        b.setStandardMinutes(BigDecimal.ZERO);
        b.setStationType("");

        assertThat(MesRouteServiceImpl.sameOperations(List.of(a), List.of(b))).isTrue();
        b.setFirstInspection("Y");
        assertThat(MesRouteServiceImpl.sameOperations(List.of(a), List.of(b))).isFalse();
        assertThat(MesRouteServiceImpl.sameOperations(List.of(a), List.of())).isFalse();
    }

    @Test
    @DisplayName("被工单引用的工艺路线不能删除；删除时连同工序")
    void deleteChecks() {
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(30L))).hasMessageContaining("工单");

        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(routeMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteByIds(List.of(30L));
        verify(operationMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("详情带工序；下拉选项名称为“名称 版本”")
    void queries() {
        when(routeMapper.selectById(30L)).thenReturn(route(30L, 1L));
        when(operationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(saved(10, "A")));
        when(routeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(route(30L, 1L)));
        when(routeMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(route(30L, 1L)));
        when(materialMapper.selectByIds(anyCollection()))
            .thenReturn(List.of(MesMaterialServiceImplTest.material(1L, "product")));

        MesRouteVo vo = service.queryById(30L);
        assertThat(vo.getOperations()).singleElement().extracting("operationCode").isEqualTo("A");
        assertThat(vo.getMaterialName()).isEqualTo("物料1");
        assertThat(service.options(1L)).singleElement().satisfies(option -> {
            assertThat(option.getName()).isEqualTo("标准路线 V1");
            assertThat(option.getParentId()).isEqualTo(1L);
        });
        assertThat(service.queryList(new MesRouteBo())).hasSize(1);
        assertThat(service.queryPageList(new MesRouteBo(), new PageParam()).getItems()).hasSize(1);
        assertThat(service.countList(new MesRouteBo())).isZero();
    }

    @Test
    @DisplayName("详情带“是否已被工单使用”，供界面在已使用时只允许改表头")
    void detailTellsWhetherUsed() {
        when(routeMapper.selectById(30L)).thenReturn(route(30L, 1L));
        when(operationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(saved(10, "A")));

        assertThat(service.queryById(30L).getUsed()).isFalse();

        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThat(service.queryById(30L).getUsed()).isTrue();
    }

    @Test
    @DisplayName("编码按不区分大小写校验唯一；并发写入撞上唯一索引时给出相同的提示")
    @SuppressWarnings("unchecked")
    void codeUniqueIgnoringCase() {
        product();
        when(routeMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.insertByBo(bo(op(10, "A")))).hasMessage("工艺路线编码“R01”已存在");
        ArgumentCaptor<Wrapper<MesRoute>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(routeMapper).exists(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("lower(route_code) = lower(");

        when(routeMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(routeMapper.insert(any(MesRoute.class))).thenThrow(new DuplicateKeyException("uk_route_code"));
        assertThatThrownBy(() -> service.insertByBo(bo(op(10, "A")))).hasMessage("工艺路线编码“R01”已存在");

        when(routeMapper.selectById(30L)).thenReturn(route(30L, 1L));
        when(routeMapper.updateById(any(MesRoute.class))).thenThrow(new DuplicateKeyException("uk_route_code"));
        MesRouteBo update = bo(op(10, "A"));
        update.setRouteId(30L);
        assertThatThrownBy(() -> service.updateByBo(update)).hasMessage("工艺路线编码“R01”已存在");
    }

}
