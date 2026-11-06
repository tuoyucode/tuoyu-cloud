package org.tuoyu.common.encrypt.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * 读取协议测试向量 api-crypto/test-vectors.json。向量由独立的 JCE 代码生成，前端的两条实现路径也使用同一份向量
 */
final class ApiCryptoVectors {

    static final JsonNode ROOT = load();

    private ApiCryptoVectors() {
    }

    static String privateKey() {
        return ROOT.get("privateKey").asString();
    }

    static ApiCryptoKeyRing keyRing() {
        return new ApiCryptoKeyRing(ApiCryptoKey.parse(privateKey(), "test"), null);
    }

    static JsonNode request() {
        return ROOT.get("request");
    }

    static JsonNode response() {
        return ROOT.get("response");
    }

    static ApiCryptoEnvelope requestEnvelope() {
        return new JsonMapper().treeToValue(request().get("envelope"), ApiCryptoEnvelope.class);
    }

    static byte[] bytes(JsonNode node) {
        return Base64.getDecoder().decode(node.asString());
    }

    private static JsonNode load() {
        try (InputStream in = ApiCryptoVectors.class.getResourceAsStream("/api-crypto/test-vectors.json")) {
            return new JsonMapper().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
