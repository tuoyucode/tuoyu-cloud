package org.tuoyu.common.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.rpc.config.RpcAutoConfiguration;
import org.tuoyu.common.rpc.support.LocalRemoteProxy;
import org.tuoyu.common.rpc.support.LocalRemoteProxyFactoryBean;
import org.tuoyu.common.rpc.testapi.GreetingApi;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("跨模块调用：注入规则与本地调用代理（ADR 0005）")
class RemoteApiRegistrarTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RpcAutoConfiguration.class))
        .withPropertyValues("tuoyu.rpc.api-packages=org.tuoyu.common.rpc.testapi");

    @Test
    @DisplayName("有本地实现时，调用方注入的是本地调用代理，调用直接到达实现")
    void injectsLocalProxy() {
        runner.withUserConfiguration(Provider.class, Consumer.class).run(context -> {
            GreetingApi api = context.getBean(Consumer.class).api;
            assertInstanceOf(LocalRemoteProxy.class, api);
            assertTrue(Proxy.isProxyClass(api.getClass()));
            assertEquals("hello tuoyu", api.greet("tuoyu"));
            assertThat(context.getBean(RemoteApiRegistry.class).localImplementations())
                .containsEntry(GreetingApi.class, "greetingImpl");
        });
    }

    @Test
    @DisplayName("提供方抛出的异常原样到达调用方")
    void rethrowsOriginalException() {
        runner.withUserConfiguration(Provider.class, Consumer.class).run(context -> {
            ServiceException e = assertThrows(ServiceException.class, () -> context.getBean(Consumer.class).api.fail("boom"));
            assertEquals("boom", e.getMessage());
        });
    }

    @Test
    @DisplayName("跨模块调用不参与调用方事务：调用方有事务时，提供方中看不到它")
    void suspendsCallerTransaction() {
        runner.withUserConfiguration(Provider.class, Consumer.class, Transactions.class).run(context -> {
            GreetingApi api = context.getBean(Consumer.class).api;
            TransactionTemplate template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            Boolean providerInTransaction = template.execute(status -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return api.inTransaction();
            });
            assertEquals(Boolean.FALSE, providerInTransaction);
        });
    }

    @Test
    @DisplayName("严格模式：拷贝参数与返回值，提供方修改参数不影响调用方")
    void strictCopy() {
        runner.withUserConfiguration(Provider.class, Consumer.class)
            .withPropertyValues(LocalRemoteProxyFactoryBean.STRICT_COPY_KEY + "=true")
            .run(context -> {
                GreetingApi.Box box = new GreetingApi.Box();
                GreetingApi.Box returned = context.getBean(Consumer.class).api.append(box);
                assertTrue(box.items.isEmpty(), "调用方的对象不应被提供方修改");
                assertEquals(List.of("added"), returned.items);
            });
        runner.withUserConfiguration(Provider.class, Consumer.class).run(context -> {
            GreetingApi.Box box = new GreetingApi.Box();
            assertSame(box, context.getBean(Consumer.class).api.append(box), "默认不拷贝，与单体中的直接调用相同");
        });
    }

    @Test
    @DisplayName("微服务中没有本地实现：注入 RPC 实现创建的远程引用")
    void injectsRemoteReference() {
        runner.withUserConfiguration(Consumer.class, FakeRpc.class).run(context -> {
            assertEquals("remote tuoyu", context.getBean(Consumer.class).api.greet("tuoyu"));
            assertThat(context.getBean(RemoteApiRegistry.class).remoteApis()).containsExactly(GreetingApi.class);
        });
    }

    @Test
    @DisplayName("单体中没有实现时拒绝启动，并列出接口")
    void monolithRequiresImplementation() {
        runner.withUserConfiguration(Consumer.class).withPropertyValues("tuoyu.deployment.mode=monolith")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining(GreetingApi.class.getName());
            });
    }

    @Test
    @DisplayName("同一接口有多个实现时拒绝启动")
    void rejectsDuplicateImplementations() {
        runner.withUserConfiguration(Provider.class, AnotherProvider.class)
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("部署方式只能是 microservice 或 monolith")
    void rejectsUnknownMode() {
        runner.withPropertyValues("tuoyu.deployment.mode=cluster").run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class Provider {
        @Bean
        GreetingApi greetingImpl() {
            return new GreetingApi() {
                @Override
                public String greet(String name) {
                    return "hello " + name;
                }

                @Override
                public Box append(Box box) {
                    box.items.add("added");
                    return box;
                }

                @Override
                public void fail(String message) {
                    throw new ServiceException(message);
                }

                @Override
                public boolean inTransaction() {
                    return TransactionSynchronizationManager.isActualTransactionActive();
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AnotherProvider {
        @Bean
        GreetingApi anotherGreeting() {
            return (GreetingApi) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{GreetingApi.class},
                (proxy, method, args) -> null);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Consumer {
        final GreetingApi api;

        Consumer(GreetingApi api) {
            this.api = api;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FakeRpc {
        @Bean
        RemoteReferenceFactory remoteReferenceFactory() {
            return new RemoteReferenceFactory() {
                @Override
                public <T> T create(Class<T> api) {
                    return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api},
                        (proxy, method, args) -> "remote " + args[0]));
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Transactions {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new InMemoryTransactionManager();
        }
    }

    /**
     * 不连接数据库的事务管理器，只用来验证挂起与恢复
     */
    static class InMemoryTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object[]{TransactionSynchronizationManager.getResource(this)};
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((Object[]) transaction)[0] != null;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
        }

        @Override
        protected Object doSuspend(Object transaction) {
            return TransactionSynchronizationManager.unbindResource(this);
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            TransactionSynchronizationManager.bindResource(this, suspendedResources);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            if (TransactionSynchronizationManager.hasResource(this)) {
                TransactionSynchronizationManager.unbindResource(this);
            }
        }
    }

}
