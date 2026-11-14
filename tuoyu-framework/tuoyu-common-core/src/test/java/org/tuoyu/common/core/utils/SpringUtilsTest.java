package org.tuoyu.common.core.utils;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把 SpringUtils 注册为 Bean：它在容器启动时把容器存入 hutool 的静态字段。
 * 结束时复原静态字段，避免影响之后用 {@code setApplicationContext} 准备容器的测试。
 */
@DisplayName("SpringUtils：静态访问 Spring 容器")
class SpringUtilsTest {

    private static GenericApplicationContext context;

    private static ApplicationContext previous;

    @BeforeAll
    static void start() {
        previous = SpringUtil.getApplicationContext();
        context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
            Map.of("spring.application.name", "core-test", "demo.flag", "false")));
        context.registerBean(SpringUtils.class);
        context.registerBean(ProxyingPostProcessor.class);
        context.registerBean("target", Target.class);
        context.refresh();
    }

    @AfterAll
    static void stop() {
        context.close();
        new SpringUtil().postProcessBeanFactory(null);
        new SpringUtil().setApplicationContext(previous);
    }

    @Test
    @DisplayName("注册为 Bean 后可以取到容器与 Bean")
    void context() {
        assertThat(SpringUtils.context()).isSameAs(context);
        assertThat(SpringUtils.getBeanFactory()).isNotNull();
        assertThat(SpringUtils.getBean(Target.class)).isSameAs(context.getBean(Target.class));
        Target byName = SpringUtils.getBean("target");
        assertThat(byName).isSameAs(context.getBean(Target.class));
    }

    @Test
    @DisplayName("按名称判断 Bean 是否存在")
    void containsBean() {
        assertThat(SpringUtils.containsBean("target")).isTrue();
        assertThat(SpringUtils.containsBean("missing")).isFalse();
    }

    @Test
    @DisplayName("getAopProxy：用 Bean 内部的 this 取得它的代理对象")
    void aopProxy() {
        Target raw = ProxyingPostProcessor.rawTarget;
        Target proxy = SpringUtils.getAopProxy(raw);

        assertThat(AopUtils.isAopProxy(raw)).isFalse();
        assertThat(AopUtils.isAopProxy(proxy)).isTrue();
        assertThat(proxy).isSameAs(context.getBean(Target.class));
        assertThat(proxy.hello()).isEqualTo("hi");
    }

    @Test
    @DisplayName("读取配置与应用名")
    void properties() {
        assertThat(SpringUtils.getApplicationName()).isEqualTo("core-test");
        assertThat(SpringUtils.getProperty("demo.flag", "x")).isEqualTo("false");
        assertThat(SpringUtils.getProperty("missing.key", "x")).isEqualTo("x");
        assertThat(SpringUtils.getProperty("demo.flag", Boolean.class, Boolean.TRUE)).isFalse();
        assertThat(SpringUtils.getProperty("missing.key", Boolean.class, Boolean.TRUE)).isTrue();
    }

    public static class Target {

        public String hello() {
            return "hi";
        }
    }

    /**
     * 把 Target 包装成类代理，并记下被包装的原始对象
     */
    public static class ProxyingPostProcessor implements BeanPostProcessor {

        static volatile Target rawTarget;

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof Target target) {
                rawTarget = target;
                ProxyFactory factory = new ProxyFactory(bean);
                factory.setProxyTargetClass(true);
                return factory.getProxy();
            }
            return bean;
        }
    }
}
