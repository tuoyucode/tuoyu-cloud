package org.tuoyu.common.encrypt.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 加密字段处理器。
 * <p>
 * 失败闭合：任何字段加密失败时，恢复已改动的字段并抛出
 * {@link FieldEncryptionException}，SQL 不会执行，明文不会入库；解密失败同样抛出异常，不把密文或半成品返回给业务。
 *
 * @author Lion Li
 */
@Slf4j
public class EncryptedFieldProcessor {

    private final EncryptorManager encryptorManager;
    private final EncryptContextFactory contextFactory;

    /**
     * 构造加密字段处理器。
     *
     * @param encryptorManager 加解密管理器
     * @param contextFactory   加密上下文工厂
     */
    public EncryptedFieldProcessor(EncryptorManager encryptorManager, EncryptContextFactory contextFactory) {
        this.encryptorManager = encryptorManager;
        this.contextFactory = contextFactory;
    }

    /**
     * 加密对象字段，并返回原始字段快照。
     *
     * @param sourceObject 待加密对象
     * @return 原始字段快照
     */
    public List<FieldSnapshot> encrypt(Object sourceObject) {
        List<FieldSnapshot> snapshots = new ArrayList<>();
        try {
            handle(sourceObject, Collections.newSetFromMap(new IdentityHashMap<>()), "加密", (target, field, value) -> {
                String encrypt = encryptorManager.encrypt(value, contextFactory.create(field));
                if (!Objects.equals(value, encrypt)) {
                    snapshots.add(new FieldSnapshot(target, field, value));
                    field.set(target, encrypt);
                }
            });
        } catch (RuntimeException e) {
            // 已加密的字段恢复为原值，调用方的对象不留下半成品
            snapshots.forEach(FieldSnapshot::restore);
            throw e;
        }
        return snapshots;
    }

    /**
     * 解密对象字段。
     *
     * @param sourceObject 待解密对象
     */
    public void decrypt(Object sourceObject) {
        handle(sourceObject, Collections.newSetFromMap(new IdentityHashMap<>()), "解密", (target, field, value) ->
            field.set(target, encryptorManager.decrypt(value, contextFactory.create(field))));
    }

    private void handle(Object sourceObject, Set<Object> visited, String action, FieldHandler fieldHandler) {
        if (ObjectUtil.isNull(sourceObject) || sourceObject instanceof String || visited.contains(sourceObject)) {
            return;
        }
        visited.add(sourceObject);
        if (sourceObject instanceof Map<?, ?> map) {
            new HashSet<>(map.values()).forEach(value -> handle(value, visited, action, fieldHandler));
            return;
        }
        if (sourceObject instanceof Collection<?> collection) {
            if (CollUtil.isEmpty(collection)) {
                return;
            }
            collection.forEach(item -> handle(item, visited, action, fieldHandler));
            return;
        }
        Set<Field> fields;
        try {
            fields = encryptorManager.getFieldCache(sourceObject.getClass());
        } catch (RuntimeException e) {
            throw new FieldEncryptionException("字段" + action + "失败：" + sourceObject.getClass().getName() + "，" + e.getMessage(), e);
        }
        if (CollUtil.isEmpty(fields)) {
            return;
        }
        for (Field field : fields) {
            try {
                String value = Convert.toStr(field.get(sourceObject));
                if (ObjectUtil.isNotNull(value)) {
                    fieldHandler.handle(sourceObject, field, value);
                }
            } catch (IllegalAccessException | RuntimeException e) {
                // 只记录类名与字段名，不记录字段值
                throw new FieldEncryptionException("字段" + action + "失败：" + field.getDeclaringClass().getName() + "." + field.getName(), e);
            }
        }
    }

    @FunctionalInterface
    private interface FieldHandler {

        void handle(Object target, Field field, String value) throws IllegalAccessException;
    }

    /**
     * 字段原始值快照。
     */
    public record FieldSnapshot(Object target, Field field, Object value) {

        /**
         * 恢复原始字段值。
         */
        public void restore() {
            try {
                field.set(target, value);
            } catch (IllegalAccessException e) {
                log.error("恢复加密字段时出错", e);
            }
        }
    }
}
