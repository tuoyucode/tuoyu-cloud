package org.tuoyu.resource.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.tuoyu.resource.config.UploadProperties;
import org.tuoyu.resource.domain.vo.SysOssUploadConfigVo;
import org.tuoyu.resource.service.ISysOssService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("上传限制：前端的可选类型与上传前提示以服务端的配置为准")
class SysOssControllerUploadConfigTest {

    @Test
    @DisplayName("返回当前生效的扩展名白名单（排序后）与单个文件的上限（两者中较小的）")
    void returnsCurrentWhitelistAndLimit() {
        UploadProperties properties = new UploadProperties();
        properties.setAllowedExtensions(List.of("PNG", "pdf", "jpg"));
        MockEnvironment environment = new MockEnvironment()
            .withProperty("spring.servlet.multipart.max-request-size", "100MB")
            .withProperty("spring.servlet.multipart.max-file-size", "50MB");
        SysOssController controller = new SysOssController(mock(ISysOssService.class), properties, environment);

        SysOssUploadConfigVo config = controller.uploadConfig().getData();

        assertThat(config.allowedExtensions()).containsExactly("jpg", "pdf", "png");
        assertThat(config.maxFileSize()).isEqualTo(50L * 1024 * 1024);
    }

    @Test
    @DisplayName("白名单随配置刷新：重新绑定后返回新的白名单")
    void followsRefreshedWhitelist() {
        UploadProperties properties = new UploadProperties();
        SysOssController controller = new SysOssController(mock(ISysOssService.class), properties, new MockEnvironment());
        assertThat(controller.uploadConfig().getData().allowedExtensions()).contains("pdf", "png");

        properties.setAllowedExtensions(List.of("txt"));

        assertThat(controller.uploadConfig().getData().allowedExtensions()).containsExactly("txt");
    }

}
