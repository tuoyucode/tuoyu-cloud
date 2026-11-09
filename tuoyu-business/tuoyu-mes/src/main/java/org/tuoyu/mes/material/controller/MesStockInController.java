package org.tuoyu.mes.material.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.domain.PageResult;
import org.tuoyu.common.core.validate.OnCreate;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.material.domain.bo.MesStockInBo;
import org.tuoyu.mes.material.domain.vo.MesStockInVo;
import org.tuoyu.mes.material.service.IMesStockInService;

import java.util.List;

/**
 * 完工入库
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/stock-in")
public class MesStockInController {

    private static final String MODULE = "完工入库";

    private final IMesStockInService stockInService;

    @SaCheckPermission("mes:stockIn:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesStockInVo>> list(MesStockInBo bo, PageParam pageParam) {
        return ApiResult.ok(stockInService.queryPageList(bo, pageParam));
    }

    @SaCheckPermission("mes:stockIn:query")
    @GetMapping("/{stockInId}")
    public ApiResult<MesStockInVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long stockInId) {
        return ApiResult.ok(stockInService.queryById(stockInId));
    }

    @SaCheckPermission("mes:stockIn:query")
    @GetMapping("/{stockInId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long stockInId) {
        return ApiResult.ok(stockInService.logs(stockInId));
    }

    @SaCheckPermission("mes:stockIn:add")
    @OperationLog(module = MODULE, action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesStockInBo bo) {
        stockInService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:stockIn:complete")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{stockInId}/complete")
    public ApiResult<Void> complete(@NotNull(message = "主键不能为空") @PathVariable Long stockInId) {
        stockInService.complete(stockInId);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:stockIn:cancel")
    @OperationLog(module = MODULE, action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{stockInId}/cancel")
    public ApiResult<Void> cancel(@NotNull(message = "主键不能为空") @PathVariable Long stockInId) {
        stockInService.cancel(stockInId);
        return ApiResult.ok();
    }

}
