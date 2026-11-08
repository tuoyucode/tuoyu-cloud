package org.tuoyu.e2e.support;

import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接口加密协议 v1 的客户端实现，只用 JDK 的 JCE，不依赖后端代码。
 * <p>
 * 请求：客户端为每个请求随机生成 32 字节 AES 密钥、12 字节 iv、16 字节 nonce，
 * 正文用 AES-256-GCM 加密（AAD 为 {@code tuoyu-api-crypto/v1/request|kid|ts|nonce}），
 * AES 密钥用 RSA-OAEP（哈希与 MGF1 均为 SHA-256）加密。响应沿用请求的 AES 密钥，
 * AAD 为 {@code tuoyu-api-crypto/v1/response|nonce}。
 *
 * @author tuoyu
 */
public final class ApiCrypto {

    /**
     * 加密请求与加密响应都带这个请求头，值为 1
     */
    public static final String HEADER = "X-Tuoyu-Encrypted";

    public static final String ALGORITHM = "RSA-OAEP-256+A256GCM";

    public static final int VERSION = 1;

    private static final int AES_KEY_BYTES = 32;

    private static final int IV_BYTES = 12;

    private static final int NONCE_BYTES = 16;

    private static final int KID_BYTES = 12;

    private static final int TAG_BITS = 128;

    /**
     * 必须显式给出 MGF1 的哈希：JDK 的 “OAEPWithSHA-256AndMGF1Padding” 中 MGF1 默认用 SHA-1，与协议不符
     */
    private static final OAEPParameterSpec OAEP_SHA256 =
        new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ApiCrypto() {
    }

    /**
     * 服务端公钥
     *
     * @param kid       SPKI 的 SHA-256 前 12 字节，Base64URL 无填充
     * @param publicKey RSA 公钥
     */
    public record ServerKey(String kid, RSAPublicKey publicKey) {
    }

    /**
     * 加密后的请求
     *
     * @param body   请求体（信封 JSON）
     * @param aesKey 本次请求的 AES 密钥，用于解密响应
     * @param nonce  本次请求的 nonce（Base64 文本），用于解密响应
     */
    public record Sealed(String body, byte[] aesKey, String nonce) {
    }

    /**
     * 解析公钥接口返回的 SPKI（标准 Base64），并按协议计算 kid
     */
    public static ServerKey parsePublicKey(String spkiBase64) {
        byte[] spki = Base64.getDecoder().decode(spkiBase64);
        try {
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki));
            return new ServerKey(kidOf(spki), key);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("公钥格式不正确", e);
        }
    }

    /**
     * kid = SHA-256(SPKI DER) 的前 12 字节，Base64URL 无填充（16 个字符）
     */
    public static String kidOf(byte[] spki) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(spki);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, KID_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 加密请求：随机生成密钥、iv、nonce，时间戳取当前时间
     */
    public static Sealed seal(byte[] plaintext, ServerKey key) {
        return seal(plaintext, key, random(AES_KEY_BYTES), random(IV_BYTES), System.currentTimeMillis(), random(NONCE_BYTES));
    }

    /**
     * 加密请求（参数全部由调用方给出，供测试向量校验）
     */
    public static Sealed seal(byte[] plaintext, ServerKey key, byte[] aesKey, byte[] iv, long ts, byte[] nonce) {
        if (aesKey.length != AES_KEY_BYTES || iv.length != IV_BYTES || nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("AES 密钥、iv、nonce 的长度应分别为 32、12、16 字节");
        }
        String nonceText = b64(nonce);
        try {
            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
            rsa.init(Cipher.ENCRYPT_MODE, key.publicKey(), OAEP_SHA256);
            byte[] wrappedKey = rsa.doFinal(aesKey);
            byte[] data = gcm(Cipher.ENCRYPT_MODE, aesKey, iv, requestAad(key.kid(), ts, nonceText), plaintext);

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("v", VERSION);
            envelope.put("kid", key.kid());
            envelope.put("key", b64(wrappedKey));
            envelope.put("iv", b64(iv));
            envelope.put("ts", ts);
            envelope.put("nonce", nonceText);
            envelope.put("data", b64(data));
            return new Sealed(JSON.writeValueAsString(envelope), aesKey.clone(), nonceText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("加密请求失败", e);
        }
    }

    /**
     * 解密响应信封中的 iv 与 data（AES-GCM 认证失败时抛出异常）
     */
    public static byte[] open(String iv, String data, byte[] aesKey, String nonce) throws GeneralSecurityException {
        byte[] ivBytes = Base64.getDecoder().decode(iv);
        if (ivBytes.length != IV_BYTES) {
            throw new GeneralSecurityException("iv 长度应为 12 字节，实际为 " + ivBytes.length);
        }
        return gcm(Cipher.DECRYPT_MODE, aesKey, ivBytes, responseAad(nonce), Base64.getDecoder().decode(data));
    }

    static byte[] requestAad(String kid, long ts, String nonce) {
        return ("tuoyu-api-crypto/v1/request|" + kid + "|" + ts + "|" + nonce).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] responseAad(String nonce) {
        return ("tuoyu-api-crypto/v1/response|" + nonce).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] gcm(int mode, byte[] key, byte[] iv, byte[] aad, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
