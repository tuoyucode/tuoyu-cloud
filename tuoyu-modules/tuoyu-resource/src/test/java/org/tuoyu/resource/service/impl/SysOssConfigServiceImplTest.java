package org.tuoyu.resource.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.resource.domain.SysOssConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("存储配置：修改时密钥留空表示不修改")
class SysOssConfigServiceImplTest {

    @Test
    @DisplayName("空串或空白的密钥置为 null，按 NOT_NULL 更新策略保留原值")
    void blankKeysAreNotUpdated() {
        SysOssConfig config = new SysOssConfig();
        config.setAccessKey("");
        config.setSecretKey("   ");

        SysOssConfigServiceImpl.keepUnchangedKeys(config);

        assertNull(config.getAccessKey());
        assertNull(config.getSecretKey());
    }

    @Test
    @DisplayName("填写了新密钥时照常修改")
    void filledKeysAreKept() {
        SysOssConfig config = new SysOssConfig();
        config.setAccessKey("new-access");
        config.setSecretKey("new-secret");

        SysOssConfigServiceImpl.keepUnchangedKeys(config);

        assertEquals("new-access", config.getAccessKey());
        assertEquals("new-secret", config.getSecretKey());
    }
}
