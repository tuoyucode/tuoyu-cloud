package org.tuoyu.system.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("第三方账号绑定：接口不下发第三方平台的令牌与密钥")
class SysSocialVoJsonTest {

    @Test
    @DisplayName("序列化时只保留界面需要的字段，令牌、授权码、签名密钥不返回")
    @SuppressWarnings("unchecked")
    void tokensAreNotSerialized() {
        SysSocialVo vo = new SysSocialVo();
        vo.setId(1L);
        vo.setSource("gitee");
        vo.setUserName("alice");
        vo.setNickName("Alice");
        vo.setAccessToken("access-secret");
        vo.setRefreshToken("refresh-secret");
        vo.setAccessCode("access-code");
        vo.setIdToken("id-token");
        vo.setMacKey("mac-key");
        vo.setCode("auth-code");
        vo.setOauthToken("oauth-token");
        vo.setOauthTokenSecret("oauth-secret");

        Map<String, Object> json = JsonMapper.builder().build().convertValue(vo, Map.class);

        assertThat(json).containsKeys("id", "source", "userName", "nickName");
        assertThat(json).doesNotContainKeys("accessToken", "refreshToken", "accessCode", "idToken", "macKey",
            "code", "oauthToken", "oauthTokenSecret");
        assertThat(json.toString()).doesNotContain("secret", "access-code", "id-token", "mac-key", "auth-code", "oauth-token");
    }

}
