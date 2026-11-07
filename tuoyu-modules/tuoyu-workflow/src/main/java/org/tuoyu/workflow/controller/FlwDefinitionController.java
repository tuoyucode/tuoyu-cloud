package org.tuoyu.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.orm.entity.FlowDefinition;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.domain.vo.FlowDefinitionVo;
import org.tuoyu.workflow.service.IFlwDefinitionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 流程定义管理 控制层
 *
 * @author may
 */
@ConditionalOnEnable
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/definition")
public class FlwDefinitionController {

    private final IFlwDefinitionService flwDefinitionService;

    /**
     * 查询流程定义列表
     *
     * @param flowDefinition 参数
     * @param pageParam      分页
     */
    @GetMapping("/list")
    @SaCheckPermission("workflow:definition:list")
    public ApiResult<PageResult<FlowDefinitionVo>> list(FlowDefinition flowDefinition, PageParam pageParam) {
        return ApiResult.ok(flwDefinitionService.queryList(flowDefinition, pageParam));
    }

    /**
     * 查询未发布的流程定义列表
     *
     * @param flowDefinition 参数
     * @param pageParam      分页
     */
    @GetMapping("/unPublishList")
    @SaCheckPermission("workflow:definition:list")
    public ApiResult<PageResult<FlowDefinitionVo>> unPublishList(FlowDefinition flowDefinition, PageParam pageParam) {
        return ApiResult.ok(flwDefinitionService.unPublishList(flowDefinition, pageParam));
    }

    /**
     * 获取流程定义详细信息
     *
     * @param id 流程定义id
     */
    @GetMapping(value = "/{id}")
    @SaCheckPermission("workflow:definition:query")
    public ApiResult<Definition> getInfo(@PathVariable Long id) {
        return ApiResult.ok(flwDefinitionService.getById(id));
    }

    /**
     * 新增流程定义
     *
     * @param flowDefinition 参数
     */
    @OperationLog(module = "流程定义", action = AuditAction.CREATE)
    @PostMapping
    @PreventResubmit
    @SaCheckPermission("workflow:definition:add")
    public ApiResult<Void> add(@RequestBody FlowDefinition flowDefinition) {
        flwDefinitionService.insertDefinition(flowDefinition);
        return ApiResult.ok();
    }

    /**
     * 修改流程定义
     *
     * @param flowDefinition 参数
     */
    @OperationLog(module = "流程定义", action = AuditAction.UPDATE)
    @PutMapping
    @PreventResubmit
    @SaCheckPermission("workflow:definition:edit")
    public ApiResult<Void> edit(@RequestBody FlowDefinition flowDefinition) {
        flwDefinitionService.updateDefinition(flowDefinition);
        return ApiResult.ok();
    }

    /**
     * 发布流程定义
     *
     * @param id 流程定义id
     */
    @OperationLog(module = "流程定义", action = AuditAction.CREATE)
    @PutMapping("/publish/{id}")
    @PreventResubmit
    @SaCheckPermission("workflow:definition:publish")
    public ApiResult<Void> publish(@PathVariable Long id) {
        flwDefinitionService.publish(id);
        return ApiResult.ok();
    }

    /**
     * 取消发布流程定义
     *
     * @param id 流程定义id
     */
    @OperationLog(module = "流程定义", action = AuditAction.CREATE)
    @PutMapping("/unPublish/{id}")
    @PreventResubmit
    @SaCheckPermission("workflow:definition:publish")
    public ApiResult<Void> unPublish(@PathVariable Long id) {
        flwDefinitionService.unPublish(id);
        return ApiResult.ok();
    }

    /**
     * 删除流程定义
     */
    @OperationLog(module = "流程定义", action = AuditAction.DELETE)
    @DeleteMapping("/{ids}")
    @SaCheckPermission("workflow:definition:remove")
    public ApiResult<Void> remove(@PathVariable List<Long> ids) {
        flwDefinitionService.removeDef(ids);
        return ApiResult.ok();
    }

    /**
     * 复制流程定义
     *
     * @param id 流程定义id
     */
    @OperationLog(module = "流程定义", action = AuditAction.CREATE)
    @PostMapping("/copy/{id}")
    @PreventResubmit
    @SaCheckPermission("workflow:definition:copy")
    public ApiResult<Void> copy(@PathVariable Long id) {
        flwDefinitionService.copyDef(id);
        return ApiResult.ok();
    }

    /**
     * 导入流程定义
     *
     * @param file     文件
     * @param category 分类
     */
    @OperationLog(module = "流程定义", action = AuditAction.IMPORT)
    @PostMapping("/importDef")
    @SaCheckPermission("workflow:definition:import")
    public ApiResult<Void> importDef(MultipartFile file, String category) {
        flwDefinitionService.importJson(file, category);
        return ApiResult.ok();
    }

    /**
     * 导出流程定义
     *
     * @param id       流程定义id
     * @param response 响应
     * @throws IOException 异常
     */
    @OperationLog(module = "流程定义", action = AuditAction.EXPORT)
    @PostMapping("/exportDef/{id}")
    @SaCheckPermission("workflow:definition:export")
    public void exportDef(@PathVariable Long id, HttpServletResponse response) throws IOException {
        flwDefinitionService.exportDef(id, response);
    }

    /**
     * 获取流程定义JSON字符串
     *
     * @param id 流程定义id
     */
    @GetMapping("/xmlString/{id}")
    @SaCheckPermission("workflow:definition:query")
    public ApiResult<String> xmlString(@PathVariable Long id) {
        return ApiResult.ok(flwDefinitionService.exportJson(id));
    }

    /**
     * 激活/挂起流程定义
     *
     * @param id     流程定义id
     * @param active 激活/挂起
     */
    @PreventResubmit
    @PutMapping("/active/{id}")
    @OperationLog(module = "流程定义", action = AuditAction.UPDATE)
    @SaCheckPermission("workflow:definition:active")
    public ApiResult<Void> active(@PathVariable Long id, @RequestParam boolean active) {
        flwDefinitionService.active(id, active);
        return ApiResult.ok();
    }

}
