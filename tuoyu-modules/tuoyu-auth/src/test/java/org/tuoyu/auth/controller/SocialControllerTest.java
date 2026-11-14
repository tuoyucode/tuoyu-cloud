package org.tuoyu.auth.controller;

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.i18n.LocaleContextHolder;
import org.tuoyu.auth.form.SocialLoginBody;
import org.tuoyu.auth.service.SocialBindingService;
import org.tuoyu.auth.support.AuthTestContext;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.web.ApiErrors;
import org.tuoyu.common.social.config.properties.SocialLoginConfigProperties;
import org.tuoyu.common.social.config.properties.SocialProperties;
import org.tuoyu.common.social.utils.SocialUtils;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("第三方平台接口：授权地址、已配置的平台、绑定回调与解绑")
class SocialControllerTest {

    private final SocialProperties socialProperties = new SocialProperties();
    private final RemoteSocialService remoteSocialService = mock(RemoteSocialService.class);
    private final SocialBindingService socialBindingService = spy(new SocialBindingService(remoteSocialService));

    private SocialController controller;

    @BeforeAll
    static void initContext() {
        AuthTestContext.init();
    }

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        controller = new SocialController(socialProperties, socialBindingService);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    /**
     * 业务拒绝抛出不带错误码的 ServiceException，由全局异常处理映射为 HTTP 400
     */
    private static void assertBadRequest(ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ServiceException.class, e -> {
            assertThat(e.getMessage()).isEqualTo(message);
            assertThat(ApiErrors.of(e).httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
    }

    @Test
    @DisplayName("第三方绑定：不支持、未填写应用 ID 与密钥的平台拒绝；支持时返回授权地址；回调失败返回原因，成功时绑定")
    void socialBinding() {
        socialProperties.setType(Map.of());
        assertBadRequest(() -> controller.authorizeUrl("gitee"), "gitee平台账号暂不支持");
        socialProperties.setType(Map.of("gitee", new SocialLoginConfigProperties()));
        assertBadRequest(() -> controller.authorizeUrl("gitee"), "gitee平台账号暂不支持");

        SocialLoginConfigProperties gitee = new SocialLoginConfigProperties();
        gitee.setClientId("client-id");
        gitee.setClientSecret("client-secret");
        socialProperties.setType(Map.of("gitee", gitee));
        AuthRequest request = mock(AuthRequest.class);
        when(request.authorize(anyString())).thenReturn("https://gitee.example/authorize");
        SocialLoginBody body = new SocialLoginBody("gitee", "code", "state");
        AuthUser authUser = new AuthUser();
        doNothing().when(socialBindingService).bind(any());
        try (MockedStatic<SocialUtils> social = mockStatic(SocialUtils.class)) {
            social.when(() -> SocialUtils.getAuthRequest("gitee", socialProperties)).thenReturn(request);
            assertThat(controller.authorizeUrl("gitee").getData()).isEqualTo("https://gitee.example/authorize");

            social.when(() -> SocialUtils.loginAuth("gitee", "code", "state", socialProperties))
                .thenReturn(AuthResponse.<AuthUser>builder().code(5000).msg("授权失败").build());
            assertBadRequest(() -> controller.bindCallback(body), "授权失败");
            verify(socialBindingService, never()).bind(any());

            social.when(() -> SocialUtils.loginAuth("gitee", "code", "state", socialProperties))
                .thenReturn(AuthResponse.<AuthUser>builder().code(2000).data(authUser).build());
            assertThat(controller.bindCallback(body).getCode()).isEqualTo(ApiResult.SUCCESS);
            verify(socialBindingService).bind(authUser);
        }
    }

    @Test
    @DisplayName("已配置的第三方平台：只列出应用 ID 与密钥都已填写的平台，按配置顺序；没有配置时为空")
    void socialSources() {
        assertThat(controller.configuredSources().getData()).isEmpty();

        Map<String, SocialLoginConfigProperties> type = new LinkedHashMap<>();
        type.put("qq", new SocialLoginConfigProperties());
        for (String source : List.of("gitee", "dingtalk")) {
            SocialLoginConfigProperties config = new SocialLoginConfigProperties();
            config.setClientId(source + "-id");
            config.setClientSecret(source + "-secret");
            type.put(source, config);
        }
        SocialLoginConfigProperties noSecret = new SocialLoginConfigProperties();
        noSecret.setClientId("weibo-id");
        noSecret.setClientSecret(" ");
        type.put("weibo", noSecret);
        socialProperties.setType(type);

        assertThat(controller.configuredSources().getData()).containsExactly("gitee", "dingtalk");
    }

    @Test
    @DisplayName("取消授权：非本人拒绝、删除失败带回原因")
    void unlock() {
        // 只能解绑本人的第三方绑定：先按当前用户查询绑定，不是本人的直接拒绝，不执行删除
        RemoteSocialVo own1 = new RemoteSocialVo();
        own1.setId(1L);
        RemoteSocialVo own2 = new RemoteSocialVo();
        own2.setId(2L);
        when(remoteSocialService.queryList(any())).thenReturn(List.of(own1, own2));
        doThrow(new ServiceException("第三方账号绑定不存在或已解除")).when(remoteSocialService).deleteWithValidById(2L);
        CurrentUser user = new CurrentUser();
        user.setUserId(100L);
        UserContextProvider previous = UserContext.setProvider(() -> Optional.of(user));
        try {
            assertThat(controller.unbind(1L).getCode()).isEqualTo(ApiResult.SUCCESS);
            assertBadRequest(() -> controller.unbind(2L), "第三方账号绑定不存在或已解除");
            assertBadRequest(() -> controller.unbind(3L), "取消授权失败");
        } finally {
            UserContext.setProvider(previous);
        }
        ArgumentCaptor<RemoteSocialBo> query = ArgumentCaptor.forClass(RemoteSocialBo.class);
        verify(remoteSocialService, atLeastOnce()).queryList(query.capture());
        assertThat(query.getValue().getUserId()).isEqualTo(100L);
        verify(remoteSocialService).deleteWithValidById(1L);
        verify(remoteSocialService, never()).deleteWithValidById(3L);
    }

}
