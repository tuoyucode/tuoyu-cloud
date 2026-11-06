package org.tuoyu.system.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.mock.env.MockEnvironment;
import org.tuoyu.system.domain.vo.HomeOverviewVo;
import org.tuoyu.system.mapper.SysLoginInfoMapper;
import org.tuoyu.system.mapper.SysOperLogMapper;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("首页概况")
class HomeOverviewServiceTest {

    private final SysLoginInfoMapper loginInfoMapper = mock(SysLoginInfoMapper.class);

    private final SysOperLogMapper operLogMapper = mock(SysOperLogMapper.class);

    private final DiscoveryClient discoveryClient = mock(DiscoveryClient.class);

    @Test
    @DisplayName("没有监控权限的用户只得到版本与部署信息，不查询日志与注册中心")
    void shouldHideDetailsWithoutPermission() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        Properties build = new Properties();
        build.setProperty("version", "1.0.0-SNAPSHOT");

        HomeOverviewVo overview = service(environment, new BuildProperties(build), Set.of()).overview();

        assertEquals("1.0.0-SNAPSHOT", overview.version());
        assertEquals("dev", overview.profile());
        assertEquals("microservice", overview.deploymentMode());
        assertFalse(overview.tenantEnabled());
        assertNull(overview.services());
        assertNull(overview.security());
        assertNull(overview.onlineUsers());
        assertNull(overview.loginStats());
        assertNull(overview.operationStats());
        assertNull(overview.recentOperations());
        verifyNoInteractions(loginInfoMapper, operLogMapper, discoveryClient);
    }

    @Test
    @DisplayName("服务状态只列出本项目的服务（注册中心可能与其他项目共用），没有实例的显示 0")
    void shouldListOwnServicesOnly() {
        ServiceInstance instance = mock(ServiceInstance.class);
        when(discoveryClient.getInstances(anyString())).thenReturn(List.of());
        when(discoveryClient.getInstances("tuoyu-system")).thenReturn(List.of(instance, instance));

        HomeOverviewVo overview = service(new MockEnvironment(), null, Set.of("monitor:security:list")).overview();

        assertNull(overview.version());
        List<HomeOverviewVo.ServiceStatus> services = overview.services();
        assertNotNull(services);
        assertEquals(HomeOverviewService.DEFAULT_SERVICES,
            services.stream().map(HomeOverviewVo.ServiceStatus::name).toList());
        assertEquals(2, services.stream().filter(s -> s.name().equals("tuoyu-system")).findFirst().orElseThrow().instances());
        assertEquals(0, services.stream().filter(s -> s.name().equals("tuoyu-job")).findFirst().orElseThrow().instances());
        verifyNoInteractions(loginInfoMapper, operLogMapper);
    }

    @Test
    @DisplayName("首页展示的服务与部署方式可以通过配置调整")
    void shouldUseConfiguredServicesAndMode() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("tuoyu.home.services[0]", "tuoyu-boot-all")
            .withProperty("tuoyu.deployment.mode", "monolith");
        when(discoveryClient.getInstances("tuoyu-boot-all")).thenReturn(List.of(mock(ServiceInstance.class)));

        HomeOverviewVo overview = service(environment, null, Set.of("monitor:security:list")).overview();

        assertEquals("monolith", overview.deploymentMode());
        assertEquals(List.of(new HomeOverviewVo.ServiceStatus("tuoyu-boot-all", 1)), overview.services());
    }

    @SuppressWarnings("unchecked")
    private HomeOverviewService service(MockEnvironment environment, BuildProperties build, Set<String> permissions) {
        ObjectProvider<BuildProperties> buildProvider = mock(ObjectProvider.class);
        when(buildProvider.getIfAvailable()).thenReturn(build);
        ObjectProvider<DiscoveryClient> discoveryProvider = mock(ObjectProvider.class);
        when(discoveryProvider.getIfAvailable()).thenReturn(discoveryClient);
        SelfCheckAggregator aggregator = new SelfCheckAggregator(environment, Optional::empty, service -> Optional.empty());
        return new HomeOverviewService(environment, buildProvider, discoveryProvider, loginInfoMapper, operLogMapper,
            aggregator, Clock.systemDefaultZone(), permissions::contains);
    }

}
