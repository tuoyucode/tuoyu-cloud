package org.tuoyu.mes.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.MesBom;
import org.tuoyu.mes.masterdata.domain.MesBomItem;
import org.tuoyu.mes.masterdata.domain.bo.MesBomBo;
import org.tuoyu.mes.masterdata.domain.bo.MesBomItemBo;
import org.tuoyu.mes.masterdata.domain.vo.MesBomVo;
import org.tuoyu.mes.masterdata.mapper.MesBomItemMapper;
import org.tuoyu.mes.masterdata.mapper.MesBomMapper;
import org.tuoyu.mes.masterdata.mapper.MesMaterialMapper;
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

@DisplayName("物料清单：明细校验、默认版本唯一与被工单引用时的限制")
class MesBomServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesBomMapper bomMapper = MesTestSupport.mapper(MesBomMapper.class);
    private final MesBomItemMapper bomItemMapper = MesTestSupport.mapper(MesBomItemMapper.class);
    private final MesMaterialMapper materialMapper = MesTestSupport.mapper(MesMaterialMapper.class);
    private final MesWorkorderMapper workorderMapper = MesTestSupport.mapper(MesWorkorderMapper.class);

    private final MesBomServiceImpl service = new MesBomServiceImpl(bomMapper, bomItemMapper, materialMapper,
        workorderMapper);

    private static MesBomItemBo item(long materialId, String qty) {
        MesBomItemBo item = new MesBomItemBo();
        item.setMaterialId(materialId);
        item.setQty(new BigDecimal(qty));
        return item;
    }

    private static MesBomBo bo(MesBomItemBo... items) {
        MesBomBo bo = new MesBomBo();
        bo.setMaterialId(1L);
        bo.setBomCode("BOM01");
        bo.setBomVersion("V1");
        bo.setItems(new ArrayList<>(List.of(items)));
        return bo;
    }

    private void productAndParts() {
        when(materialMapper.selectById(1L)).thenReturn(MesMaterialServiceImplTest.material(1L, "product"));
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(
            MesMaterialServiceImplTest.material(2L, "raw"), MesMaterialServiceImplTest.material(3L, "raw")));
    }

    private static MesBom bom(long id, long materialId) {
        MesBom bom = new MesBom();
        bom.setBomId(id);
        bom.setMaterialId(materialId);
        bom.setBomCode("BOM" + id);
        bom.setBomVersion("V" + id);
        bom.setIsDefault("N");
        return bom;
    }

    @Test
    @DisplayName("新增：保存明细，补齐基准数量、损耗率与排序；非默认版本不改其他物料清单")
    @SuppressWarnings("unchecked")
    void insertSavesItems() {
        productAndParts();
        when(bomMapper.insert(any(MesBom.class))).thenAnswer(invocation -> {
            invocation.<MesBom>getArgument(0).setBomId(10L);
            return 1;
        });
        MesBomBo bo = bo(item(2L, "2"), item(3L, "0.5"));

        service.insertByBo(bo);

        ArgumentCaptor<MesBom> header = ArgumentCaptor.forClass(MesBom.class);
        verify(bomMapper).insert(header.capture());
        assertThat(header.getValue().getBaseQty()).isEqualByComparingTo("1");
        assertThat(header.getValue().getIsDefault()).isEqualTo("N");
        ArgumentCaptor<Collection<MesBomItem>> items = ArgumentCaptor.forClass(Collection.class);
        verify(bomItemMapper).insert(items.capture());
        assertThat(items.getValue()).extracting(MesBomItem::getBomId).containsOnly(10L);
        assertThat(items.getValue()).extracting(MesBomItem::getSortNo).containsExactly(1, 2);
        assertThat(items.getValue()).extracting(MesBomItem::getLossRate).allMatch(r -> r.signum() == 0);
        assertThat(bo.getBomId()).isEqualTo(10L);
        verify(bomMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("设为默认版本时，同一产品的其他默认版本取消默认")
    void defaultClearsOthers() {
        productAndParts();
        MesBomBo bo = bo(item(2L, "1"));
        bo.setIsDefault("Y");

        service.insertByBo(bo);

        verify(bomMapper).update(any(), any(Wrapper.class));
    }

    @Test
    @DisplayName("子件不能是产品本身，也不能重复")
    void rejectsInvalidItems() {
        when(materialMapper.selectById(1L)).thenReturn(MesMaterialServiceImplTest.material(1L, "product"));

        assertThatThrownBy(() -> service.insertByBo(bo(item(1L, "1")))).hasMessageContaining("产品本身");
        assertThatThrownBy(() -> service.insertByBo(bo(item(2L, "1"), item(2L, "3")))).hasMessageContaining("重复");
        verify(bomMapper, never()).insert(any(MesBom.class));
    }

    @Test
    @DisplayName("用量必须大于 0，损耗率在 0 ~ 1 之间，至少一个子件")
    void rejectsInvalidQuantities() {
        when(materialMapper.selectById(1L)).thenReturn(MesMaterialServiceImplTest.material(1L, "product"));

        assertThatThrownBy(() -> service.insertByBo(bo(item(2L, "0")))).hasMessageContaining("大于 0");
        MesBomItemBo lossy = item(2L, "1");
        lossy.setLossRate(new BigDecimal("1.5"));
        assertThatThrownBy(() -> service.insertByBo(bo(lossy))).hasMessageContaining("损耗率");
        assertThatThrownBy(() -> service.insertByBo(bo())).hasMessageContaining("至少");
    }

    @Test
    @DisplayName("产品必须是成品或半成品；子件物料必须存在")
    void rejectsWrongProductOrMissingParts() {
        when(materialMapper.selectById(1L)).thenReturn(MesMaterialServiceImplTest.material(1L, "raw"));
        assertThatThrownBy(() -> service.insertByBo(bo(item(2L, "1")))).hasMessageContaining("不是成品或半成品");

        when(materialMapper.selectById(1L)).thenReturn(MesMaterialServiceImplTest.material(1L, "semi"));
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of());
        assertThatThrownBy(() -> service.insertByBo(bo(item(2L, "1")))).hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("编码或同一产品的版本重复时拒绝")
    void rejectsDuplicateCodeOrVersion() {
        when(bomMapper.exists(any(Wrapper.class))).thenReturn(true, false, true);

        assertThatThrownBy(() -> service.insertByBo(bo(item(2L, "1")))).hasMessageContaining("编码");
        assertThatThrownBy(() -> service.insertByBo(bo(item(2L, "1")))).hasMessageContaining("版本");
    }

    @Test
    @DisplayName("修改：明细整体覆盖；已被工单使用时不能更换产品")
    void updateReplacesItems() {
        productAndParts();
        when(bomMapper.selectById(10L)).thenReturn(bom(10L, 1L));
        when(bomMapper.updateById(any(MesBom.class))).thenReturn(1);
        MesBomBo bo = bo(item(2L, "1"));
        bo.setBomId(10L);

        service.updateByBo(bo);

        verify(bomItemMapper).delete(any(Wrapper.class));

        when(bomMapper.selectById(10L)).thenReturn(bom(10L, 5L));
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.updateByBo(bo)).hasMessageContaining("不能更换产品");
    }

    @Test
    @DisplayName("被工单引用的物料清单不能删除；删除时连同明细")
    void deleteChecks() {
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByIds(List.of(10L))).hasMessageContaining("工单");
        verify(bomMapper, never()).deleteByIds(anyCollection());

        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(false);
        when(bomMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteByIds(List.of(10L));
        verify(bomItemMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("详情带明细的物料编码、名称与单位；列表带产品名称")
    void queryDetail() {
        when(bomMapper.selectById(10L)).thenReturn(bom(10L, 1L));
        MesBomItem part = new MesBomItem();
        part.setItemId(20L);
        part.setBomId(10L);
        part.setMaterialId(2L);
        part.setQty(BigDecimal.TEN);
        when(bomItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(part));
        when(materialMapper.selectByIds(anyCollection())).thenReturn(List.of(
            MesMaterialServiceImplTest.material(1L, "product"), MesMaterialServiceImplTest.material(2L, "raw")));

        MesBomVo vo = service.queryById(10L);

        assertThat(vo.getMaterialName()).isEqualTo("物料1");
        assertThat(vo.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMaterialCode()).isEqualTo("M2");
            assertThat(item.getUnit()).isEqualTo("个");
        });

        when(bomMapper.selectList(any(IPage.class), any(Wrapper.class))).thenReturn(List.of(bom(10L, 1L)));
        assertThat(service.queryPageList(new MesBomBo(), new PageParam()).getItems())
            .singleElement().extracting(MesBomVo::getMaterialCode).isEqualTo("M1");
    }

    @Test
    @DisplayName("下拉选项：名称为版本，上级为产品")
    void options() {
        when(bomMapper.selectList(any(Wrapper.class))).thenReturn(List.of(bom(10L, 1L)));

        List<MesOptionVo> options = service.options(1L);

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.getName()).isEqualTo("V10");
            assertThat(option.getParentId()).isEqualTo(1L);
        });
        assertThat(service.queryList(new MesBomBo())).hasSize(1);
        assertThat(service.countList(new MesBomBo())).isZero();
    }

    private static MesBomItem savedItem(long materialId, String qty, int sortNo) {
        MesBomItem item = new MesBomItem();
        item.setItemId(100L + materialId);
        item.setBomId(10L);
        item.setMaterialId(materialId);
        item.setQty(new BigDecimal(qty));
        item.setLossRate(new BigDecimal("0.0000"));
        item.setSortNo(sortNo);
        return item;
    }

    private MesBomBo usedBom(MesBomItemBo... items) {
        productAndParts();
        MesBom current = bom(10L, 1L);
        current.setBaseQty(new BigDecimal("1.0000"));
        when(bomMapper.selectById(10L)).thenReturn(current);
        when(bomMapper.updateById(any(MesBom.class))).thenReturn(1);
        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        when(bomItemMapper.selectList(any(Wrapper.class))).thenReturn(List.of(savedItem(2L, "2", 1), savedItem(3L, "0.5", 2)));
        MesBomBo bo = bo(items);
        bo.setBomId(10L);
        return bo;
    }

    @Test
    @DisplayName("已被工单使用：明细不变时只改表头，保留原明细")
    void usedBomUpdatesHeaderOnly() {
        MesBomBo bo = usedBom(item(2L, "2.00"), item(3L, "0.5"));
        bo.setRemark("改了备注");
        bo.setBomVersion("V1-修订");

        service.updateByBo(bo);

        verify(bomMapper).updateById(any(MesBom.class));
        verify(bomItemMapper, never()).delete(any(Wrapper.class));
        verify(bomItemMapper, never()).insert(anyCollection());
    }

    @Test
    @DisplayName("已被工单使用：改用量、损耗率、子件或基准数量时拒绝并提示新建版本")
    void usedBomRejectsItemChanges() {
        assertThatThrownBy(() -> service.updateByBo(usedBom(item(2L, "3"), item(3L, "0.5"))))
            .hasMessageContaining("新建版本");

        MesBomItemBo lossy = item(3L, "0.5");
        lossy.setLossRate(new BigDecimal("0.05"));
        assertThatThrownBy(() -> service.updateByBo(usedBom(item(2L, "2"), lossy))).hasMessageContaining("新建版本");

        assertThatThrownBy(() -> service.updateByBo(usedBom(item(2L, "2")))).hasMessageContaining("新建版本");

        assertThatThrownBy(() -> service.updateByBo(usedBom(item(2L, "2"), item(4L, "0.5"))))
            .hasMessageContaining("新建版本");

        MesBomBo base = usedBom(item(2L, "2"), item(3L, "0.5"));
        base.setBaseQty(BigDecimal.TEN);
        assertThatThrownBy(() -> service.updateByBo(base)).hasMessageContaining("新建版本");

        verify(bomMapper, never()).updateById(any(MesBom.class));
        verify(bomItemMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("比较明细时按子件物料对应：顺序与排序号不参与比较，未填的损耗率按 0")
    void sameItemsComparesByMaterial() {
        List<MesBomItem> existing = List.of(savedItem(2L, "2", 10), savedItem(3L, "0.5", 20));
        MesBomItemBo second = item(3L, "0.50");
        second.setSortNo(1);
        assertThat(MesBomServiceImpl.sameItems(existing, List.of(second, item(2L, "2")))).isTrue();
        assertThat(MesBomServiceImpl.sameItems(existing, List.of(item(2L, "2"), item(3L, "0.5")))).isTrue();
        assertThat(MesBomServiceImpl.sameItems(existing, List.of(item(2L, "2"), item(3L, "0.6")))).isFalse();
        MesBomItemBo remarked = item(3L, "0.5");
        remarked.setRemark("加备注");
        assertThat(MesBomServiceImpl.sameItems(existing, List.of(item(2L, "2"), remarked))).isFalse();
    }

    @Test
    @DisplayName("详情带“是否已被工单使用”")
    void detailTellsWhetherUsed() {
        when(bomMapper.selectById(10L)).thenReturn(bom(10L, 1L));

        assertThat(service.queryById(10L).getUsed()).isFalse();

        when(workorderMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThat(service.queryById(10L).getUsed()).isTrue();
    }

    @Test
    @DisplayName("编码按不区分大小写校验唯一；并发写入撞上唯一索引时给出相同的提示")
    @SuppressWarnings("unchecked")
    void codeUniqueIgnoringCase() {
        when(bomMapper.exists(any(Wrapper.class))).thenReturn(true);
        assertThatThrownBy(() -> service.insertByBo(bo(item(2L, "1")))).hasMessage("BOM 编码“BOM01”已存在");
        ArgumentCaptor<Wrapper<MesBom>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(bomMapper).exists(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("lower(bom_code) = lower(");

        when(bomMapper.exists(any(Wrapper.class))).thenReturn(false);
        productAndParts();
        when(bomMapper.insert(any(MesBom.class))).thenThrow(new DuplicateKeyException("uk_bom_code"));
        assertThatThrownBy(() -> service.insertByBo(bo(item(2L, "1")))).hasMessage("BOM 编码“BOM01”已存在");
        verify(bomItemMapper, never()).insert(anyCollection());
    }

}
