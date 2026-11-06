package org.tuoyu.common.encrypt.api;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/**
 * 接口加密协议 v1 的加解密实现（RSA-OAEP-256 + AES-256-GCM），协议见 docs/adr/0001-接口加密.md。
 * <p>
 * 只负责密码学部分；时间窗口与 nonce 防重放由 {@link ApiCryptoFilter} 在解密成功后检查。
 * 客户端方向的方法（{@link #encryptRequest}、{@link #decryptResponse}）是协议的参考实现，供测试使用。
 *
 * @author tuoyu
 */
public final class ApiCryptoCodec {

    public static final int VERSION = 1;

    public static final String ALGORITHM = "RSA-OAEP-256+A256GCM";

    static final int AES_KEY_BYTES = 32;

    static final int IV_BYTES = 12;

    static final int NONCE_BYTES = 16;

    private static final int TAG_BITS = 128;

    private static final String REQUEST_AAD_PREFIX = "tuoyu-api-crypto/v1/request|";

    private static final String RESPONSE_AAD_PREFIX = "tuoyu-api-crypto/v1/response|";

    /**
     * 哈希与 MGF1 都用 SHA-256，与 WebCrypto 的 RSA-OAEP 一致。
     * 注意：JDK 的 “OAEPWithSHA-256AndMGF1Padding” 默认 MGF1 使用 SHA-1，必须显式指定参数
     */
    private static final OAEPParameterSpec OAEP_SHA256 =
        new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiCryptoCodec() {
    }

    /**
     * 解密结果
     *
     * @param body   原始请求体
     * @param aesKey 客户端生成的 AES 密钥，响应加密时复用
     * @param kid    使用的密钥
     * @param ts     客户端时间戳
     * @param nonce  随机数（Base64 文本）
     */
    public record DecryptedRequest(byte[] body, byte[] aesKey, String kid, long ts, String nonce) {
    }

    /**
     * 解密请求。成功即表示密文完整、未被篡改；时间与 nonce 需要调用方另行检查
     *
     * @throws ApiCryptoException 信封无效或解密失败
     */
    public static DecryptedRequest decryptRequest(ApiCryptoEnvelope envelope, ApiCryptoKeyRing keyRing) {
        if (envelope == null || envelope.v() == null || envelope.v() != VERSION) {
            throw ApiCryptoException.invalid("不支持的协议版本");
        }
        if (envelope.ts() == null) {
            throw ApiCryptoException.invalid("缺少时间戳");
        }
        ApiCryptoKey key = keyRing.find(envelope.kid())
            .orElseThrow(() -> ApiCryptoException.invalid("未知的 kid：" + envelope.kid()));
        byte[] iv = decode(envelope.iv(), "iv", IV_BYTES);
        decode(envelope.nonce(), "nonce", NONCE_BYTES);
        byte[] wrappedKey = decode(envelope.key(), "key", -1);
        byte[] ciphertext = decode(envelope.data(), "data", -1);

        byte[] aesKey;
        try {
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.DECRYPT_MODE, key.privateKey(), OAEP_SHA256);
            aesKey = rsa.doFinal(wrappedKey);
        } catch (GeneralSecurityException e) {
            throw ApiCryptoException.invalid("RSA-OAEP 解密 AES 密钥失败", e);
        }
        if (aesKey.length != AES_KEY_BYTES) {
            throw ApiCryptoException.invalid("AES 密钥长度应为 32 字节，实际为 " + aesKey.length);
        }
        byte[] body;
        try {
            body = gcm(Cipher.DECRYPT_MODE, aesKey, iv, requestAad(key.kid(), envelope.ts(), envelope.nonce()), ciphertext);
        } catch (GeneralSecurityException e) {
            throw ApiCryptoException.invalid("AES-GCM 解密或认证失败", e);
        }
        return new DecryptedRequest(body, aesKey, key.kid(), envelope.ts(), envelope.nonce());
    }

    /**
     * 用请求中的 AES 密钥加密响应，每次生成新的 iv
     */
    public static ApiCryptoResponseEnvelope encryptResponse(byte[] body, byte[] aesKey, String nonce) {
        return encryptResponse(body, aesKey, nonce, randomBytes(IV_BYTES));
    }

    static ApiCryptoResponseEnvelope encryptResponse(byte[] body, byte[] aesKey, String nonce, byte[] iv) {
        try {
            byte[] data = gcm(Cipher.ENCRYPT_MODE, aesKey, iv, responseAad(nonce), body);
            return new ApiCryptoResponseEnvelope(VERSION, encode(iv), encode(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("加密响应失败", e);
        }
    }

    /**
     * 客户端方向的参考实现：加密请求
     */
    public static ApiCryptoEnvelope encryptRequest(byte[] body, RSAPublicKey publicKey, byte[] aesKey,
                                                   byte[] iv, long ts, byte[] nonce) {
        String kid = ApiCryptoKey.kidOf(publicKey.getEncoded());
        String nonceText = encode(nonce);
        try {
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SHA256);
            byte[] wrappedKey = rsa.doFinal(aesKey);
            byte[] data = gcm(Cipher.ENCRYPT_MODE, aesKey, iv, requestAad(kid, ts, nonceText), body);
            return new ApiCryptoEnvelope(VERSION, kid, encode(wrappedKey), encode(iv), ts, nonceText, encode(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("加密请求失败", e);
        }
    }

    /**
     * 客户端方向的参考实现：解密响应
     *
     * @throws ApiCryptoException 解密或认证失败
     */
    public static byte[] decryptResponse(ApiCryptoResponseEnvelope envelope, byte[] aesKey, String nonce) {
        if (envelope == null || envelope.v() != VERSION) {
            throw ApiCryptoException.invalid("不支持的协议版本");
        }
        try {
            return gcm(Cipher.DECRYPT_MODE, aesKey, decode(envelope.iv(), "iv", IV_BYTES), responseAad(nonce),
                decode(envelope.data(), "data", -1));
        } catch (GeneralSecurityException e) {
            throw ApiCryptoException.invalid("AES-GCM 解密或认证失败", e);
        }
    }

    /**
     * 生成安全随机数
     */
    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    static byte[] requestAad(String kid, long ts, String nonce) {
        return (REQUEST_AAD_PREFIX + kid + "|" + ts + "|" + nonce).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] responseAad(String nonce) {
        return (RESPONSE_AAD_PREFIX + nonce).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] gcm(int mode, byte[] aesKey, byte[] iv, byte[] aad, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * @param expectedLength 期望的字节数，负数表示不限
     */
    private static byte[] decode(String text, String field, int expectedLength) {
        if (text == null || text.isEmpty()) {
            throw ApiCryptoException.invalid("缺少字段 " + field);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw ApiCryptoException.invalid("字段 " + field + " 不是有效的 Base64");
        }
        if (expectedLength >= 0 && bytes.length != expectedLength) {
            throw ApiCryptoException.invalid("字段 " + field + " 应为 " + expectedLength + " 字节，实际为 " + bytes.length);
        }
        if (expectedLength < 0 && bytes.length == 0) {
            throw ApiCryptoException.invalid("字段 " + field + " 为空");
        }
        return bytes;
    }

}
