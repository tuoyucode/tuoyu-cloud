package org.tuoyu.mes.equipment.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.equipment.domain.bo.MesOeeConfigBo;
import org.tuoyu.mes.equipment.domain.bo.MesOeeQueryBo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeConfigVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeExportVo;
import org.tuoyu.mes.equipment.domain.vo.MesOeeVo;
import org.tuoyu.mes.equipment.service.IMesOeeService;

/**
 * 设备综合效率（OEE）
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/oee")
public class MesOeeController {

    private static final String MODULE = "OEE";

    private final IMesOeeService oeeService;

    @SaCheckPermission("mes:equipment:oee")
    @GetMapping
    public ApiResult<MesOeeVo> calculate(@Validated MesOeeQueryBo bo) {
        return ApiResult.ok(oeeService.calculate(bo));
    }

    /**
     * 导出汇总与按日明细（最多 366 天，直接写出）
     */
    @SaCheckPermission("mes:equipment:oeeExport")
    @OperationLog(module = MODULE, action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(@Validated MesOeeQueryBo bo, HttpServletResponse response) {
        ExcelBuilder.of(oeeService.exportRows(bo), MesOeeExportVo.class).sheetName(MODULE).toResponse(response);
    }

    @SaCheckPermission("mes:equipment:oee")
    @GetMapping("/config")
    public ApiResult<MesOeeConfigVo> getConfig(@RequestParam String targetType, @RequestParam Long targetId) {
        return ApiResult.ok(oeeService.getConfig(targetType, targetId));
    }

    @SaCheckPermission("mes:equipment:oeeConfig")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping("/config")
    public ApiResult<Void> saveConfig(@Validated @RequestBody MesOeeConfigBo bo) {
        oeeService.saveConfig(bo);
        return ApiResult.ok();
    }

}
