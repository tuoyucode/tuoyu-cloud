package org.tuoyu.workflow.service;

import jakarta.servlet.http.HttpServletResponse;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.tuoyu.workflow.domain.vo.FlowDefinitionVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 流程定义 服务层
 *
 * @author may
 */
public interface IFlwDefinitionService {

    /**
     * 查询流程定义列表
     *
     * @param flowDefinition 参数
     * @param pageParam      分页
     * @return 返回分页列表
     */
    PageResult<FlowDefinitionVo> queryList(FlowDefinition flowDefinition, PageParam pageParam);

    /**
     * 查询未发布的流程定义列表
     *
     * @param flowDefinition 参数
     * @param pageParam      分页
     * @return 返回分页列表
     */
    PageResult<FlowDefinitionVo> unPublishList(FlowDefinition flowDefinition, PageParam pageParam);


    /**
     * 查询流程定义
     *
     * @param id 流程定义id
     * @return 流程定义，不存在时为 null
     */
    Definition getById(Long id);

    /**
     * 新增流程定义（版本号按流程编码自动递增）
     *
     * @param flowDefinition 流程定义
     */
    void insertDefinition(FlowDefinition flowDefinition);

    /**
     * 修改流程定义：流程定义不存在时抛出业务异常
     *
     * @param flowDefinition 流程定义
     */
    void updateDefinition(FlowDefinition flowDefinition);

    /**
     * 发布流程定义：有中间节点未配置办理人时抛出业务异常
     *
     * @param id 流程定义id
     */
    void publish(Long id);

    /**
     * 取消发布流程定义：已有流程实例使用时由流程引擎拒绝
     *
     * @param id 流程定义id
     */
    void unPublish(Long id);

    /**
     * 复制流程定义为新的未发布版本
     *
     * @param id 流程定义id
     */
    void copyDef(Long id);

    /**
     * 激活或挂起流程定义
     *
     * @param id     流程定义id
     * @param active true 激活，false 挂起
     */
    void active(Long id, boolean active);

    /**
     * 获取流程定义的 JSON 字符串
     *
     * @param id 流程定义id
     * @return JSON 字符串
     */
    String exportJson(Long id);

    /**
     * 导出流程定义
     *
     * @param id       流程定义id
     * @param response 响应
     * @throws IOException 异常
     */
    void exportDef(Long id, HttpServletResponse response) throws IOException;

    /**
     * 导入流程定义
     *
     * @param file     文件
     * @param category 分类
     */
    void importJson(MultipartFile file, String category);

    /**
     * 删除流程定义：已有流程实例使用时抛出业务异常
     *
     * @param ids 流程定义id
     */
    void removeDef(List<Long> ids);

}
