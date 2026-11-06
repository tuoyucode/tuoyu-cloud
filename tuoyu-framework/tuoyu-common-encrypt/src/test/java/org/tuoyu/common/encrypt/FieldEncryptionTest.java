package org.tuoyu.common.encrypt;

import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.encrypt.annotation.EncryptField;
import org.tuoyu.common.encrypt.core.EncryptContext;
import org.tuoyu.common.encrypt.core.EncryptContextFactory;
import org.tuoyu.common.encrypt.core.EncryptedFieldProcessor;
import org.tuoyu.common.encrypt.core.EncryptorManager;
import org.tuoyu.common.encrypt.core.FieldEncryptionException;
import org.tuoyu.common.encrypt.core.encryptor.AeadFieldCipher;
import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.enums.EncodeType;
import org.tuoyu.common.encrypt.properties.EncryptorProperties;
import org.tuoyu.common.encrypt.utils.EncryptUtils;

import java.lang.reflect.Method;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("字段加密 AES-GCM / SM4-GCM 与失败闭合")
class FieldEncryptionTest {

    /** 32 字节 AES 密钥（仅用于测试） */
    private static final String AES_KEY = Base64.getEncoder().encodeToString(bytes(32, 1));
    /** 16 字节 SM4 密钥（仅用于测试） */
    private static final String SM4_KEY = Base64.getEncoder().encodeToString(bytes(16, 7));

    private static final String TEXT = "13812348000 张三 a@b.c";

    @Nested
    @DisplayName("算法")
    class Algorithms {

        @Test
        @DisplayName("SM4 分组算法符合 GB/T 32907 标准测试向量")
        void sm4KnownAnswer() {
            HexFormat hex = HexFormat.of();
            byte[] key = hex.parseHex("0123456789abcdeffedcba9876543210");
            SM4Engine engine = new SM4Engine();
            engine.init(true, new KeyParameter(key));
            byte[] out = new byte[16];
            engine.processBlock(key, 0, out, 0);
            assertEquals("681edf34d206965e86b3e94f536e4246", hex.formatHex(out));
        }

        @Test
        @DisplayName("AES-GCM、SM4-GCM 加解密往返，Base64 与 Hex 两种编码")
        void roundTrip() {
            for (AeadFieldCipher cipher : List.of(AeadFieldCipher.of(AeadFieldCipher.Algorithm.AES, AES_KEY),
                AeadFieldCipher.of(AeadFieldCipher.Algorithm.SM4, SM4_KEY))) {
                String base64 = cipher.encrypt(TEXT, EncodeType.BASE64);
                String hexed = cipher.encrypt(TEXT, EncodeType.HEX);
                assertTrue(base64.startsWith(AeadFieldCipher.MARKER_BASE64), base64);
                assertTrue(hexed.startsWith(AeadFieldCipher.MARKER_HEX), hexed);
                assertEquals(TEXT, cipher.decrypt(base64));
                assertEquals(TEXT, cipher.decrypt(hexed));
                assertEquals("", cipher.decrypt(cipher.encrypt("", EncodeType.BASE64)));
            }
        }

        @Test
        @DisplayName("随机 IV：同一明文每次的密文不同")
        void randomIv() {
            AeadFieldCipher cipher = AeadFieldCipher.of(AeadFieldCipher.Algorithm.SM4, SM4_KEY);
            assertNotEquals(cipher.encrypt(TEXT, EncodeType.BASE64), cipher.encrypt(TEXT, EncodeType.BASE64));
        }

        @Test
        @DisplayName("密文被篡改、密钥不对、算法不对时解密失败")
        void tamperAndWrongKey() {
            AeadFieldCipher aes = AeadFieldCipher.of(AeadFieldCipher.Algorithm.AES, AES_KEY);
            String sealed = aes.encrypt(TEXT, EncodeType.HEX);
            char last = sealed.charAt(sealed.length() - 1);
            String tampered = sealed.substring(0, sealed.length() - 1) + (last == '0' ? '1' : '0');
            assertThrows(IllegalStateException.class, () -> aes.decrypt(tampered));

            String otherKey = Base64.getEncoder().encodeToString(bytes(32, 2));
            assertThrows(IllegalStateException.class, () -> AeadFieldCipher.of(AeadFieldCipher.Algorithm.AES, otherKey).decrypt(sealed));

            // 同一把 16 字节密钥：AES 密文不能当作 SM4 密文解密（附加数据绑定了算法）
            AeadFieldCipher aes128 = AeadFieldCipher.of(AeadFieldCipher.Algorithm.AES, SM4_KEY);
            AeadFieldCipher sm4 = AeadFieldCipher.of(AeadFieldCipher.Algorithm.SM4, SM4_KEY);
            assertThrows(IllegalStateException.class, () -> sm4.decrypt(aes128.encrypt(TEXT, EncodeType.BASE64)));

            assertThrows(IllegalStateException.class, () -> aes.decrypt("v2:%%%"));
            assertThrows(IllegalStateException.class, () -> aes.decrypt("v2:AAAA"));
            assertThrows(IllegalStateException.class, () -> aes.decrypt("not-a-ciphertext"));
        }

        @Test
        @DisplayName("密钥必须是 Base64，长度符合算法要求，报错信息不含密钥")
        void keyValidation() {
            assertThrows(IllegalArgumentException.class, () -> AeadFieldCipher.of(AeadFieldCipher.Algorithm.AES, " "));
            assertThrows(IllegalArgumentException.class, () -> AeadFieldCipher.of(AeadFieldCipher.Algorithm.AES, "not base64!"));
            String twentyBytes = Base64.getEncoder().encodeToString(bytes(20, 3));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AeadFieldCipher.of(AeadFieldCipher.Algorithm.AES, twentyBytes));
            assertFalse(e.getMessage().contains(twentyBytes));
            assertThrows(IllegalArgumentException.class, () -> AeadFieldCipher.of(AeadFieldCipher.Algorithm.SM4, AES_KEY));
            // 旧版的 16 位字符串口令不能直接当作新密钥
            assertThrows(IllegalArgumentException.class, () -> AeadFieldCipher.of(AeadFieldCipher.Algorithm.SM4, "abcdef1234567890"));
        }
    }

    @Nested
    @DisplayName("加密管理器")
    class Manager {

        private final EncryptorManager manager = new EncryptorManager("");

        @Test
        @DisplayName("AES、SM4 写入新格式，可解密")
        void writeNewFormat() {
            for (EncryptContext context : List.of(context(AlgorithmType.AES, AES_KEY, null), context(AlgorithmType.SM4, SM4_KEY, null))) {
                String stored = manager.encrypt(TEXT, context);
                assertTrue(stored.startsWith(Constants.ENCRYPT_HEADER + AeadFieldCipher.MARKER_BASE64), stored);
                assertEquals(TEXT, manager.decrypt(stored, context));
                // 已是本配置的密文时不重复加密
                assertEquals(stored, manager.encrypt(stored, context));
            }
        }

        @Test
        @DisplayName("用户输入以 ENC_ 开头时照常加密，不会以明文入库")
        void spoofedHeaderIsEncrypted() {
            EncryptContext context = context(AlgorithmType.AES, AES_KEY, null);
            String spoofed = Constants.ENCRYPT_HEADER + "13800000000";

            String stored = manager.encrypt(spoofed, context);

            assertNotEquals(spoofed, stored);
            assertEquals(spoofed, manager.decrypt(stored, context));
        }

        @Test
        @DisplayName("兼容解密旧版 ECB 密文，保存时改为新格式")
        void legacyCiphertext() {
            String legacyPassword = "1234567890abcdef";
            String legacy = Constants.ENCRYPT_HEADER + EncryptUtils.encryptByAes(TEXT, legacyPassword);
            String legacySm4 = Constants.ENCRYPT_HEADER + EncryptUtils.encryptBySm4Hex(TEXT, legacyPassword);
            EncryptContext aes = context(AlgorithmType.AES, AES_KEY, legacyPassword);
            EncryptContext sm4 = context(AlgorithmType.SM4, SM4_KEY, legacyPassword);

            assertEquals(TEXT, manager.decrypt(legacy, aes));
            assertEquals(TEXT, manager.decrypt(legacySm4, sm4));
            // 读出的明文再保存，写入新格式
            assertTrue(manager.encrypt(manager.decrypt(legacy, aes), aes).startsWith(Constants.ENCRYPT_HEADER + "v2"));

            EncryptContext withoutLegacy = context(AlgorithmType.AES, AES_KEY, null);
            assertThrows(IllegalStateException.class, () -> manager.decrypt(legacy, withoutLegacy));
        }

        @Test
        @DisplayName("只配置旧版口令时只能解密，不能写入")
        void legacyOnlyCannotWrite() {
            String legacyPassword = "1234567890abcdef";
            EncryptContext context = context(AlgorithmType.AES, null, legacyPassword);
            String legacy = Constants.ENCRYPT_HEADER + EncryptUtils.encryptByAes(TEXT, legacyPassword);

            assertEquals(TEXT, manager.decrypt(legacy, context));
            assertThrows(IllegalStateException.class, () -> manager.encrypt(TEXT, context));
        }

        @Test
        @DisplayName("没有密钥也没有旧口令时拒绝创建加密器")
        void noKeyAtAll() {
            assertThrows(RuntimeException.class, () -> manager.encrypt(TEXT, context(AlgorithmType.SM4, null, null)));
        }

        @Test
        @DisplayName("未加密的历史明文原样读出")
        void plaintextBeforeEncryptionEnabled() {
            assertEquals("13800000000", manager.decrypt("13800000000", context(AlgorithmType.AES, AES_KEY, null)));
        }
    }

    @Nested
    @DisplayName("字段处理器失败闭合")
    class FailClosed {

        @Test
        @DisplayName("任一字段加密失败：抛出异常、已加密字段恢复原值、信息不含字段值")
        void encryptFailureRestoresAndThrows() {
            // 全局密钥是 32 字节，SM4 字段无法创建加密器
            EncryptedFieldProcessor processor = processor(AlgorithmType.AES, AES_KEY);
            MixedEntity entity = new MixedEntity();
            entity.phone = "13812348000";
            entity.idCard = "110101199001011234";

            FieldEncryptionException e = assertThrows(FieldEncryptionException.class, () -> processor.encrypt(List.of(entity)));

            assertEquals("13812348000", entity.phone);
            assertEquals("110101199001011234", entity.idCard);
            assertTrue(e.getMessage().contains("MixedEntity.idCard"), e.getMessage());
            assertFalse(e.getMessage().contains("110101199001011234"));
        }

        @Test
        @DisplayName("解密失败时抛出异常，不把密文交给业务")
        void decryptFailureThrows() {
            EncryptedFieldProcessor processor = processor(AlgorithmType.AES, AES_KEY);
            PhoneEntity entity = new PhoneEntity();
            entity.phone = Constants.ENCRYPT_HEADER + "v2:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";

            assertThrows(FieldEncryptionException.class, () -> processor.decrypt(entity));
        }

        @Test
        @DisplayName("静态内部类中的加密字段同样处理")
        void nestedClassIsProcessed() {
            EncryptedFieldProcessor processor = processor(AlgorithmType.SM4, SM4_KEY);
            PhoneEntity entity = new PhoneEntity();
            entity.phone = "13812348000";

            List<EncryptedFieldProcessor.FieldSnapshot> snapshots = processor.encrypt(entity);

            assertEquals(1, snapshots.size());
            assertTrue(entity.phone.startsWith(Constants.ENCRYPT_HEADER), entity.phone);
            processor.decrypt(entity);
            assertEquals("13812348000", entity.phone);
        }

        @Test
        @DisplayName("@EncryptField 用在非字符串字段上时报错，而不是静默跳过")
        void nonStringFieldRejected() {
            EncryptedFieldProcessor processor = processor(AlgorithmType.AES, AES_KEY);
            WrongTypeEntity entity = new WrongTypeEntity();
            entity.amount = 100L;

            FieldEncryptionException e = assertThrows(FieldEncryptionException.class, () -> processor.encrypt(entity));
            assertTrue(e.getMessage().contains("只能用于 String"), e.getMessage());
        }
    }

    @Nested
    @DisplayName("启动校验")
    class StartupValidation {

        @Test
        @DisplayName("AES、SM4 必须配置合法的密钥；默认算法为 AES")
        void validateProperties() throws Exception {
            Method validate = Class.forName("org.tuoyu.common.encrypt.config.EncryptorAutoConfiguration")
                .getDeclaredMethod("validateEncryptorProperties", EncryptorProperties.class);
            validate.setAccessible(true);
            EncryptorProperties properties = new EncryptorProperties();
            assertEquals(AlgorithmType.AES, properties.getAlgorithm());

            assertThrows(IllegalArgumentException.class, () -> invoke(validate, properties));
            properties.setKey("1234567890abcdef");
            assertThrows(IllegalArgumentException.class, () -> invoke(validate, properties));
            properties.setKey(AES_KEY);
            assertDoesNotThrow(() -> invoke(validate, properties));
            properties.setAlgorithm(AlgorithmType.SM4);
            assertThrows(IllegalArgumentException.class, () -> invoke(validate, properties));
            properties.setKey(SM4_KEY);
            assertDoesNotThrow(() -> invoke(validate, properties));
        }

        private void invoke(Method method, EncryptorProperties properties) throws Throwable {
            try {
                method.invoke(null, properties);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static EncryptedFieldProcessor processor(AlgorithmType algorithm, String key) {
        EncryptorProperties properties = new EncryptorProperties();
        properties.setAlgorithm(algorithm);
        properties.setEncode(EncodeType.BASE64);
        properties.setKey(key);
        return new EncryptedFieldProcessor(new EncryptorManager(""), new EncryptContextFactory(properties));
    }

    private static EncryptContext context(AlgorithmType algorithm, String key, String legacyPassword) {
        EncryptContext context = new EncryptContext();
        context.setAlgorithm(algorithm);
        context.setEncode(EncodeType.BASE64);
        context.setKey(key);
        context.setPassword(legacyPassword);
        return context;
    }

    private static byte[] bytes(int length, int seed) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) (seed * 31 + i);
        }
        return result;
    }

    static class PhoneEntity {

        @EncryptField
        String phone;
    }

    static class MixedEntity {

        @EncryptField
        String phone;

        @EncryptField(algorithm = AlgorithmType.SM4)
        String idCard;
    }

    static class WrongTypeEntity {

        @EncryptField
        Long amount;
    }

}
