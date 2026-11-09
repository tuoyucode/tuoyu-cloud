package org.tuoyu.mes.trace.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.mes.trace.domain.vo.MesTraceVo;
import org.tuoyu.mes.trace.service.IMesTraceService;

/**
 * 追溯查询
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/trace")
public class MesTraceController {

    private final IMesTraceService traceService;

    /**
     * 按序列号、生产批次号、物料批次号或工单编号追溯（自动识别）
     */
    @SaCheckPermission("mes:trace:query")
    @GetMapping
    public ApiResult<MesTraceVo> trace(@RequestParam(required = false) String keyword) {
        return ApiResult.ok(traceService.trace(keyword));
    }

}
