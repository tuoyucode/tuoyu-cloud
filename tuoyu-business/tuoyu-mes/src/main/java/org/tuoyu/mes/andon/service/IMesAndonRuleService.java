package org.tuoyu.mes.andon.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.andon.domain.bo.MesAndonRuleBo;
import org.tuoyu.mes.andon.domain.vo.MesAndonRuleVo;

import java.util.Collection;

/**
 * 安灯升级规则：按异常类型、紧急程度配置响应与解决时限，超时后升级到哪个级别、提醒哪些人
 *
 * @author tuoyu
 */
public interface IMesAndonRuleService {

    /**
     * 查询升级规则，不存在时抛出业务异常
     */
    MesAndonRuleVo queryById(Long andonRuleId);

    PageResult<MesAndonRuleVo> queryPageList(MesAndonRuleBo bo, PageParam pageParam);

    /**
     * 新增：同一组异常类型与紧急程度只能有一条规则；提醒人员必须是当前租户中状态正常的用户
     */
    void insertByBo(MesAndonRuleBo bo);

    /**
     * 修改：规则变化后，未解决的安灯事件按新规则重新计算截止时间（已经升级过的时限不再重复升级）
     */
    void updateByBo(MesAndonRuleBo bo);

    void deleteByIds(Collection<Long> ids);

}
