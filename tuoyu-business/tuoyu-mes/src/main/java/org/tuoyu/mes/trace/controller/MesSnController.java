package org.tuoyu.mes.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
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
import org.tuoyu.common.excel.utils.ExcelBuilder;
import org.tuoyu.common.log.annotation.OperationLog;
import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.redis.annotation.PreventResubmit;
import org.tuoyu.mes.common.statuslog.MesStatusLogVo;
import org.tuoyu.mes.trace.domain.bo.MesReasonBo;
import org.tuoyu.mes.trace.domain.bo.MesSnBo;
import org.tuoyu.mes.trace.domain.bo.MesSnGenerateBo;
import org.tuoyu.mes.trace.domain.vo.MesSnVo;
import org.tuoyu.mes.trace.service.IMesSnService;

import java.util.List;

/**
 * 产品序列号
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/sn")
public class MesSnController {

    private final IMesSnService snService;

    @SaCheckPermission("mes:sn:list")
    @GetMapping("/list")
    public ApiResult<PageResult<MesSnVo>> list(MesSnBo bo, PageParam pageParam) {
        return ApiResult.ok(snService.queryPageList(bo, pageParam));
    }

    /**
     * 导出序列号（用于打印标签）
     */
    @SaCheckPermission("mes:sn:export")
    @OperationLog(module = "产品序列号", action = AuditAction.EXPORT)
    @PostMapping("/export")
    public void export(MesSnBo bo, HttpServletResponse response) {
        ExcelBuilder.query(() -> snService.countList(bo), () -> snService.queryList(bo), MesSnVo.class)
            .sheetName("产品序列号").toResponse(response);
    }

    @SaCheckPermission("mes:sn:query")
    @GetMapping("/{snId}")
    public ApiResult<MesSnVo> getInfo(@NotNull(message = "主键不能为空") @PathVariable Long snId) {
        return ApiResult.ok(snService.queryById(snId));
    }

    @SaCheckPermission("mes:sn:query")
    @GetMapping("/{snId}/logs")
    public ApiResult<List<MesStatusLogVo>> logs(@NotNull(message = "主键不能为空") @PathVariable Long snId) {
        return ApiResult.ok(snService.logs(snId));
    }

    @SaCheckPermission("mes:sn:generate")
    @OperationLog(module = "产品序列号", action = AuditAction.CREATE)
    @PreventResubmit
    @PostMapping("/generate")
    public ApiResult<Void> generate(@Validated @RequestBody MesSnGenerateBo bo) {
        snService.generate(bo);
        return ApiResult.ok();
    }

    @SaCheckPermission("mes:sn:scrap")
    @OperationLog(module = "产品序列号", action = AuditAction.UPDATE)
    @PreventResubmit
    @PostMapping("/{snId}/scrap")
    public ApiResult<Void> scrap(@NotNull(message = "主键不能为空") @PathVariable Long snId,
                                 @Validated @RequestBody MesReasonBo bo) {
        snService.scrap(snId, bo.getReason());
        return ApiResult.ok();
    }

}
