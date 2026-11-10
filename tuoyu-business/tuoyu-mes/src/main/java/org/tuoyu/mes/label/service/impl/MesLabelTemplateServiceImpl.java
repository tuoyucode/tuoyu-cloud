package org.tuoyu.mes.label.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.common.CodeEnum;
import org.tuoyu.mes.label.domain.MesLabelTemplate;
import org.tuoyu.mes.label.domain.bo.MesLabelTemplateBo;
import org.tuoyu.mes.label.domain.vo.MesLabelTemplateVo;
import org.tuoyu.mes.label.enums.BarcodeType;
import org.tuoyu.mes.label.enums.LabelField;
import org.tuoyu.mes.label.enums.LabelTarget;
import org.tuoyu.mes.label.mapper.MesLabelTemplateMapper;
import org.tuoyu.mes.label.service.IMesLabelTemplateService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 标签模板
 * <p>
 * 每种适用对象最多一个默认模板：保存默认模板时，在同一事务中取消同一对象其他模板的默认标志。
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@Service
public class MesLabelTemplateServiceImpl implements IMesLabelTemplateService {

    static final String DEFAULT = "1";

    static final String NOT_DEFAULT = "0";

    private final MesLabelTemplateMapper templateMapper;

    @Override
    public MesLabelTemplateVo queryById(Long labelTemplateId) {
        return toVo(requireTemplate(labelTemplateId));
    }

    @Override
    public PageResult<MesLabelTemplateVo> queryPageList(MesLabelTemplateBo bo, PageParam pageParam) {
        boolean defaultOrder = !pageParam.hasSort();
        Page<MesLabelTemplate> page = templateMapper.selectPage(pageParam.toPage(MesLabelTemplate.class),
            QueryBuilder.lambda(MesLabelTemplate.class)
                .likeIfText(MesLabelTemplate::getTemplateName, bo.getTemplateName())
                .eqIfText(MesLabelTemplate::getTargetType, bo.getTargetType())
                .eqIfText(MesLabelTemplate::getBarcodeType, bo.getBarcodeType())
                .eqIfText(MesLabelTemplate::getStatus, bo.getStatus())
                .orderByAsc(defaultOrder, MesLabelTemplate::getTargetType)
                .orderByDesc(defaultOrder, MesLabelTemplate::getDefaultFlag)
                .orderByAsc(defaultOrder, MesLabelTemplate::getLabelTemplateId)
                .build());
        PageResult<MesLabelTemplateVo> result = PageResults.of(page, MesLabelTemplateVo.class);
        List<MesLabelTemplate> records = page.getRecords();
        for (int i = 0; i < result.getItems().size() && i < records.size(); i++) {
            result.getItems().get(i).setFields(splitFields(records.get(i).getFieldKeys()));
        }
        return result;
    }

    @Override
    public List<MesLabelTemplateVo> options(String targetType) {
        CodeEnum.of(LabelTarget.class, targetType, "适用对象");
        return templateMapper.selectList(QueryBuilder.lambda(MesLabelTemplate.class)
                .eq(MesLabelTemplate::getTargetType, targetType)
                .eq(MesLabelTemplate::getStatus, SystemConstants.NORMAL)
                .orderByDesc(MesLabelTemplate::getDefaultFlag)
                .orderByAsc(MesLabelTemplate::getLabelTemplateId)
                .build())
            .stream().map(MesLabelTemplateServiceImpl::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertByBo(MesLabelTemplateBo bo) {
        MesLabelTemplate template = toEntity(bo);
        template.setLabelTemplateId(null);
        templateMapper.insert(template);
        bo.setLabelTemplateId(template.getLabelTemplateId());
        keepSingleDefault(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateByBo(MesLabelTemplateBo bo) {
        requireTemplate(bo.getLabelTemplateId());
        MesLabelTemplate template = toEntity(bo);
        template.setLabelTemplateId(bo.getLabelTemplateId());
        if (templateMapper.updateById(template) == 0) {
            throw new ServiceException("修改标签模板失败，标签模板不存在");
        }
        keepSingleDefault(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty() || templateMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除标签模板失败，标签模板不存在");
        }
    }

    /**
     * 校验并转换：对象、条码类型有效；字段适用于该对象且不重复；默认模板不能停用
     */
    static MesLabelTemplate toEntity(MesLabelTemplateBo bo) {
        LabelTarget target = CodeEnum.of(LabelTarget.class, bo.getTargetType(), "适用对象");
        CodeEnum.of(BarcodeType.class, bo.getBarcodeType(), "条码类型");
        List<String> fields = bo.getFields() == null ? List.of() : bo.getFields();
        if (fields.isEmpty()) {
            throw new ServiceException("请至少选择一个显示的字段");
        }
        if (fields.size() > MesLabelTemplateBo.MAX_FIELDS) {
            throw new ServiceException("显示的字段不能超过 " + MesLabelTemplateBo.MAX_FIELDS + " 个");
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String code : fields) {
            LabelField field = CodeEnum.of(LabelField.class, code, "显示的字段");
            if (!field.appliesTo(target)) {
                throw new ServiceException("“" + field.label() + "”不能用于" + target.label() + "标签");
            }
            if (!distinct.add(code)) {
                throw new ServiceException("显示的字段“" + field.label() + "”重复");
            }
        }
        String defaultFlag = bo.getDefaultFlag() == null ? NOT_DEFAULT : bo.getDefaultFlag();
        String status = bo.getStatus() == null ? SystemConstants.NORMAL : bo.getStatus();
        if (DEFAULT.equals(defaultFlag) && !SystemConstants.NORMAL.equals(status)) {
            throw new ServiceException("默认模板不能停用，请先取消默认");
        }
        MesLabelTemplate template = BeanMapper.map(bo, MesLabelTemplate.class);
        template.setTemplateName(StringUtils.trim(bo.getTemplateName()));
        template.setFieldKeys(String.join(",", distinct));
        template.setColumnGap(bo.getColumnGap() == null ? 0 : bo.getColumnGap());
        template.setDefaultFlag(defaultFlag);
        template.setStatus(status);
        return template;
    }

    /**
     * 保存的是默认模板时，取消同一对象其他模板的默认标志
     */
    private void keepSingleDefault(MesLabelTemplate template) {
        if (!DEFAULT.equals(template.getDefaultFlag())) {
            return;
        }
        templateMapper.update(null, Wrappers.<MesLabelTemplate>lambdaUpdate()
            .set(MesLabelTemplate::getDefaultFlag, NOT_DEFAULT)
            .eq(MesLabelTemplate::getTargetType, template.getTargetType())
            .eq(MesLabelTemplate::getDefaultFlag, DEFAULT)
            .ne(MesLabelTemplate::getLabelTemplateId, template.getLabelTemplateId()));
    }

    private MesLabelTemplate requireTemplate(Long labelTemplateId) {
        MesLabelTemplate template = labelTemplateId == null ? null : templateMapper.selectById(labelTemplateId);
        if (template == null) {
            throw new ServiceException("标签模板不存在");
        }
        return template;
    }

    static MesLabelTemplateVo toVo(MesLabelTemplate template) {
        MesLabelTemplateVo vo = BeanMapper.map(template, MesLabelTemplateVo.class);
        vo.setFields(splitFields(template.getFieldKeys()));
        return vo;
    }

    static List<String> splitFields(String keys) {
        List<String> fields = new ArrayList<>();
        if (StringUtils.isNotBlank(keys)) {
            for (String key : keys.split(",")) {
                if (!key.isBlank()) {
                    fields.add(key.trim());
                }
            }
        }
        return fields;
    }

}
