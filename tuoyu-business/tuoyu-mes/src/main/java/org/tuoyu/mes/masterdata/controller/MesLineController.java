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
import org.tuoyu.mes.masterdata.domain.bo.MesLineBo;
import org.tuoyu.mes.masterdata.domain.vo.MesLineVo;
import org.tuoyu.mes.masterdata.service.IMesLineService;

import java.util.List;

/**
 * 产线
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/line")
public class MesLineController {

    private final IMesLineService lineService;

    @SaCheckPermission("mes:line:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesLineVo>> list(MesLineBo bo, PageParam pageParam) {
        return ApiResult.ok(lineService.queryPageList(bo, pageParam));
    }

    /**
     * 启用的产线，供其他页面选择。只要求登录：只含 ID、编码与名称
     */
    @SaCheckLogin
    @GetMapping("/options")
    public ApiResult<List<MesOptionVo>> options(@RequestParam(required = false) Long workshopId) {
        return ApiResult.ok(lineService.options(workshopId));
    }

    @SaCheckPermission("mes:line:export")
    @OperationLog(module = "产线", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesLineBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> lineService.countList(bo), () -> lineService.queryList(bo), MesLineVo.class)
            .sheetName("产线").toResponse(response);
    }

    @SaCheckPermission("mes:line:query")
    @GetMapping("/{lineId}")
    public ApiResult<MesLineVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long lineId) {
        return ApiResult.ok(lineService.queryById(lineId));
    }

    @SaCheckPermission("mes:line:add")
    @OperationLog(module = "产线", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping
    public ApiResult<Void> add(@Validated(OnCreate.class) @RequestBody MesLineBo bo) {
        lineService.insertByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:line:edit")
    @OperationLog(module = "产线", action = AuditAction.UPDATE)
    @PreventResubmit
    @PutMapping
    public ApiResult<Void> edit(@Validated(OnUpdate.class) @RequestBody MesLineBo bo) {
        lineService.updateByBo(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:line:remove")
    @OperationLog(module = "产线", action = AuditAction.DELETE)
    @DeleteMapping("/{lineIds}")
    public ApiResult<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] lineIds) {
        lineService.deleteByIds(List.of(lineIds));
        return ApiResult.ok();
    }

}
