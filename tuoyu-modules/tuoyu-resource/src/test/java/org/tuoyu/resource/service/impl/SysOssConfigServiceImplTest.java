package org.tuoyu.resource.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.oss.constant.OssConstant;
import org.tuoyu.resource.domain.SysOssConfig;
import org.tuoyu.resource.mapper.SysOssConfigMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("存储配置：修改时密钥留空表示不修改；删除失败时给出原因")
class SysOssConfigServiceImplTest {

    private final SysOssConfigMapper mapper = mock(SysOssConfigMapper.class);

    private final SysOssConfigServiceImpl service = new SysOssConfigServiceImpl(mapper);

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

    @Test
    @DisplayName("删除：包含系统内置配置时拒绝，不执行删除")
    void rejectDeletingBuiltIn() {
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.deleteWithValidByIds(List.of(OssConstant.SYSTEM_DATA_IDS.getFirst()), true));

        assertEquals("系统内置, 不可删除!", e.getMessage());
        verify(mapper, never()).deleteByIds(any(List.class));
    }

    @Test
    @DisplayName("删除：配置不存在时抛出带原因的业务异常（没有错误码，HTTP 400）")
    void deletingMissingConfigFails() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.deleteWithValidByIds(List.of(1L), true));

        assertEquals("对象存储配置不存在", e.getMessage());
        assertNull(e.getCode());
    }
}
