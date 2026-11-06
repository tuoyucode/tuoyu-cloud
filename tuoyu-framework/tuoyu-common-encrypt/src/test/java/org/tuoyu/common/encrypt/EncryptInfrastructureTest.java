package org.tuoyu.common.encrypt;

import org.tuoyu.common.core.constant.Constants;
import org.tuoyu.common.encrypt.annotation.EncryptField;
import org.tuoyu.common.encrypt.core.EncryptContext;
import org.tuoyu.common.encrypt.core.EncryptContextFactory;
import org.tuoyu.common.encrypt.core.EncryptedFieldProcessor;
import org.tuoyu.common.encrypt.core.EncryptorManager;
import org.tuoyu.common.encrypt.core.IEncryptor;
import org.tuoyu.common.encrypt.enums.AlgorithmType;
import org.tuoyu.common.encrypt.enums.EncodeType;
import org.tuoyu.common.encrypt.properties.EncryptorProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("common-encrypt 基础设施单元测试")
class EncryptInfrastructureTest {

    /**
     * 验证集合和 Map 中的加密字段会被处理，且字段快照能够恢复持久化前的原始值。
     */
    @Test
    @DisplayName("加密并恢复容器中的字段")
    void shouldEncryptAndRestoreFieldsInsideContainers() {
        EncryptorProperties properties = new EncryptorProperties();
        properties.setAlgorithm(AlgorithmType.BASE64);
        properties.setEncode(EncodeType.BASE64);
        EncryptorManager manager = new EncryptorManager("");
        EncryptedFieldProcessor processor = new EncryptedFieldProcessor(
            manager, new EncryptContextFactory(properties));
        TestSecretEntity first = new TestSecretEntity("first");
        TestSecretEntity second = new TestSecretEntity("second");
        List<Object> source = new ArrayList<>();
        source.add(first);
        source.add(Map.of("entity", second));
        source.add(source);

        List<EncryptedFieldProcessor.FieldSnapshot> snapshots = processor.encrypt(source);

        assertEquals(2, snapshots.size());
        assertTrue(first.secret.startsWith(Constants.ENCRYPT_HEADER));
        assertTrue(second.secret.startsWith(Constants.ENCRYPT_HEADER));
        snapshots.forEach(EncryptedFieldProcessor.FieldSnapshot::restore);
        assertEquals("first", first.secret);
        assertEquals("second", second.secret);

        processor.encrypt(source);
        processor.decrypt(source);
        assertEquals("first", first.secret);
        assertEquals("second", second.secret);
    }

    /**
     * 验证相同上下文复用加密器缓存，显式移除后会重新创建实例。
     */
    @Test
    @DisplayName("复用和移除加密器缓存")
    void shouldReuseAndRemoveEncryptorCache() {
        EncryptorManager manager = new EncryptorManager("");
        EncryptContext context = new EncryptContext();
        context.setAlgorithm(AlgorithmType.BASE64);
        context.setEncode(EncodeType.BASE64);

        IEncryptor first = manager.registAndGetEncryptor(context);
        IEncryptor cached = manager.registAndGetEncryptor(context);
        manager.removeEncryptor(context);
        IEncryptor recreated = manager.registAndGetEncryptor(context);

        assertSame(first, cached);
        assertNotSame(first, recreated);
        assertTrue(manager.getFieldCache(String.class).isEmpty());
        assertTrue(manager.getFieldCache(null).isEmpty());
    }

}

class TestSecretEntity {

    @EncryptField(algorithm = AlgorithmType.BASE64)
    String secret;

    /**
     * 创建带待加密字段的顶层测试实体，以符合生产扫描器对实体类的约束。
     *
     * @param secret 原始明文
     */
    TestSecretEntity(String secret) {
        this.secret = secret;
    }
}
