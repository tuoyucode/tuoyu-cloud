package org.tuoyu.common.encrypt.core.encryptor;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.GCMModeCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.tuoyu.common.encrypt.enums.EncodeType;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 字段加密的认证加密实现：AES-GCM 与 SM4-GCM（见 docs/adr/0003-上传-XSS-脱敏与字段加密.md）。
 * <p>
 * 密文格式（不含统一的 {@code ENC_} 前缀）：
 * <pre>
 *   v2:  + Base64( IV[12] || 密文 || 认证标签[16] )
 *   v2x: + Hex   ( IV[12] || 密文 || 认证标签[16] )
 * </pre>
 * <ul>
 *     <li>每次加密使用新的随机 IV，同一明文的密文不同，不会暴露“哪些行的值相同”；</li>
 *     <li>认证标签保证密文不能被篡改或伪造，解密失败一律抛出异常；</li>
 *     <li>附加数据（AAD）绑定格式版本与算法，AES 密文不能被当作 SM4 密文解密。</li>
 * </ul>
 * 线程安全。
 *
 * @author tuoyu
 */
public final class AeadFieldCipher {

    /**
     * Base64 编码的密文标记
     */
    public static final String MARKER_BASE64 = "v2:";

    /**
     * Hex 编码的密文标记
     */
    public static final String MARKER_HEX = "v2x:";

    private static final int IV_LENGTH = 12;

    private static final int TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 支持的分组算法
     */
    public enum Algorithm {
        /** AES-GCM，密钥 16、24 或 32 字节（推荐 32 字节） */
        AES,
        /** SM4-GCM（GB/T 32907 分组算法 + GCM 模式），密钥 16 字节 */
        SM4
    }

    private final Algorithm algorithm;

    private final byte[] key;

    private final byte[] aad;

    private AeadFieldCipher(Algorithm algorithm, byte[] key) {
        this.algorithm = algorithm;
        this.key = key;
        this.aad = ("tuoyu-field:v2:" + algorithm.name()).getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * 创建加解密器。
     *
     * @param algorithm 算法
     * @param base64Key Base64 编码的密钥
     * @return 加解密器
     * @throws IllegalArgumentException 密钥为空、不是合法的 Base64，或长度不符合算法要求（异常信息不包含密钥）
     */
    public static AeadFieldCipher of(Algorithm algorithm, String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException(algorithm + " 字段加密密钥不能为空");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(algorithm + " 字段加密密钥必须是 Base64 编码");
        }
        boolean valid = switch (algorithm) {
            case AES -> key.length == 16 || key.length == 24 || key.length == 32;
            case SM4 -> key.length == 16;
        };
        if (!valid) {
            String expected = algorithm == Algorithm.AES ? "16、24 或 32 字节（推荐 32 字节）" : "16 字节";
            throw new IllegalArgumentException(algorithm + " 字段加密密钥长度应为 " + expected + "，实际为 " + key.length
                + " 字节。可用 java tools/secrets/GenerateEnv.java --field-key 生成");
        }
        return new AeadFieldCipher(algorithm, key);
    }

    /**
     * 判断密文是否为本格式（不含 {@code ENC_} 前缀）
     */
    public static boolean isCurrentFormat(String value) {
        return value != null && (value.startsWith(MARKER_BASE64) || value.startsWith(MARKER_HEX));
    }

    /**
     * 加密。
     *
     * @param plaintext 明文
     * @param encode    编码方式，HEX 以外一律使用 Base64
     * @return 带格式标记的密文
     */
    public String encrypt(String plaintext, EncodeType encode) {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        byte[] sealed = crypt(true, iv, plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[IV_LENGTH + sealed.length];
        System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
        System.arraycopy(sealed, 0, payload, IV_LENGTH, sealed.length);
        if (encode == EncodeType.HEX) {
            return MARKER_HEX + HexFormat.of().formatHex(payload);
        }
        return MARKER_BASE64 + Base64.getEncoder().encodeToString(payload);
    }

    /**
     * 解密。
     *
     * @param ciphertext 带格式标记的密文
     * @return 明文
     * @throws IllegalStateException 格式不对、密文被篡改或密钥不对
     */
    public String decrypt(String ciphertext) {
        byte[] payload;
        try {
            if (ciphertext.startsWith(MARKER_HEX)) {
                payload = HexFormat.of().parseHex(ciphertext.substring(MARKER_HEX.length()));
            } else if (ciphertext.startsWith(MARKER_BASE64)) {
                payload = Base64.getDecoder().decode(ciphertext.substring(MARKER_BASE64.length()));
            } else {
                throw new IllegalStateException("不是 " + algorithm + "-GCM 字段密文");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(algorithm + "-GCM 字段密文编码不正确");
        }
        if (payload.length < IV_LENGTH + TAG_BITS / 8) {
            throw new IllegalStateException(algorithm + "-GCM 字段密文长度不正确");
        }
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] sealed = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
        return new String(crypt(false, iv, sealed), StandardCharsets.UTF_8);
    }

    public Algorithm algorithm() {
        return algorithm;
    }

    private byte[] crypt(boolean encrypt, byte[] iv, byte[] input) {
        return switch (algorithm) {
            case AES -> aes(encrypt, iv, input);
            case SM4 -> sm4(encrypt, iv, input);
        };
    }

    private byte[] aes(boolean encrypt, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(encrypt ? "AES-GCM 加密失败" : "AES-GCM 解密失败（密文被篡改或密钥不对）", e);
        }
    }

    private byte[] sm4(boolean encrypt, byte[] iv, byte[] input) {
        GCMModeCipher cipher = GCMBlockCipher.newInstance(new SM4Engine());
        cipher.init(encrypt, new AEADParameters(new KeyParameter(key), TAG_BITS, iv, aad));
        byte[] output = new byte[cipher.getOutputSize(input.length)];
        int length = cipher.processBytes(input, 0, input.length, output, 0);
        try {
            length += cipher.doFinal(output, length);
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM4-GCM 解密失败（密文被篡改或密钥不对）", e);
        }
        return length == output.length ? output : Arrays.copyOf(output, length);
    }

}
