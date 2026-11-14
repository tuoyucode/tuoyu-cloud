<#assign pkType = pkColumn.javaType>
<#assign pkField = pkColumn.javaField>
<#assign service = className + "Service">
package ${gen.packageName}.controller;

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
import ${gen.packageName}.domain.bo.${ClassName}Bo;
import ${gen.packageName}.domain.vo.${ClassName}Vo;
import ${gen.packageName}.service.I${ClassName}Service;

import java.util.List;

/**
 * ${functionName}
 * <p>
 * 路径只写业务名，模块前缀由网关或单体按包添加。
 *
 * @author ${gen.author}
 * @date ${gen.datetime}
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/${businessName}")
public class ${ClassName}Controller {

    private final I${ClassName}Service ${service};

<#if table.crud>
    /**
     * 分页查询${functionName}列表
     */
    @SaCheckPermission("${permissionPrefix}:list")
    @GetMapping("/list")
    public ApiResult<PageResult<${ClassName}Vo>> list(${ClassName}Bo bo, PageParam pageParam) {
        return ApiResult.ok(${service}.queryPageList(bo, pageParam));
    }
<#else>
    /**
     * 查询${functionName}列表（树表一次返回全部）
     */
    @SaCheckPermission("${permissionPrefix}:list")
    @GetMapping("/list")
    public ApiResult<List<${ClassName}Vo>> list(${ClassName}Bo bo) {
        return ApiResult.ok(${service}.queryList(bo));
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
        ExcelBuilder.query(() -> ${service}.countList(bo), () -> ${service}.queryList(bo), ${ClassName}Vo.class)
            .sheetName("${functionName}").toResponse(response);
    }
</#if>

    /**
     * 获取${functionName}详细信息
     *
     * @param ${pkField} 主键
     */
    @SaCheckPermission("${permissionPrefix}:query")
    @GetMapping("/{${pkField}}")
    public ApiResult<${ClassName}Vo> getInfo(@NotNull(message = "主键不能为空")
                                             @PathVariable ${pkType} ${pkField}) {
        return ApiResult.ok(${service}.queryById(${pkField}));
    }

    /**
     * 新增${functionName}
     */
    @SaCheckPermission("${permissionPrefix}:add")
    @OperationLog(module = "${functionName}", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody ${ClassName}Bo bo) {
        ${service}.insertByBo(bo);
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
        ${service}.updateByBo(bo);
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
        ${service}.updateStatus(bo.get${pkColumn.capJavaField}(), bo.get${statusColumn.capJavaField}());
        return ApiResult.ok();
    }
</#if>
<#if gen.enableSort>

    /**
     * 调整${functionName}排序
     */
    @SaCheckPermission("${permissionPrefix}:edit")
    @OperationLog(module = "${functionName}", action = AuditAction.UPDATE)
    @PutMapping("/updateSort")
    public ApiResult<Void> updateSort(@RequestBody ${ClassName}Bo bo) {
        ${service}.updateSort(bo.get${pkColumn.capJavaField}(), bo.get${gen.sortColumn.capJavaField}());
        return ApiResult.ok();
    }
</#if>

    /**
     * 删除${functionName}
     *
     * @param ${pkField}s 主键
     */
    @SaCheckPermission("${permissionPrefix}:remove")
    @OperationLog(module = "${functionName}", action = AuditAction.DELETE)
    @DeleteMapping("/{${pkField}s}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空")
                                  @PathVariable ${pkType}[] ${pkField}s) {
        ${service}.deleteWithValidByIds(List.of(${pkField}s), true);
        return ApiResult.ok();
    }
}
