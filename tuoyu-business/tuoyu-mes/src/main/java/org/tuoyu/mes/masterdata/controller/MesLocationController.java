package org.tuoyu.mes.masterdata.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.domain.vo.MesOptionVo;
import org.tuoyu.mes.masterdata.domain.bo.MesLocationBo;
import org.tuoyu.mes.masterdata.domain.vo.MesLocationVo;
import org.tuoyu.mes.masterdata.service.IMesWarehouseService;

import java.util.List;

/**
 * 库位（在仓库页面中维护，权限与仓库相同）
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/location")
public class MesLocationController {

    private final IMesWarehouseService warehouseService;

    @SaCheckPermission("mes:warehouse:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesLocationVo>> list(MesLocationBo bo, PageParam pageParam) {
        return ApiResult.ok(warehouseService.queryLocationPageList(bo, pageParam));
    }

    /**
     * 启用的库位，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long warehouseId) {
        return ApiResult.ok(warehouseService.locationOptions(warehouseId));
    }

    @SaCheckPermission("mes:warehouse:query")
    @GetMapping("/{locationId}")
    public ApiResult<MesLocationVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long locationId) {
        return ApiResult.ok(warehouseService.queryLocationById(locationId));
    }

    @SaCheckPermission("mes:warehouse:add")
    @OperationLog(module = "库位", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesLocationBo bo) {
        warehouseService.insertLocation(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:warehouse:edit")
    @OperationLog(module = "库位", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesLocationBo bo) {
        warehouseService.updateLocation(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:warehouse:remove")
    @OperationLog(module = "库位", action = AuditAction.DELETE)
    @DeleteMapping("/{locationIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] locationIds) {
        warehouseService.deleteLocations(List.of(locationIds));
        return ApiResult.ok();
    }

}
