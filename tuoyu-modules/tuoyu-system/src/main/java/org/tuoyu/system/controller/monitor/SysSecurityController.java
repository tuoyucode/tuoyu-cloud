package org.tuoyu.system.controller.monitor;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.system.domain.vo.SecurityOverviewVo;
import org.tuoyu.system.service.SecurityOverviewService;

/**
 * 安全中心（只读）
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/security")
public class SysSecurityController {

    private final SecurityOverviewService securityOverviewService;

    /**
     * 启动自检结果与运行时防护配置
     */
    @SaCheckPermission("monitor:security:list")
    @GetMapping("/overview")
    public R<SecurityOverviewVo> overview() {
        return R.ok(securityOverviewService.overview());
    }

}
