package org.tuoyu.common.encrypt.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.encrypt.core.EncryptContextFactory;
import org.tuoyu.common.encrypt.core.EncryptedFieldProcessor;
import org.tuoyu.common.encrypt.core.EncryptorManager;
import org.tuoyu.common.encrypt.core.encryptor.AeadFieldCipher;
import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.interceptor.MybatisDecryptInterceptor;
import org.tuoyu.common.encrypt.interceptor.MybatisEncryptInterceptor;
import org.tuoyu.common.encrypt.properties.EncryptorProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 加解密配置
 *
 * @author 老马
 * @version 4.6.0
 */
@Slf4j
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class)
@EnableConfigurationProperties({EncryptorProperties.class, MybatisPlusProperties.class})
@ConditionalOnClass(MybatisPlusAutoConfiguration.class)
@ConditionalOnProperty(value = "mybatis-encryptor.enable", havingValue = "true")
public class EncryptorAutoConfiguration {

    @Autowired
    private EncryptorProperties properties;

    /**
     * 创建字段加解密管理器。
     *
     * @param mybatisPlusProperties MyBatis-Plus 配置
     * @return 字段加解密管理器
     */
    @Bean
    public EncryptorManager encryptorManager(MybatisPlusProperties mybatisPlusProperties) {
        validateEncryptorProperties(properties);
        return new EncryptorManager(mybatisPlusProperties.getTypeAliasesPackage());
    }

    /**
     * 创建加密上下文工厂。
     *
     * @return 加密上下文工厂
     */
    @Bean
    public EncryptContextFactory encryptContextFactory() {
        return new EncryptContextFactory(properties);
    }

    /**
     * 创建加密字段处理器。
     *
     * @param encryptorManager      加解密管理器
     * @param encryptContextFactory 加密上下文工厂
     * @return 加密字段处理器
     */
    @Bean
    public EncryptedFieldProcessor encryptedFieldProcessor(EncryptorManager encryptorManager, EncryptContextFactory encryptContextFactory) {
        return new EncryptedFieldProcessor(encryptorManager, encryptContextFactory);
    }

    /**
     * 创建 MyBatis 入参加密拦截器。
     *
     * @param encryptedFieldProcessor 加密字段处理器
     * @return MyBatis 入参加密拦截器
     */
    @Bean
    public MybatisEncryptInterceptor mybatisEncryptInterceptor(EncryptedFieldProcessor encryptedFieldProcessor) {
        return new MybatisEncryptInterceptor(encryptedFieldProcessor);
    }

    /**
     * 创建 MyBatis 出参解密拦截器。
     *
     * @param encryptedFieldProcessor 加密字段处理器
     * @return MyBatis 出参解密拦截器
     */
    @Bean
    public MybatisDecryptInterceptor mybatisDecryptInterceptor(EncryptedFieldProcessor encryptedFieldProcessor) {
        return new MybatisDecryptInterceptor(encryptedFieldProcessor);
    }

    /**
     * 启动时校验配置，不合理时拒绝启动（异常信息不包含密钥）
     *
     * @param properties 加解密配置
     */
    static void validateEncryptorProperties(EncryptorProperties properties) {
        AlgorithmType algorithm = properties.getAlgorithm();
        if (algorithm == null || algorithm == AlgorithmType.DEFAULT) {
            throw new IllegalArgumentException("mybatis-encryptor.algorithm 不能为空，可选 AES、SM4");
        }
        if (algorithm == AlgorithmType.AES || algorithm == AlgorithmType.SM4) {
            if (StringUtils.isBlank(properties.getKey())) {
                throw new IllegalArgumentException("mybatis-encryptor.key 不能为空（环境变量 TUOYU_FIELD_ENCRYPT_KEY），"
                    + "可用 java tools/secrets/GenerateEnv.java --field-key 生成");
            }
            // 校验密钥格式与长度
            AeadFieldCipher.of(algorithm == AlgorithmType.AES ? AeadFieldCipher.Algorithm.AES : AeadFieldCipher.Algorithm.SM4,
                properties.getKey());
        }
        if (algorithm == AlgorithmType.RSA || algorithm == AlgorithmType.SM2) {
            if (StringUtils.isAnyBlank(properties.getPublicKey(), properties.getPrivateKey())) {
                throw new IllegalArgumentException("mybatis-encryptor.publicKey 与 privateKey 不能为空");
            }
        }
        if (algorithm == AlgorithmType.BASE64) {
            log.warn("mybatis-encryptor.algorithm 为 BASE64：这只是编码，不是加密，任何人都能还原。请改为 AES 或 SM4");
        }
        if (StringUtils.isNotBlank(properties.getPassword())) {
            log.warn("已配置旧版口令 mybatis-encryptor.password：只用于解密旧格式（ECB）数据，这些数据在下次保存时改为新格式。"
                + "全部迁移后请删除旧口令");
        }
    }
}
