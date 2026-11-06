package org.tuoyu.common.encrypt.core.encryptor;

import org.tuoyu.common.encrypt.core.EncryptContext;
import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.utils.EncryptUtils;

/**
 * SM4 字段加密：写入使用 SM4-GCM，兼容解密旧版 SM4-ECB 密文
 *
 * @author tuoyu
 */
public class Sm4Encryptor extends AbstractAeadEncryptor {

    public Sm4Encryptor(EncryptContext context) {
        super(context, AeadFieldCipher.Algorithm.SM4);
    }

    /**
     * 获得当前算法
     */
    @Override
    public AlgorithmType algorithm() {
        return AlgorithmType.SM4;
    }

    @Override
    protected String decryptLegacy(String value, String password) {
        return EncryptUtils.decryptBySm4(value, password);
    }
}
