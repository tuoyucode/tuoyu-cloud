package org.tuoyu.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.workflow.common.ConditionalOnEnable;
import org.tuoyu.workflow.domain.bo.FlowCategoryBo;
import org.tuoyu.workflow.domain.vo.FlowCategoryVo;
import org.tuoyu.workflow.service.IFlwCategoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程分类
 *
 * @author may
 */
@ConditionalOnEnable
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/category")
public class FlwCategoryController {

    private final IFlwCategoryService flwCategoryService;

    /**
     * 查询流程分类列表
     */
    @SaCheckPermission("workflow:category:list")
    @GetMapping("/list")
    public ApiResult<List<FlowCategoryVo>> list(FlowCategoryBo bo) {
        List<FlowCategoryVo> list = flwCategoryService.queryList(bo);
        return ApiResult.ok(list);
    }

    /**
     * 导出流程分类列表
     */
    @SaCheckPermission("workflow:category:export")
    @OperationLog(module = "流程分类", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(FlowCategoryBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> flwCategoryService.countList(bo), () -> flwCategoryService.queryList(bo), FlowCategoryVo.class)
            .sheetName("流程分类").toResponse(response);
    }

    /**
     * 获取流程分类详细信息
     *
     * @param categoryId 主键
     */
    @SaCheckPermission("workflow:category:query")
    @GetMapping("/{categoryId}")
    public ApiResult<FlowCategoryVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long categoryId) {
        return ApiResult.ok(flwCategoryService.queryById(categoryId));
    }

    /**
     * 新增流程分类
     */
    @SaCheckPermission("workflow:category:add")
    @OperationLog(module = "流程分类", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping()
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody FlowCategoryBo category) {
        flwCategoryService.insertByBo(category);
        return ApiResult.ok();
    }

    /**
     * 修改流程分类
     */
    @SaCheckPermission("workflow:category:edit")
    @OperationLog(module = "流程分类", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping()
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody FlowCategoryBo category) {
        flwCategoryService.updateByBo(category);
        return ApiResult.ok();
    }

    /**
     * 删除流程分类
     *
     * @param categoryId 主键
     */
    @SaCheckPermission("workflow:category:remove")
    @OperationLog(module = "流程分类", action = AuditAction.DELETE)
    @DeleteMapping("/{categoryId}")
    public ApiResult<Void> remove(@PathVariable Long categoryId) {
        flwCategoryService.deleteWithValidById(categoryId);
        return ApiResult.ok();
    }

    /**
     * 获取流程分类树列表（仅登录：我的待办、我发起的等个人页面按分类筛选，分类名称不属于管理数据）
     *
     * @param categoryBo 流程分类
     */
    @SaCheckLogin
    @GetMapping("/categoryTree")
    public ApiResult<List<Tree<String>>> categoryTree(FlowCategoryBo categoryBo) {
        return ApiResult.ok(flwCategoryService.selectCategoryTreeList(categoryBo));
    }

}
