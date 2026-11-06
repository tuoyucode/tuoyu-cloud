package org.tuoyu.system.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.tuoyu.common.core.web.ip.TrustedProxies;
import org.tuoyu.common.encrypt.api.ApiCryptoKeyRing;
import org.tuoyu.common.encrypt.api.ApiCryptoProperties;
import org.tuoyu.system.domain.vo.SecurityOverviewVo;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("安全中心")
class SecurityOverviewServiceTest {

    @Test
    @DisplayName("读取当前生效的请求大小、并发、分页上限与 Redis 白名单扩展")
    void shouldReadConfiguredProtections() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("tuoyu.request.max-body-size", "8MB")
            .withProperty("spring.servlet.multipart.max-file-size", "50MB")
            .withProperty("spring.servlet.multipart.max-request-size", "100MB")
            .withProperty("server.jetty.max-http-form-post-size", "2MB")
            .withProperty("server.jetty.threads.max", "2000")
            .withProperty("tuoyu.page.max-size", "300")
            .withProperty("tuoyu.excel.max-export-rows", "20000")
            .withProperty("tuoyu.excel.max-import-rows", "1000")
            .withProperty("tuoyu.redis.allowed-type-prefixes[0]", "com.acme.order.");

        SecurityOverviewVo.Protections protections = service(environment, null, null).protections();

        assertEquals("8MB", protections.requestMaxBodySize());
        assertEquals("50MB", protections.uploadMaxFileSize());
        assertEquals("100MB", protections.uploadMaxRequestSize());
        assertEquals("2MB", protections.formMaxSize());
        assertEquals(2000, protections.maxThreads());
        assertEquals(300, protections.pageMaxSize());
        assertEquals(20000, protections.excelMaxExportRows());
        assertEquals(1000, protections.excelMaxImportRows());
        assertEquals(List.of("com.acme.order."), protections.redisAllowlist().extraPrefixes());
        assertFalse(protections.tenantEnabled());
    }

    @Test
    @DisplayName("可信代理网段：显示规范写法；未配置时为默认网段；none 表示不信任任何代理")
    void shouldDescribeTrustedProxies() {
        MockEnvironment configured = new MockEnvironment()
            .withProperty("tuoyu.web.trusted-proxies", "10.1.2.0/24, 192.168.10.5");
        assertEquals(List.of("10.1.2.0/24", "192.168.10.5/32"),
            service(configured, null, null).protections().trustedProxies());

        assertEquals(TrustedProxies.defaults().ranges(),
            service(new MockEnvironment(), null, null).protections().trustedProxies());

        MockEnvironment none = new MockEnvironment().withProperty("tuoyu.web.trusted-proxies", "none");
        assertEquals(List.of(), service(none, null, null).protections().trustedProxies());
    }

    @Test
    @DisplayName("未配置时显示与实际生效一致的默认值；未开启接口加密时如实显示")
    void shouldShowDefaultsWhenNotConfigured() {
        SecurityOverviewVo.Protections protections = service(new MockEnvironment(), null, null).protections();

        assertEquals("10MB", protections.requestMaxBodySize());
        assertEquals(500, protections.pageMaxSize());
        assertEquals(50000, protections.excelMaxExportRows());
        assertEquals(5000, protections.excelMaxImportRows());
        assertEquals(List.of(), protections.redisAllowlist().extraPrefixes());
        assertFalse(protections.apiCrypto().enabled());
        assertNull(protections.apiCrypto().kid());
    }

    @Test
    @DisplayName("接口加密：显示算法、密钥位数、kid、是否要求 HTTPS，不显示密钥本身")
    void shouldDescribeApiCryptoWithoutSecrets() throws Exception {
        ApiCryptoProperties properties = new ApiCryptoProperties();
        properties.setPrivateKey(generatePrivateKey());
        properties.setRequireHttps(false);
        ApiCryptoKeyRing keyRing = ApiCryptoKeyRing.from(properties);

        SecurityOverviewVo.ApiCrypto apiCrypto =
            service(new MockEnvironment(), keyRing, properties).protections().apiCrypto();

        assertTrue(apiCrypto.enabled());
        assertEquals("RSA-OAEP-256+A256GCM", apiCrypto.algorithm());
        assertEquals(2048, apiCrypto.keySize());
        assertEquals(keyRing.current().kid(), apiCrypto.kid());
        assertFalse(apiCrypto.requireHttps());
        assertFalse(apiCrypto.rotating());
    }

    @SuppressWarnings("unchecked")
    private static SecurityOverviewService service(MockEnvironment environment, ApiCryptoKeyRing keyRing,
                                                   ApiCryptoProperties properties) {
        ObjectProvider<ApiCryptoKeyRing> keyRingProvider = mock(ObjectProvider.class);
        when(keyRingProvider.getIfAvailable()).thenReturn(keyRing);
        ObjectProvider<ApiCryptoProperties> propertiesProvider = mock(ObjectProvider.class);
        when(propertiesProvider.getIfAvailable()).thenReturn(properties);
        SelfCheckAggregator aggregator = new SelfCheckAggregator(environment, Optional::empty, service -> Optional.empty());
        return new SecurityOverviewService(environment, keyRingProvider, propertiesProvider, aggregator);
    }

    private static String generatePrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    }

}
