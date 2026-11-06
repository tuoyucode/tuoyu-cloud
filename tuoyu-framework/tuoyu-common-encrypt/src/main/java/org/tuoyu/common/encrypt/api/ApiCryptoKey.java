package org.tuoyu.common.encrypt.api;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 一把接口加密密钥：RSA 私钥、由它推导的公钥，以及用于区分密钥的 kid
 *
 * @param kid        SPKI 的 SHA-256 前 12 字节，Base64URL 无填充
 * @param privateKey RSA 私钥
 * @param publicKey  RSA 公钥
 * @param spki       公钥的 X.509 SubjectPublicKeyInfo DER 编码
 * @author tuoyu
 */
public record ApiCryptoKey(String kid, RSAPrivateCrtKey privateKey, RSAPublicKey publicKey, byte[] spki) {

    /**
     * 低于此位数拒绝使用
     */
    public static final int MIN_KEY_SIZE = 2048;

    /**
     * 建议的位数，低于它时启动日志给出警告
     */
    public static final int RECOMMENDED_KEY_SIZE = 3072;

    private static final int KID_BYTES = 12;

    /**
     * 解析 PKCS#8 DER 的 Base64 私钥
     *
     * @param base64Pkcs8 私钥
     * @param name        配置项名，用于错误提示
     * @throws IllegalStateException 私钥无效或位数不足
     */
    public static ApiCryptoKey parse(String base64Pkcs8, String name) {
        RSAPrivateCrtKey privateKey;
        RSAPublicKey publicKey;
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            byte[] der = Base64.getMimeDecoder().decode(base64Pkcs8.trim());
            if (!(factory.generatePrivate(new PKCS8EncodedKeySpec(der)) instanceof RSAPrivateCrtKey crtKey)) {
                throw new IllegalStateException(name + " 不是完整的 RSA 私钥（缺少 CRT 参数，无法推导公钥）");
            }
            privateKey = crtKey;
            publicKey = (RSAPublicKey) factory.generatePublic(
                new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(name + " 不是有效的 RSA 私钥（应为 PKCS#8 DER 的 Base64）", e);
        }
        int bits = publicKey.getModulus().bitLength();
        if (bits < MIN_KEY_SIZE) {
            throw new IllegalStateException(name + " 只有 " + bits + " 位，至少需要 " + MIN_KEY_SIZE
                + " 位，建议 " + RECOMMENDED_KEY_SIZE + " 位。可用 java tools/secrets/GenerateEnv.java 重新生成");
        }
        byte[] spki = publicKey.getEncoded();
        return new ApiCryptoKey(kidOf(spki), privateKey, publicKey, spki);
    }

    /**
     * 密钥位数
     */
    public int keySize() {
        return publicKey.getModulus().bitLength();
    }

    /**
     * 公钥（SPKI DER 的标准 Base64），供前端导入
     */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(spki);
    }

    static String kidOf(byte[] spki) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(spki);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, KID_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "ApiCryptoKey[kid=" + kid + ", bits=" + keySize() + "]";
    }

}
