package org.tuoyu.common.mybatis.aspect;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.framework.ProxyFactory;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 数据权限切面：调用标注了 {@link DataPermission} 的方法（或 Mapper 接口上标注了它的方法）期间，
 * 把注解放进数据权限上下文，结束后恢复上一层。只通过 {@link DataPermissionPointcutAdvisor} 使用。
 */
@DisplayName("数据权限注解：切点匹配、调用期间设置注解并在结束后恢复上一层")
class DataPermissionAdviceTest {

    @DataPermission({@DataColumn(key = "deptName", value = "d.dept_id")})
    interface AnnotatedMapper {

        String selectList();

        @DataPermission({@DataColumn(key = "userName", value = "u.user_id")})
        String selectOwn();

    }

    interface PlainMapper {

        String selectList();

    }

    /**
     * 与 MyBatis Mapper 相同的调用方式：默认方法在 Mapper 代理内部转调抽象方法，不经过外层的 Spring 代理
     */
    interface DemoMapper {

        @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
        String selectAnnotated();

        String update(String entity);

        @DataPermission({@DataColumn(key = "userName", value = "create_by")})
        default String annotatedDefault() {
            return update("x");
        }

        default String plainDefault() {
            return update("x");
        }

    }

    static class AnnotatedService {

        @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
        public String annotated() {
            return "ok";
        }

        public String plain() {
            return "ok";
        }

    }

    @DataPermission({@DataColumn(key = "deptName", value = "s.dept_id")})
    static class ClassAnnotatedService {

        public String any() {
            DataPermission permission = DataPermissionHelper.getPermission();
            return permission == null ? "none" : permission.value()[0].value()[0];
        }

    }

    private final DataPermissionPointcutAdvisor advisor = new DataPermissionPointcutAdvisor();

    @AfterEach
    void tearDown() {
        DataPermissionHelper.removePermission();
    }

    /**
     * MyBatis Mapper 式的 JDK 代理：抽象方法返回执行时看到的注解（没有时为 "none"），默认方法在代理自身上执行
     */
    private static <T> T mybatisProxy(Class<T> type) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "MapperProxy";
                };
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            DataPermission permission = DataPermissionHelper.getPermission();
            return permission == null ? "none" : permission.value()[0].value()[0];
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    /**
     * 与容器中相同：目标外面包一层带数据权限切面的 Spring 代理
     */
    private <T> T springProxy(Object target, Class<T> type) {
        ProxyFactory factory = new ProxyFactory(target);
        if (type.isInterface()) {
            factory.setInterfaces(type);
        } else {
            factory.setProxyTargetClass(true);
        }
        factory.addAdvisor(advisor);
        return type.cast(factory.getProxy(type.getClassLoader()));
    }

    @Test
    @DisplayName("切点：方法上有注解、目标类上有注解、或 MyBatis 代理的 Mapper 接口上有注解时匹配")
    void pointcut() throws Exception {
        MethodMatcher matcher = advisor.getPointcut().getMethodMatcher();
        Object annotatedProxy = mybatisProxy(AnnotatedMapper.class);
        Object plainProxy = mybatisProxy(PlainMapper.class);

        assertThat(advisor.getPointcut().getClassFilter().matches(AnnotatedService.class)).isTrue();
        assertThat(matcher.matches(AnnotatedService.class.getMethod("annotated"), AnnotatedService.class)).isTrue();
        assertThat(matcher.matches(AnnotatedService.class.getMethod("plain"), AnnotatedService.class)).isFalse();
        assertThat(matcher.matches(ClassAnnotatedService.class.getMethod("any"), ClassAnnotatedService.class)).isTrue();
        assertThat(matcher.matches(AnnotatedMapper.class.getMethod("selectList"), annotatedProxy.getClass())).isTrue();
        assertThat(matcher.matches(AnnotatedMapper.class.getMethod("selectOwn"), annotatedProxy.getClass())).isTrue();
        assertThat(matcher.matches(PlainMapper.class.getMethod("selectList"), plainProxy.getClass())).isFalse();
        assertThat(advisor.getAdvice()).isInstanceOf(MethodInterceptor.class);
    }

    @Test
    @DisplayName("经 Spring 代理调用：方法上的注解优先，其次是 Mapper 接口上的注解；调用结束后清除")
    void throughSpringProxy() {
        AnnotatedMapper mapper = springProxy(mybatisProxy(AnnotatedMapper.class), AnnotatedMapper.class);
        assertThat(mapper.selectOwn()).isEqualTo("u.user_id");
        assertThat(mapper.selectList()).isEqualTo("d.dept_id");
        assertThat(DataPermissionHelper.getPermission()).isNull();

        PlainMapper plain = springProxy(mybatisProxy(PlainMapper.class), PlainMapper.class);
        assertThat(plain.selectList()).isEqualTo("none");

        ClassAnnotatedService service = springProxy(new ClassAnnotatedService(), ClassAnnotatedService.class);
        assertThat(service.any()).isEqualTo("s.dept_id");
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    @Test
    @DisplayName("Mapper 的多个方法：标注的方法、标注的默认方法转调的抽象方法都带注解；未标注的默认方法转调时不带（不经过 Spring 代理）")
    void multipleMapperMethods() {
        DemoMapper mapper = springProxy(mybatisProxy(DemoMapper.class), DemoMapper.class);
        List<String> seen = new ArrayList<>();
        seen.add(mapper.selectAnnotated());
        seen.add(mapper.annotatedDefault());
        seen.add(mapper.plainDefault());
        seen.add(mapper.update("x"));
        assertThat(seen).containsExactly("dept_id", "create_by", "none", "none");
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    private static MethodInvocation invocation(Object target, Method method, AtomicReference<DataPermission> seen,
                                               Runnable inside) {
        return new MethodInvocation() {
            @Override
            public Method getMethod() {
                return method;
            }

            @Override
            public Object[] getArguments() {
                return new Object[0];
            }

            @Override
            public Object proceed() {
                seen.set(DataPermissionHelper.getPermission());
                inside.run();
                return "result";
            }

            @Override
            public Object getThis() {
                return target;
            }

            @Override
            public java.lang.reflect.AccessibleObject getStaticPart() {
                return method;
            }
        };
    }

    @Test
    @DisplayName("嵌套调用：内层结束后恢复外层的注解；内层抛出异常时同样恢复，异常原样抛出")
    void restoresOuterAnnotation() throws Throwable {
        MethodInterceptor advice = (MethodInterceptor) advisor.getAdvice();
        AtomicReference<DataPermission> outerSeen = new AtomicReference<>();
        AtomicReference<DataPermission> innerSeen = new AtomicReference<>();
        AtomicReference<DataPermission> afterInner = new AtomicReference<>();
        Object mapper = mybatisProxy(AnnotatedMapper.class);
        Method inner = AnnotatedMapper.class.getMethod("selectOwn");

        MethodInvocation outer = invocation(new AnnotatedService(), AnnotatedService.class.getMethod("annotated"), outerSeen, () -> {
            assertThatThrownBy(() -> advice.invoke(invocation(mapper, inner, innerSeen, () -> {
                throw new IllegalStateException("inner failed");
            }))).hasMessage("inner failed");
            afterInner.set(DataPermissionHelper.getPermission());
        });
        assertThat(advice.invoke(outer)).isEqualTo("result");

        assertThat(innerSeen.get().value()[0].key()).containsExactly("userName");
        assertThat(outerSeen.get().value()[0].value()).containsExactly("dept_id");
        assertThat(afterInner.get()).isSameAs(outerSeen.get());
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    @Test
    @DisplayName("没有注解的方法经过切面时，执行期间没有注解；之前已有的注解在结束后恢复")
    void plainMethodClearsDuringCall() throws Throwable {
        MethodInterceptor advice = (MethodInterceptor) advisor.getAdvice();
        DataPermission outer = AnnotatedService.class.getMethod("annotated").getAnnotation(DataPermission.class);
        DataPermissionHelper.setPermission(outer);
        AtomicReference<DataPermission> seen = new AtomicReference<>();
        advice.invoke(invocation(new AnnotatedService(), AnnotatedService.class.getMethod("plain"), seen, () -> {
        }));
        assertThat(seen.get()).isNull();
        assertThat(DataPermissionHelper.getPermission()).isSameAs(outer);
    }

}
