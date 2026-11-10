package org.tuoyu.mes.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.mes.report.domain.vo.MesDashboardVo;
import org.tuoyu.mes.report.service.IMesDashboardService;

/**
 * 生产看板
 *
 * @author tuoyu
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/dashboard")
public class MesDashboardController {

    private final IMesDashboardService dashboardService;

    /**
     * 看板数据：车间为空表示全部车间，days 为最近几天（1 ~ 90，默认 7）
     */
    @SaCheckPermission("mes:dashboard:view")
    @GetMapping("/overview")
    public ApiResult<MesDashboardVo> overview(@RequestParam(required = false) Long workshopId,
                                              @RequestParam(required = false) Integer days) {
        return ApiResult.ok(dashboardService.overview(workshopId, days));
    }

}
