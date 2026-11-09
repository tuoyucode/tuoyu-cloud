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
import org.tuoyu.mes.masterdata.domain.bo.MesWorkshopBo;
import org.tuoyu.mes.masterdata.domain.vo.MesWorkshopVo;
import org.tuoyu.mes.masterdata.service.IMesWorkshopService;

import java.util.List;

/**
 * 车间
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/workshop")
public class MesWorkshopController {

    private final IMesWorkshopService workshopService;

    @SaCheckPermission("mes:workshop:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesWorkshopVo>> list(MesWorkshopBo bo, PageParam pageParam) {
        return ApiResult.ok(workshopService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的车间，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long factoryId) {
        return ApiResult.ok(workshopService.options(factoryId));
    }

    @SaCheckPermission("mes:workshop:export")
    @OperationLog(module = "车间", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesWorkshopBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> workshopService.countList(bo), () -> workshopService.queryList(bo), MesWorkshopVo.class)
            .sheetName("车间").toResponse(response);
    }

    @SaCheckPermission("mes:workshop:query")
    @GetMapping("/{workshopId}")
    public ApiResult<MesWorkshopVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long workshopId) {
        return ApiResult.ok(workshopService.queryById(workshopId));
    }

    @SaCheckPermission("mes:workshop:add")
    @OperationLog(module = "车间", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesWorkshopBo bo) {
        workshopService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workshop:edit")
    @OperationLog(module = "车间", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesWorkshopBo bo) {
        workshopService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:workshop:remove")
    @OperationLog(module = "车间", action = AuditAction.DELETE)
    @DeleteMapping("/{workshopIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] workshopIds) {
        workshopService.deleteByIds(List.of(workshopIds));
        return ApiResult.ok();
    }

}
