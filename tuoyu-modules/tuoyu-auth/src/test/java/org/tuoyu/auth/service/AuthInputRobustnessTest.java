package org.tuoyu.auth.service;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("认证：客户端超时为空、注册时的用户类型")
class AuthInputRobustnessTest {

    @Test
    @DisplayName("客户端没有配置超时：沿用全局配置，不在拆箱时空指针；配置了则按客户端设置")
    void clientWithoutTimeouts() {
        RemoteClientVo client = new RemoteClientVo();
        client.setClientId("pc");
        client.setDeviceType("pc");
        SaLoginParameter defaults = new SaLoginParameter();

        SaLoginParameter parameter = IAuthStrategy.buildLoginParameter(client);

        assertEquals(defaults.getTimeout(), parameter.getTimeout());
        assertEquals(defaults.getActiveTimeout(), parameter.getActiveTimeout());

        client.setTimeout(3600L);
        client.setActiveTimeout(600L);
        SaLoginParameter configured = IAuthStrategy.buildLoginParameter(client);
        assertEquals(3600L, configured.getTimeout());
        assertEquals(Long.valueOf(600L), configured.getActiveTimeout());
    }

    @Test
    @DisplayName("注册的用户类型必须与某个类型完全一致，缺少或不正确时给出明确提示，不返回 500")
    void registerUserType() {
        assertEquals("sys_user", SysLoginService.registerUserType("sys_user"));
        assertEquals("app_user", SysLoginService.registerUserType("app_user"));
        for (String invalid : new String[]{null, "", "admin", "xsys_userx"}) {
            assertThrows(ServiceException.class, () -> SysLoginService.registerUserType(invalid), String.valueOf(invalid));
        }
    }

}
