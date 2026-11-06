package org.tuoyu.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.system.domain.vo.HomeOverviewVo;
import org.tuoyu.system.service.HomeOverviewService;

/**
 * 首页
 *
 * @author tuoyu
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/home")
public class SysHomeController {

    private final HomeOverviewService homeOverviewService;

    /**
     * 首页概况。登录即可访问；各部分按当前用户的权限返回，没有权限的部分为空
     */
    @SaCheckLogin
    @GetMapping("/overview")
    public R<HomeOverviewVo> overview() {
        return R.ok(homeOverviewService.overview());
    }

}
