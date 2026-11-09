package org.tuoyu.mes.masterdata.controller;

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
import org.tuoyu.mes.masterdata.domain.bo.MesStationBo;
import org.tuoyu.mes.masterdata.domain.vo.MesStationVo;
import org.tuoyu.mes.masterdata.service.IMesStationService;

import java.util.List;

/**
 * 工位
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/station")
public class MesStationController {

    private final IMesStationService stationService;

    @SaCheckPermission("mes:station:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesStationVo>> list(MesStationBo bo, PageParam pageParam) {
        return ApiResult.ok(stationService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的工位，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long lineId) {
        return ApiResult.ok(stationService.options(lineId));
    }

    @SaCheckPermission("mes:station:export")
    @OperationLog(module = "工位", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesStationBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> stationService.countList(bo), () -> stationService.queryList(bo), MesStationVo.class)
            .sheetName("工位").toResponse(response);
    }

    @SaCheckPermission("mes:station:query")
    @GetMapping("/{stationId}")
    public ApiResult<MesStationVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long stationId) {
        return ApiResult.ok(stationService.queryById(stationId));
    }

    @SaCheckPermission("mes:station:add")
    @OperationLog(module = "工位", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesStationBo bo) {
        stationService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:station:edit")
    @OperationLog(module = "工位", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesStationBo bo) {
        stationService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:station:remove")
    @OperationLog(module = "工位", action = AuditAction.DELETE)
    @DeleteMapping("/{stationIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] stationIds) {
        stationService.deleteByIds(List.of(stationIds));
        return ApiResult.ok();
    }

}
