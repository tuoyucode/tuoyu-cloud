package org.tuoyu.workflow.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.constant.SystemConstants;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.BeanMapper;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.page.PageResults;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.system.api.domain.bo.RemoteTaskAssigneeBo;
import org.tuoyu.system.api.domain.vo.RemoteTaskAssigneeVo;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.domain.FlowSpel;
import org.tuoyu.workflow.domain.bo.FlowSpelBo;
import org.tuoyu.workflow.domain.vo.FlowSpelVo;
import org.tuoyu.workflow.mapper.FlwSpelMapper;
import org.tuoyu.workflow.service.IFlwSpelService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流程spel表达式定义Service业务层处理
 *
 * @author Michelle.Chung
 * @date 2025-07-04
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwSpelServiceImpl implements IFlwSpelService {

    private final FlwSpelMapper spelMapper;

    /**
     * 查询流程spel表达式定义
     *
     * @param id 主键
     * @return 流程spel表达式定义
     */
    @Override
    public FlowSpelVo queryById(Long id) {
        return BeanMapper.map(spelMapper.selectById(id), FlowSpelVo.class);
    }

    /**
     * 分页查询流程spel表达式定义列表
     *
     * @param bo        查询条件
     * @param pageParam 分页参数
     * @return 流程spel表达式定义分页列表
     */
    @Override
    public PageResult<FlowSpelVo> queryPageList(FlowSpelBo bo, PageParam pageParam) {
        LambdaQueryWrapper<FlowSpel> lqw = buildQueryWrapper(bo);
        return PageResults.of(spelMapper.selectPage(pageParam.toPage(FlowSpel.class), lqw), FlowSpelVo.class);
    }

    /**
     * 查询符合条件的流程spel表达式定义列表
     *
     * @param bo 查询条件
     * @return 流程spel表达式定义列表
     */
    @Override
    public List<FlowSpelVo> queryList(FlowSpelBo bo) {
        LambdaQueryWrapper<FlowSpel> lqw = buildQueryWrapper(bo);
        return BeanMapper.mapList(spelMapper.selectList(lqw), FlowSpelVo.class);
    }

    private LambdaQueryWrapper<FlowSpel> buildQueryWrapper(FlowSpelBo bo) {
        return QueryBuilder.lambda(FlowSpel.class)
            .likeIfText(FlowSpel::getComponentName, bo.getComponentName())
            .likeIfText(FlowSpel::getMethodName, bo.getMethodName())
            .eqIfText(FlowSpel::getMethodParams, bo.getMethodParams())
            .eqIfText(FlowSpel::getViewSpel, bo.getViewSpel())
            .eqIfText(FlowSpel::getStatus, bo.getStatus())
            .likeIfText(FlowSpel::getRemark, bo.getRemark())
            .orderByAsc(FlowSpel::getId)
            .build();
    }

    /**
     * 新增流程spel表达式定义
     *
     * @param bo 流程spel表达式定义
     */
    @Override
    public void insertByBo(FlowSpelBo bo) {
        FlowSpel add = BeanMapper.map(bo, FlowSpel.class);
        validEntityBeforeSave(add);
        spelMapper.insert(add);
        bo.setId(add.getId());
    }

    /**
     * 修改流程spel表达式定义
     *
     * @param bo 流程spel表达式定义
     */
    @Override
    public void updateByBo(FlowSpelBo bo) {
        FlowSpel update = BeanMapper.map(bo, FlowSpel.class);
        validEntityBeforeSave(update);
        if (spelMapper.updateById(update) == 0) {
            throw new ServiceException("流程spel表达式定义不存在，无法修改");
        }
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(FlowSpel entity) {
        if (StringUtils.isNotBlank(entity.getViewSpel())) {
            boolean exists = spelMapper.exists(QueryBuilder.lambda(FlowSpel.class)
                .eq(FlowSpel::getViewSpel, entity.getViewSpel())
                .neIfPresent(FlowSpel::getId, entity.getId())
                .build());
            if (exists) {
                throw new ServiceException("SpEL表达式已存在，请勿重复添加");
            }
        }
    }

    /**
     * 校验并批量删除流程spel表达式定义信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     */
    @Override
    public void deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        if (spelMapper.deleteByIds(ids) == 0) {
            throw new ServiceException("删除流程表达式失败，表达式不存在");
        }
    }

    /**
     * 查询spel并返回任务指派的列表，支持分页
     *
     * @param taskQuery 查询条件
     * @return 办理人
     */
    @Override
    public RemoteTaskAssigneeVo selectSpelByTaskAssigneeList(RemoteTaskAssigneeBo taskQuery) {
        PageParam pageParam = new PageParam(taskQuery.getPage(), taskQuery.getPageSize());
        FlowSpelBo bo = new FlowSpelBo();
        bo.setViewSpel(taskQuery.getHandlerCode());
        bo.setRemark(taskQuery.getHandlerName());
        bo.setStatus(SystemConstants.NORMAL);
        Map<String, Object> params = bo.getParams();
        params.put("beginTime", taskQuery.getBeginTime());
        params.put("endTime", taskQuery.getEndTime());
        PageResult<FlowSpelVo> page = this.queryPageList(bo, pageParam);
        // 使用封装的字段映射方法进行转换
        List<RemoteTaskAssigneeVo.TaskHandler> handlers = RemoteTaskAssigneeVo.convertToHandlerList(page.getItems(),
            FlowSpelVo::getViewSpel, item -> "", FlowSpelVo::getRemark, item -> "", FlowSpelVo::getCreateTime);
        return new RemoteTaskAssigneeVo(page.getTotal(), handlers);
    }

    /**
     * 根据视图 SpEL 表达式列表，查询对应的备注信息
     *
     * @param viewSpels SpEL 表达式列表
     * @return 映射表：key 为 SpEL 表达式，value 为对应备注；若为空则返回空 Map
     */
    @Override
    public Map<String, String> selectRemarksBySpels(List<String> viewSpels) {
        if (CollUtil.isEmpty(viewSpels)) {
            return Collections.emptyMap();
        }
        List<FlowSpel> list = spelMapper.selectList(QueryBuilder.lambda(FlowSpel.class)
            .select(FlowSpel::getViewSpel, FlowSpel::getRemark)
            .in(FlowSpel::getViewSpel, viewSpels)
            .build());
        return StreamUtils.toMap(list, FlowSpel::getViewSpel, FlowSpel::getRemark);
    }

}
