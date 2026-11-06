package org.tuoyu.resource.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.oss.client.OssClient;
import org.tuoyu.common.oss.exception.S3StorageException;
import org.tuoyu.common.oss.factory.OssFactory;
import org.tuoyu.common.oss.model.Options;
import org.tuoyu.common.oss.model.PutObjectResult;
import org.tuoyu.resource.config.UploadProperties;
import org.tuoyu.resource.domain.SysOss;
import org.tuoyu.resource.domain.SysOssExt;
import org.tuoyu.resource.domain.vo.SysOssVo;
import org.tuoyu.resource.mapper.SysOssMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("OSS 上传的类型校验")
class SysOssServiceImplUploadTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    private final SysOssMapper mapper = mock(SysOssMapper.class);
    private final OssClient client = mock(OssClient.class);
    private MockedStatic<OssFactory> factory;
    private SysOssServiceImpl service;

    /**
     * 保存文件信息时会用 JsonUtils、MapstructUtils，它们从 Spring 容器取 JsonMapper 与 Converter
     */
    @BeforeAll
    static void initSpring() {
        Converter converter = mock(Converter.class);
        when(converter.convert(any(SysOss.class), eq(SysOssVo.class))).thenAnswer(invocation -> {
            SysOss oss = invocation.getArgument(0);
            SysOssVo vo = new SysOssVo();
            vo.setOriginalName(oss.getOriginalName());
            vo.setFileSuffix(oss.getFileSuffix());
            vo.setService(oss.getService());
            return vo;
        });
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        context.registerBean(Converter.class, () -> converter);
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @BeforeEach
    void setUp() {
        UploadProperties properties = new UploadProperties();
        properties.afterPropertiesSet();
        service = new SysOssServiceImpl(mapper, properties);
        factory = mockStatic(OssFactory.class);
        factory.when(OssFactory::instance).thenReturn(client);
        factory.when(() -> OssFactory.instance(anyString())).thenReturn(client);
    }

    @AfterEach
    void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("内容与扩展名不符时拒绝，且不会写入对象存储")
    void rejectDisguisedFileBeforeStorage() {
        MockMultipartFile html = new MockMultipartFile("file", "avatar.png", "image/png",
            "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        ServiceException e = assertThrows(ServiceException.class, () -> service.upload(html, null));

        assertTrue(e.getMessage().contains("不符"), e.getMessage());
        verifyNoInteractions(client, mapper);
    }

    @Test
    @DisplayName("不在白名单的类型与双扩展名拒绝")
    void rejectNotAllowed() {
        assertThrows(ServiceException.class, () -> service.upload(
            new MockMultipartFile("file", "index.html", "text/html", "<p>x</p>".getBytes(StandardCharsets.UTF_8)), null));
        assertThrows(ServiceException.class, () -> service.upload(
            new MockMultipartFile("file", "shell.jsp.png", "image/png", PNG), null));
        verifyNoInteractions(client, mapper);
    }

    @Test
    @DisplayName("对象键扩展名与媒体类型取校验后的值，不采信客户端声明")
    void storeWithServerSideTypes() {
        when(client.buildPathKey(isNull(), anyString())).thenAnswer(invocation -> "2026/10/27/uuid" + invocation.getArgument(1));
        when(client.clientId()).thenReturn("minio");
        when(client.upload(anyString(), any(InputStream.class), anyLong(), any(Options.class)))
            .thenReturn(PutObjectResult.form("http://oss/2026/10/27/uuid.png", "2026/10/27/uuid.png", "etag", PNG.length));
        // 客户端声称是 text/html
        MockMultipartFile file = new MockMultipartFile("file", "LOGO.PNG", "text/html", PNG);

        SysOssVo vo = service.upload(file, new SysOssExt());

        verify(client).buildPathKey(null, ".png");
        ArgumentCaptor<Options> options = ArgumentCaptor.forClass(Options.class);
        verify(client).upload(eq("2026/10/27/uuid.png"), any(InputStream.class), eq((long) PNG.length), options.capture());
        assertEquals("image/png", options.getValue().getContentType());
        ArgumentCaptor<SysOss> saved = ArgumentCaptor.forClass(SysOss.class);
        verify(mapper).insert(saved.capture());
        assertEquals(".png", saved.getValue().getFileSuffix());
        assertEquals("LOGO.PNG", saved.getValue().getOriginalName());
        assertTrue(saved.getValue().getExt1().contains("image/png"), saved.getValue().getExt1());
        assertEquals("LOGO.PNG", vo.getOriginalName());
    }

    @Test
    @DisplayName("对象存储访问失败时提示检查存储配置并给出错误编号，不返回存储地址等内部信息")
    void storageFailureIsReportedWithoutInternals() {
        when(client.buildPathKey(isNull(), anyString())).thenReturn("2026/11/03/uuid.png");
        when(client.clientId()).thenReturn("minio");
        when(client.upload(anyString(), any(InputStream.class), anyLong(), any(Options.class)))
            .thenThrow(S3StorageException.form("Unable to execute HTTP request: Connect to http://10.0.0.8:9000 failed"));
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", PNG);

        ServiceException e = assertThrows(ServiceException.class, () -> service.upload(file, null));

        assertTrue(e.getMessage().matches("文件存储服务访问失败，请检查存储配置 \\[错误编号: \\d{8}]"), e.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("白名单含有不支持的类型时拒绝启动")
    void rejectUnsupportedWhitelist() {
        UploadProperties properties = new UploadProperties();
        properties.setAllowedExtensions(List.of("png", "svg"));
        assertThrows(IllegalArgumentException.class, properties::afterPropertiesSet);
    }

    @Test
    @DisplayName("刷新白名单后按新配置校验")
    void refreshWhitelist() {
        UploadProperties properties = new UploadProperties();
        properties.afterPropertiesSet();
        assertTrue(properties.validator().allowedExtensions().contains("pdf"));

        properties.setAllowedExtensions(List.of("png"));

        assertEquals(List.of("png"), List.copyOf(properties.validator().allowedExtensions()));
    }

}
