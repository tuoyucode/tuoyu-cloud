package org.tuoyu.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.auth.domain.vo.ApiCryptoPublicKeyVo;
import org.tuoyu.common.encrypt.api.ApiCryptoCodec;
import org.tuoyu.common.encrypt.api.ApiCryptoKey;
import org.tuoyu.common.encrypt.api.ApiCryptoKeyRing;
import org.tuoyu.common.encrypt.api.ApiCryptoProperties;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("公钥接口：只下发公钥与协议参数，未启用时 enabled=false")
class ApiCryptoControllerTest {

    @Test
    @DisplayName("未启用接口加密时只返回 enabled=false")
    void disabled() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        ApiCryptoController controller = new ApiCryptoController(beans.getBeanProvider(ApiCryptoKeyRing.class),
            beans.getBeanProvider(ApiCryptoProperties.class));

        ApiCryptoPublicKeyVo vo = controller.publicKey().getData();
        assertThat(vo.getEnabled()).isFalse();
        assertThat(vo.getPublicKey()).isNull();
        assertThat(vo.getKid()).isNull();
    }

    @Test
    @DisplayName("启用时返回当前密钥的公钥、kid、算法、HTTPS 要求与防重放窗口，不含私钥")
    void enabled() throws Exception {
        // 测试密钥运行时生成，不写入仓库
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(ApiCryptoKey.MIN_KEY_SIZE);
        String pkcs8 = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        ApiCryptoKey key = ApiCryptoKey.parse(pkcs8, "test");
        ApiCryptoProperties properties = new ApiCryptoProperties();
        properties.setRequireHttps(false);
        properties.setReplayWindow(Duration.ofMinutes(3));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("ring", new ApiCryptoKeyRing(key, null));
        beans.addBean("properties", properties);

        ApiCryptoPublicKeyVo vo = new ApiCryptoController(beans.getBeanProvider(ApiCryptoKeyRing.class),
            beans.getBeanProvider(ApiCryptoProperties.class)).publicKey().getData();

        assertThat(vo.getEnabled()).isTrue();
        assertThat(vo.getVersion()).isEqualTo(ApiCryptoCodec.VERSION);
        assertThat(vo.getAlgorithm()).isEqualTo(ApiCryptoCodec.ALGORITHM);
        assertThat(vo.getKid()).isEqualTo(key.kid());
        assertThat(vo.getPublicKey()).isEqualTo(key.publicKeyBase64()).isNotEqualTo(pkcs8);
        assertThat(vo.getRequireHttps()).isFalse();
        assertThat(vo.getReplayWindowSeconds()).isEqualTo(180L);
    }

}
