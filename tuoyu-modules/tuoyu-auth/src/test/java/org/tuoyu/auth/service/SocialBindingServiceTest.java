package org.tuoyu.auth.service;

import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.UserContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.system.api.RemoteSocialService;
import org.tuoyu.system.api.domain.bo.RemoteSocialBo;
import org.tuoyu.system.api.domain.vo.RemoteSocialVo;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("绑定与解绑第三方账号")
class SocialBindingServiceTest {

    private final RemoteSocialService remoteSocialService = mock(RemoteSocialService.class);

    private final SocialBindingService service = new SocialBindingService(remoteSocialService);

    private UserContextProvider previousProvider;

    @BeforeEach
    void setUp() {
        previousProvider = UserContext.getProvider();
    }

    @AfterEach
    void tearDown() {
        UserContext.setProvider(previousProvider);
    }

    private static void actAs(long userId) {
        CurrentUser user = new CurrentUser();
        user.setUserId(userId);
        UserContext.setProvider(() -> Optional.of(user));
    }

    private static AuthUser authUser() {
        AuthToken token = new AuthToken();
        token.setAccessToken("at");
        token.setExpireIn(7200);
        AuthUser user = new AuthUser();
        user.setSource("gitee");
        user.setUuid("u-1");
        user.setUsername("gitee-user");
        user.setNickname("Gitee User");
        user.setEmail("u1@example.com");
        user.setToken(token);
        return user;
    }

    @Test
    @DisplayName("绑定第三方账号：已被其他用户绑定时拒绝")
    void socialAlreadyBound() {
        actAs(3L);
        when(remoteSocialService.selectByAuthId("giteeu-1")).thenReturn(List.of(new RemoteSocialVo()));

        assertThatThrownBy(() -> service.bind(authUser())).isInstanceOf(ServiceException.class)
            .hasMessage("此三方账号已经被绑定!");
        verify(remoteSocialService, never()).insertByBo(any());
        verify(remoteSocialService, never()).updateByBo(any());
    }

    @Test
    @DisplayName("绑定第三方账号：当前用户在该平台没有绑定时新增，绑定的用户是当前登录用户")
    void socialInsert() {
        actAs(3L);
        when(remoteSocialService.selectByAuthId("giteeu-1")).thenReturn(List.of());
        when(remoteSocialService.queryList(any())).thenReturn(List.of());

        service.bind(authUser());

        ArgumentCaptor<RemoteSocialBo> captor = ArgumentCaptor.forClass(RemoteSocialBo.class);
        verify(remoteSocialService).insertByBo(captor.capture());
        RemoteSocialBo bo = captor.getValue();
        assertThat(bo.getUserId()).isEqualTo(3L);
        assertThat(bo.getAuthId()).isEqualTo("giteeu-1");
        assertThat(bo.getOpenId()).isEqualTo("u-1");
        assertThat(bo.getUserName()).isEqualTo("gitee-user");
        assertThat(bo.getNickName()).isEqualTo("Gitee User");
        // 与 RemoteSocialBo 同名的属性从第三方用户与令牌中复制
        assertThat(bo.getSource()).isEqualTo("gitee");
        assertThat(bo.getEmail()).isEqualTo("u1@example.com");
        assertThat(bo.getAccessToken()).isEqualTo("at");
        assertThat(bo.getExpireIn()).isEqualTo(7200);

        ArgumentCaptor<RemoteSocialBo> query = ArgumentCaptor.forClass(RemoteSocialBo.class);
        verify(remoteSocialService).queryList(query.capture());
        assertThat(query.getValue().getUserId()).isEqualTo(3L);
        assertThat(query.getValue().getSource()).isEqualTo("gitee");
    }

    @Test
    @DisplayName("绑定第三方账号：当前用户在该平台已有绑定时更新原记录")
    void socialUpdate() {
        actAs(3L);
        RemoteSocialVo existing = new RemoteSocialVo();
        existing.setId(99L);
        when(remoteSocialService.selectByAuthId("giteeu-1")).thenReturn(List.of());
        when(remoteSocialService.queryList(any())).thenReturn(List.of(existing));

        service.bind(authUser());

        ArgumentCaptor<RemoteSocialBo> captor = ArgumentCaptor.forClass(RemoteSocialBo.class);
        verify(remoteSocialService).updateByBo(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(99L);
        assertThat(captor.getValue().getUserId()).isEqualTo(3L);
        verify(remoteSocialService, never()).insertByBo(any());
    }

    @Test
    @DisplayName("解绑：只能解除本人的绑定，不是本人的拒绝且不删除")
    void unbind() {
        actAs(5L);
        RemoteSocialVo own = new RemoteSocialVo();
        own.setId(7L);
        when(remoteSocialService.queryList(any())).thenReturn(List.of(own));

        service.unbind(7L);
        verify(remoteSocialService).deleteWithValidById(7L);

        assertThatThrownBy(() -> service.unbind(8L)).isInstanceOf(ServiceException.class).hasMessage("取消授权失败");
        assertThatThrownBy(() -> service.unbind(null)).isInstanceOf(ServiceException.class).hasMessage("取消授权失败");
        verify(remoteSocialService, never()).deleteWithValidById(8L);
        ArgumentCaptor<RemoteSocialBo> query = ArgumentCaptor.forClass(RemoteSocialBo.class);
        verify(remoteSocialService, org.mockito.Mockito.atLeastOnce()).queryList(query.capture());
        assertThat(query.getValue().getUserId()).isEqualTo(5L);
    }

}
