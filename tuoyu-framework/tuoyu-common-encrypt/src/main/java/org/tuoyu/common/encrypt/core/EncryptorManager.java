package org.tuoyu.common.encrypt.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReflectUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.io.Resources;
import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.encrypt.annotation.EncryptField;
import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.enums.EncodeType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 加密管理类
 *
 * @author 老马
 * @version 4.6.0
 */
@Slf4j
public class EncryptorManager {

    /**
     * 缓存加密器
     */
    Map<EncryptorCacheKey, IEncryptor> encryptorMap = new ConcurrentHashMap<>();

    /**
     * 类加密字段缓存
     */
    Map<Class<?>, Set<Field>> fieldCache = new ConcurrentHashMap<>();

    /**
     * 构造方法传入类加密字段缓存
     *
     * @param typeAliasesPackage 实体类包
     */
    public EncryptorManager(String typeAliasesPackage) {
        scanEncryptClasses(typeAliasesPackage);
    }


    /**
     * 获取类加密字段缓存
     */
    public Set<Field> getFieldCache(Class<?> sourceClazz) {
        if (sourceClazz == null || sourceClazz.isPrimitive() || sourceClazz.isArray()
            || sourceClazz.isEnum() || sourceClazz.isAnnotation()
            || ClassUtils.isPrimitiveOrWrapper(sourceClazz)
            || sourceClazz.getName().startsWith("java.")) {
            return Set.of();
        }
        return fieldCache.computeIfAbsent(sourceClazz, this::getEncryptFieldSetFromClazz);
    }

    /**
     * 注册加密执行者到缓存
     *
     * @param encryptContext 加密执行者需要的相关配置参数
     */
    public IEncryptor registAndGetEncryptor(EncryptContext encryptContext) {
        EncryptorCacheKey key = EncryptorCacheKey.of(encryptContext);
        return encryptorMap.computeIfAbsent(key, cacheKey ->
            ReflectUtil.newInstance(cacheKey.algorithm().getClazz(), encryptContext));
    }

    /**
     * 移除缓存中的加密执行者
     *
     * @param encryptContext 加密执行者需要的相关配置参数
     */
    public void removeEncryptor(EncryptContext encryptContext) {
        this.encryptorMap.remove(EncryptorCacheKey.of(encryptContext));
    }

    /**
     * 根据配置进行加密。会进行本地缓存对应的算法和对应的秘钥信息。
     * <p>
     * 只有能用当前配置解密的值才视为“已加密”而原样返回；用户输入恰好以 {@code ENC_} 开头时照常加密，
     * 不会因此以明文入库（AES、SM4 的密文带认证标签，无法伪造）。
     *
     * @param value          待加密的值
     * @param encryptContext 加密相关的配置信息
     */
    public String encrypt(String value, EncryptContext encryptContext) {
        IEncryptor encryptor = this.registAndGetEncryptor(encryptContext);
        if (StringUtils.startsWith(value, Constants.ENCRYPT_HEADER) && isDecryptable(encryptor, value)) {
            return value;
        }
        String encrypt = encryptor.encrypt(value, encryptContext.getEncode());
        return Constants.ENCRYPT_HEADER + encrypt;
    }

    private static boolean isDecryptable(IEncryptor encryptor, String value) {
        try {
            encryptor.decrypt(StringUtils.removeStart(value, Constants.ENCRYPT_HEADER));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 根据配置进行解密。不带 {@code ENC_} 前缀的值视为启用加密之前写入的明文，原样返回；
     * 带前缀但无法解密时抛出异常
     *
     * @param value          待解密的值
     * @param encryptContext 加密相关的配置信息
     */
    public String decrypt(String value, EncryptContext encryptContext) {
        if (!StringUtils.startsWith(value, Constants.ENCRYPT_HEADER)) {
            return value;
        }
        IEncryptor encryptor = this.registAndGetEncryptor(encryptContext);
        String str = StringUtils.removeStart(value, Constants.ENCRYPT_HEADER);
        return encryptor.decrypt(str);
    }

    /**
     * 通过 typeAliasesPackage 设置的扫描包 扫描缓存实体
     */
    private void scanEncryptClasses(String typeAliasesPackage) {
        if (StringUtils.isBlank(typeAliasesPackage)) {
            return;
        }
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        CachingMetadataReaderFactory factory = new CachingMetadataReaderFactory();
        String[] packagePatternArray = StringUtils.splitPreserveAllTokens(typeAliasesPackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS);
        String classpath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX;
        try {
            for (String packagePattern : packagePatternArray) {
                String path = ClassUtils.convertClassNameToResourcePath(packagePattern);
                Resource[] resources = resolver.getResources(classpath + path + "/*.class");
                for (Resource resource : resources) {
                    ClassMetadata classMetadata = factory.getMetadataReader(resource).getClassMetadata();
                    Class<?> clazz = Resources.classForName(classMetadata.getClassName());
                    Set<Field> encryptFieldSet = getEncryptFieldSetFromClazz(clazz);
                    if (CollUtil.isNotEmpty(encryptFieldSet)) {
                        fieldCache.put(clazz, encryptFieldSet);
                    }
                }
            }
        } catch (IllegalStateException e) {
            // 注解用法错误，拒绝启动
            throw e;
        } catch (Exception e) {
            log.error("初始化数据安全缓存时出错:{}", e.getMessage());
        }
    }

    /**
     * 获得一个类的加密字段集合
     */
    private Set<Field> getEncryptFieldSetFromClazz(Class<?> clazz) {
        Set<Field> fieldSet = new HashSet<>();
        // 接口与匿名类没有需要处理的实例字段。静态内部类照常处理，否则其中的加密字段会被静默跳过、以明文入库
        if (clazz.isInterface() || clazz.isAnonymousClass()) {
            return Set.of();
        }
        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();
            fieldSet.addAll(Arrays.asList(fields));
            clazz = clazz.getSuperclass();
        }
        fieldSet = fieldSet.stream().filter(field -> field.isAnnotationPresent(EncryptField.class))
            .collect(Collectors.toSet());
        for (Field field : fieldSet) {
            // 非字符串字段无法加密，静默跳过会让数据以明文入库，因此直接报错
            if (field.getType() != String.class) {
                throw new IllegalStateException("@EncryptField 只能用于 String 字段："
                    + field.getDeclaringClass().getName() + "." + field.getName());
            }
        }
        for (Field field : fieldSet) {
            field.setAccessible(true);
        }
        return fieldSet;
    }

    private record EncryptorCacheKey(
        AlgorithmType algorithm,
        EncodeType encode,
        String key,
        String password,
        String publicKey,
        String privateKey
    ) {

        private static EncryptorCacheKey of(EncryptContext encryptContext) {
            return new EncryptorCacheKey(
                encryptContext.getAlgorithm(),
                encryptContext.getEncode(),
                encryptContext.getKey(),
                encryptContext.getPassword(),
                encryptContext.getPublicKey(),
                encryptContext.getPrivateKey());
        }
    }

}
