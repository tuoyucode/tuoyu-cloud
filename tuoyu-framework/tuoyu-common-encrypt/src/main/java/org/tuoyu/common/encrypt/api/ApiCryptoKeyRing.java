package org.tuoyu.common.encrypt.api;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * 接口加密的密钥组：当前密钥，以及轮换期间仍然有效的上一把密钥
 *
 * @author tuoyu
 */
public final class ApiCryptoKeyRing {

    private final ApiCryptoKey current;

    private final ApiCryptoKey previous;

    public ApiCryptoKeyRing(ApiCryptoKey current, ApiCryptoKey previous) {
        this.current = current;
        this.previous = previous;
    }

    /**
     * 按配置创建
     *
     * @throws IllegalStateException 未配置当前私钥，或私钥无效
     */
    public static ApiCryptoKeyRing from(ApiCryptoProperties properties) {
        if (StringUtils.isBlank(properties.getPrivateKey())) {
            throw new IllegalStateException("已开启接口加密（tuoyu.api-crypto.enabled），但未配置 tuoyu.api-crypto.private-key。"
                + "请设置环境变量 TUOYU_API_CRYPTO_PRIVATE_KEY，可用 java tools/secrets/GenerateEnv.java 生成");
        }
        ApiCryptoKey current = ApiCryptoKey.parse(properties.getPrivateKey(), "tuoyu.api-crypto.private-key");
        ApiCryptoKey previous = StringUtils.isBlank(properties.getPreviousPrivateKey()) ? null
            : ApiCryptoKey.parse(properties.getPreviousPrivateKey(), "tuoyu.api-crypto.previous-private-key");
        if (previous != null && previous.kid().equals(current.kid())) {
            previous = null;
        }
        return new ApiCryptoKeyRing(current, previous);
    }

    public ApiCryptoKey current() {
        return current;
    }

    public Optional<ApiCryptoKey> previous() {
        return Optional.ofNullable(previous);
    }

    /**
     * 按 kid 查找有效的密钥
     */
    public Optional<ApiCryptoKey> find(String kid) {
        if (current.kid().equals(kid)) {
            return Optional.of(current);
        }
        if (previous != null && previous.kid().equals(kid)) {
            return Optional.of(previous);
        }
        return Optional.empty();
    }

}
