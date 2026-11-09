package org.tuoyu.mes.trace.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.trace.domain.bo.MesCodeRuleBo;
import org.tuoyu.mes.trace.domain.vo.MesCodeRuleVo;

import java.util.Collection;

/**
 * 编码规则：单据编号、批次号与序列号的格式
 *
 * @author tuoyu
 */
public interface IMesCodeRuleService {

    /**
     * 查询编码规则，不存在时抛出业务异常
     */
    MesCodeRuleVo queryById(Long ruleId);

    PageResult<MesCodeRuleVo> queryPageList(MesCodeRuleBo bo, PageParam pageParam);

    /**
     * 按规则预览下一个编号的格式（不占用流水号）；规则停用时为默认格式
     */
    String preview(Long ruleId);

    void insertByBo(MesCodeRuleBo bo);

    /**
     * 修改规则：规则编码创建后不能修改（流水号按规则编码保存）
     */
    void updateByBo(MesCodeRuleBo bo);

    /**
     * 删除规则；内置规则删除后对应单据按默认格式生成编号
     */
    void deleteByIds(Collection<Long> ids);

}
