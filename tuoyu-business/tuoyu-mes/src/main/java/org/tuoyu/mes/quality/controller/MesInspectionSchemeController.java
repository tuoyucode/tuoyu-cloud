package org.tuoyu.mes.quality.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.core.validate.OnUpdate;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.quality.domain.bo.MesInspectionSchemeBo;
import org.tuoyu.mes.quality.domain.vo.MesInspectionSchemeVo;
import org.tuoyu.mes.quality.service.IMesInspectionSchemeService;

import java.util.List;

/**
 * 检验方案
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/inspection-scheme")
public class MesInspectionSchemeController {

    private static final String MODULE = "检验方案";

    private final IMesInspectionSchemeService schemeService;

    @SaCheckPermission("mes:inspectionScheme:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesInspectionSchemeVo>> list(MesInspectionSchemeBo bo, PageParam pageParam) {
        return ApiResult.ok(schemeService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:inspectionScheme:export")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesInspectionSchemeBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> schemeService.countList(bo), () -> schemeService.queryList(bo), MesInspectionSchemeVo.class)
            .sheetName(MODULE).toResponse(response);
    }

    /**
     * 启用的检验方案，供新增检验单时选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) String inspectionType,
                                                @RequestParam(required = false) Long materialId) {
        return ApiResult.ok(schemeService.options(inspectionType, materialId));
    }

    /**
     * 新增检验单时自动匹配的方案；没有匹配的方案时 data 为 null
     */
    @SaCheckPermission("mes:inspection:add")
    @GetMapping("/match")
    public ApiResult<MesInspectionSchemeVo> match(@RequestParam String inspectionType,
                                                  @RequestParam(required = false) Long materialId,
                                                  @RequestParam(required = false) String operationCode) {
        return ApiResult.ok(schemeService.match(inspectionType, materialId, operationCode));
    }

    @SaCheckPermission("mes:inspectionScheme:query")
    @GetMapping("/{schemeId}")
    public ApiResult<MesInspectionSchemeVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long schemeId) {
        return ApiResult.ok(schemeService.queryById(schemeId));
    }

    @SaCheckPermission("mes:inspectionScheme:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesInspectionSchemeBo bo) {
        schemeService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:inspectionScheme:edit")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesInspectionSchemeBo bo) {
        schemeService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:inspectionScheme:remove")
    @OperationLog(module = MODULE, action = AuditAction.DELETE)
    @DeleteMapping("/{schemeIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] schemeIds) {
        schemeService.deleteByIds(List.of(schemeIds));
        return ApiResult.ok();
    }

}
