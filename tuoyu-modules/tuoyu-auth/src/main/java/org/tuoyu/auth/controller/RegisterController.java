package org.tuoyu.auth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.auth.form.RegisterBody;
import org.tuoyu.auth.service.RegistrationService;
import org.tuoyu.auth.service.TenantLoginGuard;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.encrypt.annotation.ApiEncrypt;

/**
 * 用户注册
 *
 * @author tuoyu
 */
@RestController
public class RegisterController {

    private final TenantLoginGuard tenantLoginGuard;

    private final RegistrationService registrationService;

    public RegisterController(TenantLoginGuard tenantLoginGuard, RegistrationService registrationService) {
        this.tenantLoginGuard = tenantLoginGuard;
        this.registrationService = registrationService;
    }

    /**
     * 注册。开启多租户时注册到所选的租户
     *
     * @param body 注册请求
     */
    @SaIgnore
    @ApiEncrypt
    @PostMapping("/register")
    public ApiResult<Void> register(@Validated @RequestBody RegisterBody body) {
        tenantLoginGuard.within(body.tenantId(), () -> {
            registrationService.register(body);
            return null;
        });
        return ApiResult.ok();
    }

}
