package org.tuoyu.common.log.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.system.api.RemoteClientService;
import org.tuoyu.system.api.RemoteLogService;
import org.tuoyu.system.api.domain.bo.RemoteLoginInfoBo;
import org.tuoyu.system.api.domain.vo.RemoteClientVo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("登录日志：客户端、IP、浏览器与状态；请求没有 User-Agent 时照常记录")
class LogEventListenerTest {

    private final RemoteLogService remoteLogService = mock(RemoteLogService.class);

    private final RemoteClientService remoteClientService = mock(RemoteClientService.class);

    private final LogEventListener listener = new LogEventListener(remoteLogService, remoteClientService);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static LoginInfoEvent event(String status) {
        LoginInfoEvent event = new LoginInfoEvent();
        event.setUsername("admin");
        event.setStatus(status);
        event.setMessage("msg");
        return event;
    }

    private RemoteLoginInfoBo saved() {
        ArgumentCaptor<RemoteLoginInfoBo> captor = ArgumentCaptor.forClass(RemoteLoginInfoBo.class);
        verify(remoteLogService).saveLoginInfo(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("记录客户端、IP、归属地与浏览器；登录成功记为成功")
    void savesLoginInfo() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.0.0.9");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36");
        request.addHeader("clientid", "client-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RemoteClientVo client = new RemoteClientVo();
        client.setClientKey("pc");
        client.setDeviceType("pc");
        when(remoteClientService.queryByClientId("client-1")).thenReturn(client);

        listener.saveLoginInfo(event(Constants.LOGIN_SUCCESS));

        RemoteLoginInfoBo bo = saved();
        assertThat(bo.getUserName()).isEqualTo("admin");
        assertThat(bo.getIpaddr()).isEqualTo("10.0.0.9");
        assertThat(bo.getLoginLocation()).isEqualTo("内网IP");
        assertThat(bo.getClientKey()).isEqualTo("pc");
        assertThat(bo.getBrowser()).isEqualTo("Chrome");
        assertThat(bo.getStatus()).isEqualTo(Constants.SUCCESS);
    }

    @Test
    @DisplayName("请求没有 User-Agent 时照常记录，浏览器与系统为空；登录失败记为失败")
    void withoutUserAgent() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        listener.saveLoginInfo(event(Constants.LOGIN_FAIL));

        RemoteLoginInfoBo bo = saved();
        assertThat(bo.getBrowser()).isNull();
        assertThat(bo.getOs()).isNull();
        assertThat(bo.getClientKey()).isNull();
        assertThat(bo.getStatus()).isEqualTo(Constants.FAIL);
    }

}
