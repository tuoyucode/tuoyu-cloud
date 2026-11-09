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
import org.tuoyu.mes.masterdata.domain.bo.MesWarehouseBo;
import org.tuoyu.mes.masterdata.domain.vo.MesWarehouseVo;
import org.tuoyu.mes.masterdata.service.IMesWarehouseService;

import java.util.List;

/**
 * 仓库
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/warehouse")
public class MesWarehouseController {

    private final IMesWarehouseService warehouseService;

    @SaCheckPermission("mes:warehouse:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesWarehouseVo>> list(MesWarehouseBo bo, PageParam pageParam) {
        return ApiResult.ok(warehouseService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的仓库，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long factoryId,
                                                @RequestParam(required = false) String warehouseType) {
        return ApiResult.ok(warehouseService.options(factoryId, warehouseType));
    }

    @SaCheckPermission("mes:warehouse:export")
    @OperationLog(module = "仓库", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesWarehouseBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> warehouseService.countList(bo), () -> warehouseService.queryList(bo), MesWarehouseVo.class)
            .sheetName("仓库").toResponse(response);
    }

    @SaCheckPermission("mes:warehouse:query")
    @GetMapping("/{warehouseId}")
    public ApiResult<MesWarehouseVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long warehouseId) {
        return ApiResult.ok(warehouseService.queryById(warehouseId));
    }

    @SaCheckPermission("mes:warehouse:add")
    @OperationLog(module = "仓库", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesWarehouseBo bo) {
        warehouseService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:warehouse:edit")
    @OperationLog(module = "仓库", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesWarehouseBo bo) {
        warehouseService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:warehouse:remove")
    @OperationLog(module = "仓库", action = AuditAction.DELETE)
    @DeleteMapping("/{warehouseIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] warehouseIds) {
        warehouseService.deleteByIds(List.of(warehouseIds));
        return ApiResult.ok();
    }

}
