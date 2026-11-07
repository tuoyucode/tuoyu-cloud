package org.tuoyu.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IoUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.StreamUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.json.utils.JsonUtils;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.LambdaQueryBuilder;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.service.DefService;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.dromara.warm.flow.orm.entity.FlowHisTask;
import org.dromara.warm.flow.orm.entity.FlowNode;
import org.dromara.warm.flow.orm.mapper.FlowDefinitionMapper;
import org.dromara.warm.flow.orm.mapper.FlowHisTaskMapper;
import org.dromara.warm.flow.orm.mapper.FlowNodeMapper;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.domain.vo.FlowDefinitionVo;
import org.tuoyu.workflow.mapper.FlwCategoryMapper;
import org.tuoyu.workflow.service.IFlwCommonService;
import org.tuoyu.workflow.service.IFlwDefinitionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 流程定义 服务层实现
 *
 * @author may
 */
@ConditionalOnEnable
@Slf4j
@RequiredArgsConstructor
@Service
public class FlwDefinitionServiceImpl implements IFlwDefinitionService {

    private final DefService defService;
    private final FlowDefinitionMapper flowDefinitionMapper;
    private final FlowHisTaskMapper flowHisTaskMapper;
    private final FlowNodeMapper flowNodeMapper;
    private final FlwCategoryMapper flwCategoryMapper;
    private final IFlwCommonService flwCommonService;

    /**
     * 查询流程定义列表
     *
     * @param flowDefinition 流程定义信息
     * @param pageParam      分页
     * @return 返回分页列表
     */
    @Override
    public PageResult<FlowDefinitionVo> queryList(FlowDefinition flowDefinition, PageParam pageParam) {
        LambdaQueryWrapper<FlowDefinition> wrapper = buildQueryWrapper(flowDefinition);
        wrapper.eq(FlowDefinition::getIsPublish, PublishStatus.PUBLISHED.getKey());
        Page<FlowDefinition> page = flowDefinitionMapper.selectPage(pageParam.toPage(FlowDefinition.class), wrapper);
        List<FlowDefinitionVo> list = BeanUtil.copyToList(page.getRecords(), FlowDefinitionVo.class);
        return PageResult.of(list, page.getTotal());
    }

    /**
     * 查询未发布的流程定义列表
     *
     * @param flowDefinition 流程定义信息
     * @param pageParam      分页
     * @return 返回分页列表
     */
    @Override
    public PageResult<FlowDefinitionVo> unPublishList(FlowDefinition flowDefinition, PageParam pageParam) {
        LambdaQueryWrapper<FlowDefinition> wrapper = buildQueryWrapper(flowDefinition);
        wrapper.in(FlowDefinition::getIsPublish, Arrays.asList(PublishStatus.UNPUBLISHED.getKey(), PublishStatus.EXPIRED.getKey()));
        Page<FlowDefinition> page = flowDefinitionMapper.selectPage(pageParam.toPage(FlowDefinition.class), wrapper);
        List<FlowDefinitionVo> list = BeanUtil.copyToList(page.getRecords(), FlowDefinitionVo.class);
        return PageResult.of(list, page.getTotal());
    }

    private LambdaQueryWrapper<FlowDefinition> buildQueryWrapper(FlowDefinition flowDefinition) {
        LambdaQueryBuilder<FlowDefinition> builder = QueryBuilder.lambda(FlowDefinition.class)
            .likeIfText(FlowDefinition::getFlowCode, flowDefinition.getFlowCode())
            .likeIfText(FlowDefinition::getFlowName, flowDefinition.getFlowName());
        if (StringUtils.isNotBlank(flowDefinition.getCategory())) {
            List<Long> categoryIds = flwCategoryMapper.selectCategoryIdsByParentId(Convert.toLong(flowDefinition.getCategory()));
            builder.inIfNotEmpty(FlowDefinition::getCategory, StreamUtils.toList(categoryIds, Convert::toStr));
        }
        return builder.orderByDesc(FlowDefinition::getCreateTime).build();
    }

    @Override
    public Definition getById(Long id) {
        return defService.getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insertDefinition(FlowDefinition flowDefinition) {
        if (!defService.checkAndSave(flowDefinition)) {
            throw new ServiceException("新增流程定义失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDefinition(FlowDefinition flowDefinition) {
        if (!defService.updateById(flowDefinition)) {
            throw new ServiceException("流程定义不存在，无法修改");
        }
    }

    /**
     * 发布流程定义
     *
     * @param id 流程定义id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        List<FlowNode> flowNodes = flowNodeMapper.selectList(
            QueryBuilder.lambda(FlowNode.class).eq(FlowNode::getDefinitionId, id).build());
        List<String> errorMsg = new ArrayList<>();
        if (CollUtil.isNotEmpty(flowNodes)) {
            String applyNodeCode = flwCommonService.applyNodeCode(id);
            for (FlowNode flowNode : flowNodes) {
                if (StringUtils.isBlank(flowNode.getPermissionFlag()) && !applyNodeCode.equals(flowNode.getNodeCode()) && NodeType.BETWEEN.getKey().equals(flowNode.getNodeType())) {
                    errorMsg.add(flowNode.getNodeName());
                }
            }
            if (CollUtil.isNotEmpty(errorMsg)) {
                throw new ServiceException("节点【{}】未配置办理人!", StringUtils.joinComma(errorMsg));
            }
        }
        if (!defService.publish(id)) {
            throw new ServiceException("发布流程定义失败，流程定义未更新");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unPublish(Long id) {
        if (!defService.unPublish(id)) {
            throw new ServiceException("取消发布流程定义失败，流程定义未更新");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyDef(Long id) {
        if (!defService.copyDef(id)) {
            throw new ServiceException("复制流程定义失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void active(Long id, boolean active) {
        boolean updated = active ? defService.active(id) : defService.unActive(id);
        if (!updated) {
            throw new ServiceException((active ? "激活" : "挂起") + "流程定义失败，流程定义未更新");
        }
    }

    @Override
    public String exportJson(Long id) {
        return defService.exportJson(id);
    }

    /**
     * 导入流程定义
     *
     * @param file 文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importJson(MultipartFile file, String category) {
        try {
            DefJson defJson = JsonUtils.parseObject(file.getBytes(), DefJson.class);
            defJson.setCategory(category);
            defService.importDef(defJson);
        } catch (IOException e) {
            log.error("读取文件流错误: {}", e.getMessage(), e);
            throw new IllegalStateException("文件读取失败，请检查文件内容", e);
        }
    }

    /**
     * 导出流程定义
     *
     * @param id       流程定义id
     * @param response 响应
     * @throws IOException 异常
     */
    @Override
    public void exportDef(Long id, HttpServletResponse response) throws IOException {
        byte[] data = defService.exportJson(id).getBytes(StandardCharsets.UTF_8);
        // 设置响应头和内容类型
        response.reset();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/text");
        response.setHeader("Content-Disposition", "attachment;");
        response.addHeader("Content-Length", "" + data.length);
        IoUtil.write(response.getOutputStream(), false, data);
    }

    /**
     * 删除流程定义
     *
     * @param ids 流程定义id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDef(List<Long> ids) {
        List<FlowHisTask> flowHisTasks = flowHisTaskMapper.selectList(
            QueryBuilder.lambda(FlowHisTask.class)
                .inIfNotEmpty(FlowHisTask::getDefinitionId, ids)
                .build());
        if (CollUtil.isNotEmpty(flowHisTasks)) {
            List<FlowDefinition> flowDefinitions = flowDefinitionMapper.selectByIds(StreamUtils.toList(flowHisTasks, FlowHisTask::getDefinitionId));
            if (CollUtil.isNotEmpty(flowDefinitions)) {
                String join = StreamUtils.join(flowDefinitions, FlowDefinition::getFlowCode);
                log.info("流程定义【{}】已被使用不可被删除！", join);
                throw new ServiceException("流程定义【" + join + "】已被使用不可被删除！");
            }
        }
        try {
            defService.removeDef(ids);
        } catch (Exception e) {
            log.error("Error removing flow definitions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove flow definitions", e);
        }
    }

}
