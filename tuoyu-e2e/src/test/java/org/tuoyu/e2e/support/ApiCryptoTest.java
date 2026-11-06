package org.tuoyu.e2e.support;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用后端共用的测试向量（tuoyu-common-encrypt 的 api-crypto/test-vectors.json）校验端到端客户端的加密实现。
 * 不需要被测系统，在 {@code mvn -P e2e -pl tuoyu-e2e test} 中运行。
 *
 * @author tuoyu
 */
class ApiCryptoTest {

    private static JsonNode vectors;

    private static ApiCrypto.ServerKey serverKey;

    @BeforeAll
    static void loadVectors() throws IOException {
        try (InputStream in = ApiCryptoTest.class.getResourceAsStream("/api-crypto/test-vectors.json")) {
            assertThat(in).as("测试向量 api-crypto/test-vectors.json（由 pom 从 tuoyu-common-encrypt 引入）").isNotNull();
            vectors = JsonMapper.builder().build().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        serverKey = ApiCrypto.parsePublicKey(vectors.path("publicKey").asString());
    }

    @Test
    void kidIsDerivedFromPublicKey() {
        assertThat(serverKey.kid()).isEqualTo(vectors.path("kid").asString()).hasSize(16);
        assertThat(vectors.path("algorithm").asString()).isEqualTo(ApiCrypto.ALGORITHM);
    }

    @Test
    void requestMatchesVector() {
        JsonNode request = vectors.path("request");
        JsonNode expected = request.path("envelope");

        ApiCrypto.Sealed sealed = ApiCrypto.seal(utf8(request.path("plaintext").asString()), serverKey,
            b64(request.path("aesKey").asString()), b64(expected.path("iv").asString()),
            expected.path("ts").asLong(), b64(expected.path("nonce").asString()));
        JsonNode actual = JsonMapper.builder().build().readTree(sealed.body());

        // AES-GCM 部分是确定的，必须与向量逐字一致（同时校验了 AAD 的拼法）
        assertThat(actual.path("v").asInt()).isEqualTo(expected.path("v").asInt());
        assertThat(actual.path("kid").asString()).isEqualTo(expected.path("kid").asString());
        assertThat(actual.path("iv").asString()).isEqualTo(expected.path("iv").asString());
        assertThat(actual.path("ts").asLong()).isEqualTo(expected.path("ts").asLong());
        assertThat(actual.path("nonce").asString()).isEqualTo(expected.path("nonce").asString());
        assertThat(actual.path("data").asString()).isEqualTo(expected.path("data").asString());
        assertThat(new String(ApiCrypto.requestAad(serverKey.kid(), expected.path("ts").asLong(),
            expected.path("nonce").asString()), StandardCharsets.UTF_8)).isEqualTo(request.path("aad").asString());
        assertThat(sealed.nonce()).isEqualTo(expected.path("nonce").asString());
    }

    @Test
    void wrappedKeyUsesOaepWithSha256AndMgf1Sha256() throws Exception {
        // RSA-OAEP 带随机数，密文与向量不同；用向量中的私钥按协议参数解开，能还原 AES 密钥即说明参数正确
        // （如果客户端误用了 MGF1-SHA1，这里会解密失败）
        byte[] aesKey = b64(vectors.path("request").path("aesKey").asString());
        ApiCrypto.Sealed sealed = ApiCrypto.seal(utf8("{}"), serverKey, aesKey, new byte[12], 1L, new byte[16]);
        String wrapped = JsonMapper.builder().build().readTree(sealed.body()).path("key").asString();

        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey(),
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        assertThat(rsa.doFinal(b64(wrapped))).isEqualTo(aesKey);
        assertThat(sealed.aesKey()).isEqualTo(aesKey);
    }

    @Test
    void randomRequestsDiffer() {
        ApiCrypto.Sealed first = ApiCrypto.seal(utf8("{}"), serverKey);
        ApiCrypto.Sealed second = ApiCrypto.seal(utf8("{}"), serverKey);
        assertThat(first.aesKey()).hasSize(32).isNotEqualTo(second.aesKey());
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
    }

    @Test
    void responseMatchesVector() throws Exception {
        JsonNode response = vectors.path("response");
        JsonNode envelope = response.path("envelope");
        byte[] plain = ApiCrypto.open(envelope.path("iv").asString(), envelope.path("data").asString(),
            b64(vectors.path("request").path("aesKey").asString()), vectors.path("request").path("envelope").path("nonce").asString());
        assertThat(new String(plain, StandardCharsets.UTF_8)).isEqualTo(response.path("plaintext").asString());
        assertThat(new String(ApiCrypto.responseAad(vectors.path("request").path("envelope").path("nonce").asString()),
            StandardCharsets.UTF_8)).isEqualTo(response.path("aad").asString());
    }

    @Test
    void tamperedResponseIsRejected() {
        JsonNode envelope = vectors.path("response").path("envelope");
        byte[] aesKey = b64(vectors.path("request").path("aesKey").asString());
        String nonce = vectors.path("request").path("envelope").path("nonce").asString();
        byte[] data = b64(envelope.path("data").asString());
        data[0] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(data);

        assertThatThrownBy(() -> ApiCrypto.open(envelope.path("iv").asString(), tampered, aesKey, nonce))
            .isInstanceOf(AEADBadTagException.class);
        // nonce 不同（AAD 不同）同样认证失败
        assertThatThrownBy(() -> ApiCrypto.open(envelope.path("iv").asString(), envelope.path("data").asString(), aesKey,
            Base64.getEncoder().encodeToString(new byte[16])))
            .isInstanceOf(AEADBadTagException.class);
    }

    private static PrivateKey privateKey() throws Exception {
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(b64(vectors.path("privateKey").asString())));
    }

    private static byte[] b64(String text) {
        return Base64.getDecoder().decode(text);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
