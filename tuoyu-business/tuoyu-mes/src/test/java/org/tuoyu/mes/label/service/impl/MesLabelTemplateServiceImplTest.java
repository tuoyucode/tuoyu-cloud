package org.tuoyu.mes.label.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.label.domain.MesLabelTemplate;
import org.tuoyu.mes.label.domain.bo.MesLabelTemplateBo;
import org.tuoyu.mes.label.domain.vo.MesLabelTemplateVo;
import org.tuoyu.mes.label.mapper.MesLabelTemplateMapper;
import org.tuoyu.mes.support.MesTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("标签模板：字段与对象的校验、每种对象只有一个默认模板、打印时的可选模板")
class MesLabelTemplateServiceImplTest {

    static {
        MesTestSupport.init();
    }

    private final MesLabelTemplateMapper templateMapper = MesTestSupport.mapper(MesLabelTemplateMapper.class);

    private final MesLabelTemplateServiceImpl service = new MesLabelTemplateServiceImpl(templateMapper);

    private static MesLabelTemplateBo bo() {
        MesLabelTemplateBo bo = new MesLabelTemplateBo();
        bo.setTemplateName("序列号 50×30");
        bo.setTargetType("sn");
        bo.setPaperWidth(50);
        bo.setPaperHeight(30);
        bo.setColumnCount(2);
        bo.setBarcodeType("code128");
        bo.setFields(List.of("snCode", "materialName", "productionDate"));
        return bo;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<MesLabelTemplate> capturedDefaultReset() {
        ArgumentCaptor<Wrapper<MesLabelTemplate>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(templateMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<MesLabelTemplate> update = (LambdaUpdateWrapper<MesLabelTemplate>) captor.getValue();
        update.getSqlSegment();
        return update;
    }

    @Test
    @DisplayName("新增默认模板：同一对象的其他默认模板取消默认（不含自己）")
    void defaultTemplateIsUniquePerTarget() {
        when(templateMapper.insert(any(MesLabelTemplate.class))).thenAnswer(invocation -> {
            invocation.<MesLabelTemplate>getArgument(0).setLabelTemplateId(5L);
            return 1;
        });
        MesLabelTemplateBo bo = bo();
        bo.setDefaultFlag("1");

        service.insertByBo(bo);

        ArgumentCaptor<MesLabelTemplate> saved = ArgumentCaptor.forClass(MesLabelTemplate.class);
        verify(templateMapper).insert(saved.capture());
        assertThat(saved.getValue().getFieldKeys()).isEqualTo("snCode,materialName,productionDate");
        assertThat(saved.getValue().getColumnGap()).isZero();
        LambdaUpdateWrapper<MesLabelTemplate> reset = capturedDefaultReset();
        assertThat(reset.getSqlSet()).contains("default_flag");
        assertThat(reset.getSqlSegment()).contains("target_type").contains("default_flag").contains("label_template_id <>");
        assertThat(reset.getParamNameValuePairs().values()).contains("0", "sn", "1", 5L);
    }

    @Test
    @DisplayName("修改成默认模板时同样取消其他默认；不是默认模板时不改动其他模板")
    void updateKeepsSingleDefault() {
        MesLabelTemplate stored = new MesLabelTemplate();
        stored.setLabelTemplateId(5L);
        when(templateMapper.selectById(5L)).thenReturn(stored);
        when(templateMapper.updateById(any(MesLabelTemplate.class))).thenReturn(1);
        MesLabelTemplateBo bo = bo();
        bo.setLabelTemplateId(5L);
        bo.setDefaultFlag("1");

        service.updateByBo(bo);
        capturedDefaultReset();

        clearInvocations(templateMapper);
        MesLabelTemplateBo plain = bo();
        plain.setLabelTemplateId(5L);
        service.updateByBo(plain);
        verify(templateMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("默认模板不能停用")
    void defaultTemplateCannotBeDisabled() {
        MesLabelTemplateBo bo = bo();
        bo.setDefaultFlag("1");
        bo.setStatus("1");

        assertThatThrownBy(() -> service.insertByBo(bo)).hasMessageContaining("默认模板不能停用");
    }

    @Test
    @DisplayName("字段必须适用于该对象，不能重复；条码类型、对象必须有效")
    void validatesFields() {
        MesLabelTemplateBo batch = bo();
        batch.setTargetType("batch");
        assertThatThrownBy(() -> MesLabelTemplateServiceImpl.toEntity(batch)).hasMessageContaining("不能用于批次标签");

        MesLabelTemplateBo duplicate = bo();
        duplicate.setFields(List.of("snCode", "snCode"));
        assertThatThrownBy(() -> MesLabelTemplateServiceImpl.toEntity(duplicate)).hasMessageContaining("重复");

        MesLabelTemplateBo unknown = bo();
        unknown.setFields(List.of("price"));
        assertThatThrownBy(() -> MesLabelTemplateServiceImpl.toEntity(unknown)).hasMessageContaining("显示的字段不正确");

        MesLabelTemplateBo empty = bo();
        empty.setFields(List.of());
        assertThatThrownBy(() -> MesLabelTemplateServiceImpl.toEntity(empty)).hasMessageContaining("至少选择");

        MesLabelTemplateBo barcode = bo();
        barcode.setBarcodeType("pdf417");
        assertThatThrownBy(() -> MesLabelTemplateServiceImpl.toEntity(barcode)).hasMessageContaining("条码类型不正确");

        MesLabelTemplateBo quantity = bo();
        quantity.setTargetType("batch");
        quantity.setFields(List.of("batchCode", "quantity"));
        assertThat(MesLabelTemplateServiceImpl.toEntity(quantity).getFieldKeys()).isEqualTo("batchCode,quantity");
    }

    @Test
    @DisplayName("打印时的可选模板：字段按顺序还原；对象无效时拒绝")
    void options() {
        MesLabelTemplate template = new MesLabelTemplate();
        template.setLabelTemplateId(5L);
        template.setTargetType("sn");
        template.setFieldKeys("snCode, materialName");
        when(templateMapper.selectList(any())).thenReturn(List.of(template));

        List<MesLabelTemplateVo> options = service.options("sn");

        assertThat(options).hasSize(1);
        assertThat(options.get(0).getFields()).containsExactly("snCode", "materialName");
        assertThatThrownBy(() -> service.options("pallet")).hasMessageContaining("适用对象不正确");
    }

    @Test
    @DisplayName("查询、删除不存在的模板时提示不存在；列表为空时返回空")
    void missingAndList() {
        assertThatThrownBy(() -> service.queryById(404L)).hasMessageContaining("不存在");
        assertThatThrownBy(() -> service.deleteByIds(List.of(404L))).hasMessageContaining("不存在");
        MesLabelTemplateBo update = bo();
        update.setLabelTemplateId(404L);
        assertThatThrownBy(() -> service.updateByBo(update)).hasMessageContaining("不存在");
        when(templateMapper.deleteByIds(anyCollection())).thenReturn(1);
        service.deleteByIds(List.of(5L));
        assertThat(service.queryPageList(new MesLabelTemplateBo(), new PageParam(1, 10)).getItems()).isEmpty();
    }

}
