package org.tuoyu.common.dubbo.config;

import org.apache.dubbo.config.DubboShutdownHook;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Dubbo 的停止只由 Spring 负责：不注册 Dubbo 自己的 JVM 停机钩子")
class DubboShutdownHookEnvironmentPostProcessorTest {

    private final DubboShutdownHookEnvironmentPostProcessor processor = new DubboShutdownHookEnvironmentPostProcessor();

    private String original;

    @BeforeEach
    void saveProperty() {
        original = System.getProperty(DubboShutdownHookEnvironmentPostProcessor.KEY);
        System.clearProperty(DubboShutdownHookEnvironmentPostProcessor.KEY);
    }

    @AfterEach
    void restoreProperty() {
        if (original == null) {
            System.clearProperty(DubboShutdownHookEnvironmentPostProcessor.KEY);
        } else {
            System.setProperty(DubboShutdownHookEnvironmentPostProcessor.KEY, original);
        }
    }

    @Test
    @DisplayName("设置开关后，Dubbo 的停机钩子不会注册到 JVM")
    void shouldNotRegisterDubboHook() {
        processor.postProcessEnvironment(new StandardEnvironment(), new SpringApplication());

        FrameworkModel framework = new FrameworkModel();
        try {
            DubboShutdownHook hook = new DubboShutdownHook(framework.newApplication());
            hook.register();
            assertFalse(hook.getRegistered());
        } finally {
            framework.destroy();
        }
    }

    @Test
    @DisplayName("对照：没有开关时 Dubbo 会注册自己的钩子")
    void dubboRegistersHookByDefault() {
        FrameworkModel framework = new FrameworkModel();
        try {
            DubboShutdownHook hook = new DubboShutdownHook(framework.newApplication());
            hook.register();
            assertTrue(hook.getRegistered());
            hook.unregister();
        } finally {
            framework.destroy();
        }
    }

    @Test
    @DisplayName("已显式配置时不覆盖")
    void shouldKeepExplicitValue() {
        System.setProperty(DubboShutdownHookEnvironmentPostProcessor.KEY, "false");

        processor.postProcessEnvironment(new StandardEnvironment(), new SpringApplication());

        assertEquals("false", System.getProperty(DubboShutdownHookEnvironmentPostProcessor.KEY));
    }

}
