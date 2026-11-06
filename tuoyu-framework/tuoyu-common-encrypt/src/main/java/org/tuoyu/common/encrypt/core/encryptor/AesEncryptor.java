package org.tuoyu.common.encrypt.core.encryptor;

import org.tuoyu.common.encrypt.core.EncryptContext;
import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.utils.EncryptUtils;

/**
 * AES 字段加密：写入使用 AES-GCM，兼容解密旧版 AES-ECB 密文
 *
 * @author tuoyu
 */
public class AesEncryptor extends AbstractAeadEncryptor {

    public AesEncryptor(EncryptContext context) {
        super(context, AeadFieldCipher.Algorithm.AES);
    }

    /**
     * 获得当前算法
     */
    @Override
    public AlgorithmType algorithm() {
        return AlgorithmType.AES;
    }

    @Override
    protected String decryptLegacy(String value, String password) {
        return EncryptUtils.decryptByAes(value, password);
    }
}
