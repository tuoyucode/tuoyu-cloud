package org.tuoyu.auth.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.auth.properties.CaptchaProperties;
import org.tuoyu.auth.service.AuthAttempt;
import org.tuoyu.auth.service.SysLoginService;
import org.tuoyu.common.encrypt.password.PasswordHasher;
import org.tuoyu.system.api.RemoteUserService;
import org.tuoyu.system.api.model.LoginUser;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("密码比对")
class PasswordAuthStrategyTest {

    private final SysLoginService loginService = mock(SysLoginService.class);

    private final PasswordHasher passwordHasher = spy(new PasswordHasher(PasswordHasher.MIN_COST));

    private final PasswordAuthStrategy strategy =
        new PasswordAuthStrategy(new CaptchaProperties(), loginService, passwordHasher, mock(RemoteUserService.class));

    @Test
    @DisplayName("账号不存在时也执行一次同等强度的比对，响应时间不暴露账号是否存在")
    void comparesEvenWhenUserMissing() {
        AuthAttempt missing = AuthAttempt.failure("user.not.exists");
        when(loginService.findUser(any())).thenReturn(missing);

        assertSame(missing, strategy.checkPassword("nobody", "P@ssw0rd"));
        verify(passwordHasher).matches("P@ssw0rd", null);
    }

    @Test
    @DisplayName("密码正确时通过")
    void passesWithCorrectPassword() {
        LoginUser user = new LoginUser();
        user.setPassword(passwordHasher.hash("P@ssw0rd"));
        when(loginService.findUser(any())).thenReturn(AuthAttempt.success(user));

        assertTrue(strategy.checkPassword("admin", "P@ssw0rd").passed());
    }

}
