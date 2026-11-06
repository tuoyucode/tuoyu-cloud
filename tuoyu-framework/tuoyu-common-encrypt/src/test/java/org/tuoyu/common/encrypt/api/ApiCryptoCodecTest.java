package org.tuoyu.common.encrypt.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("接口加密协议 v1")
class ApiCryptoCodecTest {

    private static final ApiCryptoKeyRing KEY_RING = ApiCryptoVectors.keyRing();

    @Test
    @DisplayName("测试向量：公钥与 kid 一致")
    void vectorKeyShouldMatch() {
        ApiCryptoKey key = KEY_RING.current();
        assertEquals(ApiCryptoVectors.ROOT.get("kid").asString(), key.kid());
        assertEquals(ApiCryptoVectors.ROOT.get("publicKey").asString(), key.publicKeyBase64());
        assertEquals(3072, key.keySize());
    }

    @Test
    @DisplayName("测试向量：解密请求得到原文与客户端的 AES 密钥")
    void shouldDecryptVectorRequest() {
        ApiCryptoCodec.DecryptedRequest decrypted = ApiCryptoCodec.decryptRequest(ApiCryptoVectors.requestEnvelope(), KEY_RING);

        JsonNode request = ApiCryptoVectors.request();
        assertEquals(request.get("plaintext").asString(), new String(decrypted.body(), StandardCharsets.UTF_8));
        assertArrayEquals(ApiCryptoVectors.bytes(request.get("aesKey")), decrypted.aesKey());
        assertEquals(request.get("envelope").get("nonce").asString(), decrypted.nonce());
    }

    @Test
    @DisplayName("测试向量：给定密钥与随机数时，请求密文、AAD 与向量完全一致")
    void requestCiphertextShouldMatchVector() {
        JsonNode request = ApiCryptoVectors.request();
        JsonNode expected = request.get("envelope");
        ApiCryptoEnvelope envelope = ApiCryptoCodec.encryptRequest(
            request.get("plaintext").asString().getBytes(StandardCharsets.UTF_8), KEY_RING.current().publicKey(),
            ApiCryptoVectors.bytes(request.get("aesKey")), ApiCryptoVectors.bytes(expected.get("iv")),
            expected.get("ts").asLong(), ApiCryptoVectors.bytes(expected.get("nonce")));

        assertEquals(expected.get("data").asString(), envelope.data());
        assertEquals(expected.get("kid").asString(), envelope.kid());
        assertEquals(request.get("aad").asString(),
            new String(ApiCryptoCodec.requestAad(envelope.kid(), envelope.ts(), envelope.nonce()), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("测试向量：响应密文与向量一致，客户端可解密")
    void responseShouldMatchVector() {
        JsonNode response = ApiCryptoVectors.response();
        byte[] aesKey = ApiCryptoVectors.bytes(ApiCryptoVectors.request().get("aesKey"));
        String nonce = ApiCryptoVectors.request().get("envelope").get("nonce").asString();
        byte[] plaintext = response.get("plaintext").asString().getBytes(StandardCharsets.UTF_8);

        ApiCryptoResponseEnvelope envelope = ApiCryptoCodec.encryptResponse(
            plaintext, aesKey, nonce, ApiCryptoVectors.bytes(response.get("envelope").get("iv")));

        assertEquals(response.get("envelope").get("data").asString(), envelope.data());
        assertEquals(response.get("aad").asString(), new String(ApiCryptoCodec.responseAad(nonce), StandardCharsets.UTF_8));
        assertArrayEquals(plaintext, ApiCryptoCodec.decryptResponse(envelope, aesKey, nonce));
    }

    @Test
    @DisplayName("随机生成的请求可以往返；每次的 iv 与包装后的密钥都不同")
    void shouldRoundTripFreshRequests() {
        byte[] body = "{\"oldPassword\":\"a\",\"newPassword\":\"b\"}".getBytes(StandardCharsets.UTF_8);
        ApiCryptoEnvelope first = fresh(body, System.currentTimeMillis());
        ApiCryptoEnvelope second = fresh(body, System.currentTimeMillis());

        assertArrayEquals(body, ApiCryptoCodec.decryptRequest(first, KEY_RING).body());
        assertTrue(!first.key().equals(second.key()) && !first.iv().equals(second.iv()));
    }

    @Test
    @DisplayName("密文、时间戳、nonce、kid 任一被改动，都只返回统一的“请求解密失败”")
    void shouldRejectTampering() {
        ApiCryptoEnvelope e = ApiCryptoVectors.requestEnvelope();
        byte[] data = Base64.getDecoder().decode(e.data());
        data[0] ^= 1;
        String flipped = Base64.getEncoder().encodeToString(data);

        assertDecryptFails(new ApiCryptoEnvelope(e.v(), e.kid(), e.key(), e.iv(), e.ts(), e.nonce(), flipped));
        assertDecryptFails(new ApiCryptoEnvelope(e.v(), e.kid(), e.key(), e.iv(), e.ts() + 1, e.nonce(), e.data()));
        String otherNonce = Base64.getEncoder().encodeToString(new byte[16]);
        assertDecryptFails(new ApiCryptoEnvelope(e.v(), e.kid(), e.key(), e.iv(), e.ts(), otherNonce, e.data()));
        assertDecryptFails(new ApiCryptoEnvelope(e.v(), "unknown-kid-0000", e.key(), e.iv(), e.ts(), e.nonce(), e.data()));
        assertDecryptFails(new ApiCryptoEnvelope(e.v(), e.kid(), e.data(), e.iv(), e.ts(), e.nonce(), e.data()));
    }

    @Test
    @DisplayName("信封格式错误：版本、字段长度、Base64、缺少字段")
    void shouldRejectMalformedEnvelope() {
        ApiCryptoEnvelope e = ApiCryptoVectors.requestEnvelope();
        String shortIv = Base64.getEncoder().encodeToString(new byte[8]);

        assertDecryptFails(null);
        assertDecryptFails(new ApiCryptoEnvelope(2, e.kid(), e.key(), e.iv(), e.ts(), e.nonce(), e.data()));
        assertDecryptFails(new ApiCryptoEnvelope(e.v(), e.kid(), e.key(), shortIv, e.ts(), e.nonce(), e.data()));
        assertDecryptFails(new ApiCryptoEnvelope(e.v(), e.kid(), e.key(), e.iv(), e.ts(), e.nonce(), "不是Base64"));
        assertDecryptFails(new ApiCryptoEnvelope(e.v(), e.kid(), null, e.iv(), e.ts(), e.nonce(), e.data()));
        assertDecryptFails(new ApiCryptoEnvelope(e.v(), e.kid(), e.key(), e.iv(), null, e.nonce(), e.data()));
    }

    @Test
    @DisplayName("响应用错误的密钥或 nonce 解密会失败")
    void responseShouldBeBoundToRequest() {
        byte[] aesKey = ApiCryptoCodec.randomBytes(32);
        ApiCryptoResponseEnvelope envelope = ApiCryptoCodec.encryptResponse("{}".getBytes(StandardCharsets.UTF_8), aesKey, "bm9uY2U=");

        assertThrows(ApiCryptoException.class, () -> ApiCryptoCodec.decryptResponse(envelope, aesKey, "b3RoZXI="));
        assertThrows(ApiCryptoException.class, () -> ApiCryptoCodec.decryptResponse(envelope, ApiCryptoCodec.randomBytes(32), "bm9uY2U="));
    }

    @Test
    @DisplayName("轮换期间：用上一把密钥加密的请求仍可解密，kid 不认识的请求被拒绝")
    void shouldAcceptPreviousKeyDuringRotation() throws Exception {
        String newKey = generatePrivateKey(2048);
        ApiCryptoProperties properties = new ApiCryptoProperties();
        properties.setPrivateKey(newKey);
        properties.setPreviousPrivateKey(ApiCryptoVectors.privateKey());
        ApiCryptoKeyRing rotating = ApiCryptoKeyRing.from(properties);

        ApiCryptoCodec.DecryptedRequest decrypted = ApiCryptoCodec.decryptRequest(ApiCryptoVectors.requestEnvelope(), rotating);
        assertEquals(KEY_RING.current().kid(), decrypted.kid());

        properties.setPreviousPrivateKey(null);
        ApiCryptoKeyRing rotated = ApiCryptoKeyRing.from(properties);
        assertThrows(ApiCryptoException.class, () -> ApiCryptoCodec.decryptRequest(ApiCryptoVectors.requestEnvelope(), rotated));
    }

    @Test
    @DisplayName("密钥配置：缺失、无效或少于 2048 位时拒绝启动")
    void shouldRejectInvalidKeys() throws Exception {
        ApiCryptoProperties properties = new ApiCryptoProperties();
        assertTrue(assertThrows(IllegalStateException.class, () -> ApiCryptoKeyRing.from(properties))
            .getMessage().contains("TUOYU_API_CRYPTO_PRIVATE_KEY"));

        properties.setPrivateKey("bm90LWEta2V5");
        assertThrows(IllegalStateException.class, () -> ApiCryptoKeyRing.from(properties));

        properties.setPrivateKey(generatePrivateKey(1024));
        assertTrue(assertThrows(IllegalStateException.class, () -> ApiCryptoKeyRing.from(properties))
            .getMessage().contains("至少需要 2048 位"));
    }

    private static ApiCryptoEnvelope fresh(byte[] body, long ts) {
        return ApiCryptoCodec.encryptRequest(body, KEY_RING.current().publicKey(), ApiCryptoCodec.randomBytes(32),
            ApiCryptoCodec.randomBytes(12), ts, ApiCryptoCodec.randomBytes(16));
    }

    private static void assertDecryptFails(ApiCryptoEnvelope envelope) {
        ApiCryptoException e = assertThrows(ApiCryptoException.class, () -> ApiCryptoCodec.decryptRequest(envelope, KEY_RING));
        assertEquals(ApiCryptoException.DECRYPT_FAILED, e.getMessage());
    }

    static String generatePrivateKey(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    }

}
