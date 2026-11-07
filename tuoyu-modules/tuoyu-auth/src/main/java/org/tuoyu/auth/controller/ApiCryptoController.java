package org.tuoyu.auth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tuoyu.auth.domain.vo.ApiCryptoPublicKeyVo;
import org.tuoyu.common.core.domain.ApiResult;
import org.tuoyu.common.encrypt.api.ApiCryptoCodec;
import org.tuoyu.common.encrypt.api.ApiCryptoKey;
import org.tuoyu.common.encrypt.api.ApiCryptoKeyRing;
import org.tuoyu.common.encrypt.api.ApiCryptoProperties;

/**
 * 接口加密：向前端提供公钥。无需登录，网关白名单中已放行
 *
 * @author tuoyu
 */
@RestController
public class ApiCryptoController {

    private final ObjectProvider<ApiCryptoKeyRing> keyRing;

    private final ObjectProvider<ApiCryptoProperties> properties;

    public ApiCryptoController(ObjectProvider<ApiCryptoKeyRing> keyRing, ObjectProvider<ApiCryptoProperties> properties) {
        this.keyRing = keyRing;
        this.properties = properties;
    }

    /**
     * 获取当前公钥。前端可以缓存；收到“请求解密失败”时应重新获取一次
     */
    @SaIgnore
    @GetMapping("/crypto/public-key")
    public ApiResult<ApiCryptoPublicKeyVo> publicKey() {
        ApiCryptoPublicKeyVo vo = new ApiCryptoPublicKeyVo();
        ApiCryptoKeyRing ring = keyRing.getIfAvailable();
        ApiCryptoProperties config = properties.getIfAvailable();
        vo.setEnabled(ring != null && config != null);
        if (ring == null || config == null) {
            return ApiResult.ok(vo);
        }
        ApiCryptoKey current = ring.current();
        vo.setVersion(ApiCryptoCodec.VERSION);
        vo.setKid(current.kid());
        vo.setAlgorithm(ApiCryptoCodec.ALGORITHM);
        vo.setPublicKey(current.publicKeyBase64());
        vo.setRequireHttps(config.isRequireHttps());
        vo.setReplayWindowSeconds(config.getReplayWindow().toSeconds());
        return ApiResult.ok(vo);
    }

}
