package org.tuoyu.resource.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.tuoyu.common.core.domain.R;
import org.tuoyu.common.satoken.utils.LoginHelper;
import org.tuoyu.common.web.core.BaseController;
import org.tuoyu.resource.domain.vo.SysMessageBoxVo;
import org.tuoyu.resource.service.ISysMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息记录控制器
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/message")
public class SysMessageController extends BaseController {

    private final ISysMessageService messageService;

    /**
     * 当前用户的消息盒子（仅登录：只查询本人的消息）
     */
    @SaCheckLogin
    @GetMapping("/box")
    public R<SysMessageBoxVo> getBox() {
        return R.ok(messageService.queryMessageBox(LoginHelper.getUserId()));
    }
}
