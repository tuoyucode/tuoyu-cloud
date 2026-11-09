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
import org.tuoyu.mes.masterdata.domain.bo.MesRouteBo;
import org.tuoyu.mes.masterdata.domain.vo.MesRouteVo;
import org.tuoyu.mes.masterdata.service.IMesRouteService;

import java.util.List;

/**
 * 工艺路线
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/route")
public class MesRouteController {

    private final IMesRouteService routeService;

    @SaCheckPermission("mes:route:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesRouteVo>> list(MesRouteBo bo, PageParam pageParam) {
        return ApiResult.ok(routeService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的工艺路线，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long materialId) {
        return ApiResult.ok(routeService.options(materialId));
    }

    @SaCheckPermission("mes:route:export")
    @OperationLog(module = "工艺路线", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesRouteBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> routeService.countList(bo), () -> routeService.queryList(bo), MesRouteVo.class)
            .sheetName("工艺路线").toResponse(response);
    }

    @SaCheckPermission("mes:route:query")
    @GetMapping("/{routeId}")
    public ApiResult<MesRouteVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long routeId) {
        return ApiResult.ok(routeService.queryById(routeId));
    }

    @SaCheckPermission("mes:route:add")
    @OperationLog(module = "工艺路线", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesRouteBo bo) {
        routeService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:route:edit")
    @OperationLog(module = "工艺路线", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesRouteBo bo) {
        routeService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:route:remove")
    @OperationLog(module = "工艺路线", action = AuditAction.DELETE)
    @DeleteMapping("/{routeIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] routeIds) {
        routeService.deleteByIds(List.of(routeIds));
        return ApiResult.ok();
    }

}
