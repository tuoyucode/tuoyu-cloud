package org.tuoyu.gateway.support;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * 网关单元测试的最小 Spring 环境：JsonUtils 需要的 JsonMapper，以及 actuator Basic 认证读取的配置。只初始化一次
 */
public final class GatewayTestContext {

    public static final String ACTUATOR_USERNAME = "monitor";

    /**
     * 每次运行随机生成，不在仓库中保存口令
     */
    public static final String ACTUATOR_PASSWORD = UUID.randomUUID().toString();

    private static boolean initialized;

    private GatewayTestContext() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(new MockEnvironment()
            .withProperty("spring.cloud.nacos.discovery.metadata.username", ACTUATOR_USERNAME)
            .withProperty("spring.cloud.nacos.discovery.metadata.userpassword", ACTUATOR_PASSWORD));
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        initialized = true;
    }

}
