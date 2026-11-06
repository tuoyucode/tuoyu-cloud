package org.tuoyu.resource.domain.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("存储配置：接口不返回密钥明文")
class SysOssConfigVoTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("密钥只以“是否已设置”返回")
    void serializesOnlyKeyFlags() {
        SysOssConfigVo vo = new SysOssConfigVo();
        vo.setConfigKey("minio");
        vo.setAccessKey("access-value");
        vo.setSecretKey("secret-value");

        JsonNode json = mapper.valueToTree(vo);

        assertFalse(json.has("accessKey"));
        assertFalse(json.has("secretKey"));
        assertTrue(json.get("accessKeySet").asBoolean());
        assertTrue(json.get("secretKeySet").asBoolean());
        assertFalse(json.toString().contains("secret-value"));
        assertFalse(json.toString().contains("access-value"));
    }

    @Test
    @DisplayName("未设置或为空白时标记为 false")
    void blankKeysAreNotSet() {
        SysOssConfigVo vo = new SysOssConfigVo();
        vo.setSecretKey("  ");

        JsonNode json = mapper.valueToTree(vo);

        assertFalse(json.get("accessKeySet").asBoolean());
        assertFalse(json.get("secretKeySet").asBoolean());
    }
}
