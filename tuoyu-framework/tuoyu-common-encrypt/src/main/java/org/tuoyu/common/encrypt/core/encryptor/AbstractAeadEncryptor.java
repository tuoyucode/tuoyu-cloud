package org.tuoyu.common.encrypt.core.encryptor;

import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.encrypt.core.EncryptContext;
import org.tuoyu.common.encrypt.enums.EncodeType;

/**
 * 对称算法字段加密的公共逻辑（AES、SM4）：
 * <ul>
 *     <li>写入一律使用 GCM 认证加密（{@link AeadFieldCipher}），密钥取 {@code mybatis-encryptor.key}；</li>
 *     <li>读取时按密文格式区分：新格式用密钥解密；旧格式（ECB）用旧版口令解密，未配置旧口令时报错；</li>
 *     <li>任何失败都抛出异常，不返回原文或半成品。</li>
 * </ul>
 *
 * @author tuoyu
 */
abstract class AbstractAeadEncryptor extends AbstractEncryptor {

    /**
     * 新格式加解密器，未配置密钥时为 null（此时只能解密旧数据，写入会报错）
     */
    private final AeadFieldCipher cipher;

    private final String legacyPassword;

    AbstractAeadEncryptor(EncryptContext context, AeadFieldCipher.Algorithm algorithm) {
        super(context);
        this.cipher = StringUtils.isBlank(context.getKey()) ? null : AeadFieldCipher.of(algorithm, context.getKey());
        this.legacyPassword = context.getPassword();
        if (cipher == null && StringUtils.isBlank(legacyPassword)) {
            throw new IllegalArgumentException(algorithm + " 字段加密需要配置 mybatis-encryptor.key");
        }
    }

    @Override
    public String encrypt(String value, EncodeType encodeType) {
        if (cipher == null) {
            throw new IllegalStateException("未配置 mybatis-encryptor.key，不能写入加密字段（旧版口令只用于解密旧数据）");
        }
        return cipher.encrypt(value, encodeType);
    }

    @Override
    public String decrypt(String value) {
        if (AeadFieldCipher.isCurrentFormat(value)) {
            if (cipher == null) {
                throw new IllegalStateException("未配置 mybatis-encryptor.key，无法解密");
            }
            return cipher.decrypt(value);
        }
        if (StringUtils.isBlank(legacyPassword)) {
            throw new IllegalStateException("旧格式（ECB）密文需要配置旧版口令 mybatis-encryptor.password 才能解密");
        }
        return decryptLegacy(value, legacyPassword);
    }

    /**
     * 解密旧格式（ECB 模式、原始字符串口令）的密文
     */
    protected abstract String decryptLegacy(String value, String password);

}
