package org.tuoyu.workflow.service;

import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.system.api.domain.bo.RemoteTaskAssigneeBo;
import org.tuoyu.system.api.domain.vo.RemoteTaskAssigneeVo;
import org.tuoyu.workflow.domain.bo.FlowSpelBo;
import org.tuoyu.workflow.domain.vo.FlowSpelVo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 流程spel表达式定义Service接口
 *
 * @author Michelle.Chung
 * @date 2025-07-04
 */
public interface IFlwSpelService {

    /**
     * 查询流程spel表达式定义
     *
     * @param id 主键
     * @return 流程spel表达式定义
     */
    FlowSpelVo queryById(Long id);

    /**
     * 分页查询流程spel表达式定义列表
     *
     * @param bo        查询条件
     * @param pageParam 分页参数
     * @return 流程spel表达式定义分页列表
     */
    PageResult<FlowSpelVo> queryPageList(FlowSpelBo bo, PageParam pageParam);

    /**
     * 查询符合条件的流程spel表达式定义列表
     *
     * @param bo 查询条件
     * @return 流程spel表达式定义列表
     */
    List<FlowSpelVo> queryList(FlowSpelBo bo);

    /**
     * 新增流程spel表达式定义：表达式重复时抛出业务异常
     *
     * @param bo 流程spel表达式定义
     */
    void insertByBo(FlowSpelBo bo);

    /**
     * 修改流程spel表达式定义：表达式重复或记录不存在时抛出业务异常
     *
     * @param bo 流程spel表达式定义
     */
    void updateByBo(FlowSpelBo bo);

    /**
     * 校验并批量删除流程spel表达式定义信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     */
    void deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 查询spel并返回任务指派的列表，支持分页
     *
     * @param taskQuery 查询条件
     * @return 办理人
     */
    RemoteTaskAssigneeVo selectSpelByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery);

    /**
     * 根据视图 SpEL 表达式列表，查询对应的备注信息
     *
     * @param viewSpels SpEL 表达式列表
     * @return 映射表：key 为 SpEL 表达式，value 为对应备注；若为空则返回空 Map
     */
    Map<String, String> selectRemarksBySpels(List<String> viewSpels);

}
