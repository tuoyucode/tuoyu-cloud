package org.tuoyu.mes.label.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.label.domain.bo.MesLabelTemplateBo;
import org.tuoyu.mes.label.domain.vo.MesLabelTemplateVo;

import java.util.Collection;
import java.util.List;

/**
 * 标签模板
 *
 * @author tuoyu
 */
public interface IMesLabelTemplateService {

    /**
     * 查询标签模板，不存在时抛出业务异常
     */
    MesLabelTemplateVo queryById(Long labelTemplateId);

    PageResult<MesLabelTemplateVo> queryPageList(MesLabelTemplateBo bo, PageParam pageParam);

    /**
     * 打印时可选的模板：适用于该对象的启用模板，默认模板在最前
     *
     * @param targetType 适用对象（LabelTarget）
     */
    List<MesLabelTemplateVo> options(String targetType);

    /**
     * 新增；设为默认时，同一对象的其他模板取消默认
     */
    void insertByBo(MesLabelTemplateBo bo);

    /**
     * 修改；设为默认时，同一对象的其他模板取消默认
     */
    void updateByBo(MesLabelTemplateBo bo);

    void deleteByIds(Collection<Long> ids);

}
