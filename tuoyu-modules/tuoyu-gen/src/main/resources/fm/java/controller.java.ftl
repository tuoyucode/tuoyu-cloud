package ${packageName}.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
<#if enableExport>
import jakarta.servlet.http.HttpServletResponse;
</#if>
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.tuoyu.common.core.domain.ApiResult;
<#if table.crud>
import org.tuoyu.common.core.domain.PageResult;
</#if>
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
<#if enableExport>
import org.tuoyu.common.excel.utils.ExcelBuilder;
</#if>
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
<#if table.crud>
import org.tuoyu.common.mybatis.core.page.PageParam;
</#if>
import org.tuoyu.common.redis.annotation.PreventResubmit;
import ${packageName}.domain.bo.${ClassName}Bo;
import ${packageName}.domain.vo.${ClassName}Vo;
import ${packageName}.service.I${ClassName}Service;

import java.util.List;

/**
 * ${functionName}
 * <p>
 * 失败时由服务层抛出带原因的 ServiceException，全局异常处理按它返回 HTTP 状态码与提示。
 *
 * @author ${author}
 * @date ${datetime}
 */
@Validated
@RequiredArgsConstructor
@RestController
<#-- 模块前缀（例如 /system）由网关或单体按模块添加，这里只写业务路径 -->
@RequestMapping("/${businessName}")
public class ${ClassName}Controller {

    private final I${ClassName}Service ${className}Service;

    /**
     * 查询${functionName}列表
     */
    @SaCheckPermission("${permissionPrefix}:list")
    @GetMapping("/list")
<#if table.crud>
    public ApiResult<PageResult<${ClassName}Vo>> list(${ClassName}Bo bo, PageParam pageParam) {
        return ApiResult.ok(${className}Service.queryPageList(bo, pageParam));
    }
<#elseif table.tree>
    public ApiResult<List<${ClassName}Vo>> list(${ClassName}Bo bo) {
        return ApiResult.ok(${className}Service.queryList(bo));
    }
</#if>

<#if enableExport>
    /**
     * 导出${functionName}列表
     */
    @SaCheckPermission("${permissionPrefix}:export")
    @OperationLog(module = "${functionName}", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(${ClassName}Bo bo, HttpServletResponse response) {
        // 先统计行数，超过 tuoyu.excel.max-export-rows 时直接拒绝，不执行查询
        ExcelBuilder.query(() -> ${className}Service.countList(bo), () -> ${className}Service.queryList(bo), ${ClassName}Vo.class)
            .sheetName("${functionName}").toResponse(response);
    }

</#if>
    /**
     * 获取${functionName}详细信息
     *
     * @param ${pkColumn.javaField} 主键
     */
    @SaCheckPermission("${permissionPrefix}:query")
    @GetMapping("/{${pkColumn.javaField}}")
    public ApiResult<${ClassName}Vo> getInfo(@NotNull(message = "主键不能为空")
                                             @PathVariable ${pkColumn.javaType} ${pkColumn.javaField}) {
        return ApiResult.ok(${className}Service.queryById(${pkColumn.javaField}));
    }

    /**
     * 新增${functionName}
     */
    @SaCheckPermission("${permissionPrefix}:add")
    @OperationLog(module = "${functionName}", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody ${ClassName}Bo bo) {
        ${className}Service.insertByBo(bo);
        return ApiResult.ok();
    }

    /**
     * 修改${functionName}
     */
    @SaCheckPermission("${permissionPrefix}:edit")
    @OperationLog(module = "${functionName}", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody ${ClassName}Bo bo) {
        ${className}Service.updateByBo(bo);
        return ApiResult.ok();
    }

<#if enableStatus>
    /**
     * 修改${functionName}状态
     */
    @SaCheckPermission("${permissionPrefix}:edit")
    @OperationLog(module = "${functionName}", action = AuditAction.UPDATE)
    @PutMapping("/changeStatus")
    public ApiResult<Void> changeStatus(@RequestBody ${ClassName}Bo bo) {
        ${className}Service.updateStatus(bo.get${pkColumn.capJavaField}(), bo.get${statusColumn.capJavaField}());
        return ApiResult.ok();
    }

</#if>
<#if enableSort>
    /**
     * 调整${functionName}排序
     */
    @SaCheckPermission("${permissionPrefix}:edit")
    @OperationLog(module = "${functionName}", action = AuditAction.UPDATE)
    @PutMapping("/updateSort")
    public ApiResult<Void> updateSort(@RequestBody ${ClassName}Bo bo) {
        ${className}Service.updateSort(bo.get${pkColumn.capJavaField}(), bo.get${sortColumn.capJavaField}());
        return ApiResult.ok();
    }

</#if>
    /**
     * 删除${functionName}
     *
     * @param ${pkColumn.javaField}s 主键串
     */
    @SaCheckPermission("${permissionPrefix}:remove")
    @OperationLog(module = "${functionName}", action = AuditAction.DELETE)
    @DeleteMapping("/{${pkColumn.javaField}s}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                  @PathVariable ${pkColumn.javaType}[] ${pkColumn.javaField}s) {
        ${className}Service.deleteWithValidByIds(List.of(${pkColumn.javaField}s), true);
        return ApiResult.ok();
    }
}
