package org.tuoyu.common.mybatis.aspect;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.tuoyu.common.core.service.DataScopeService;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.enums.DataScopeType;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.service.SysDataScopeService;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("数据权限注解：切点匹配、调用期间设置注解并在结束后恢复上一层")
class DataPermissionAdviceTest {

    @DataPermission({@DataColumn(key = "deptName", value = "d.dept_id")})
    interface AnnotatedMapper {

        void selectList();

        @DataPermission({@DataColumn(key = "userName", value = "u.user_id")})
        void selectOwn();

    }

    interface PlainMapper {

        void selectList();

    }

    static class AnnotatedService {

        @DataPermission({@DataColumn(key = "deptName", value = "dept_id")})
        public void annotated() {
        }

        public void plain() {
        }

    }

    @AfterEach
    void tearDown() {
        DataPermissionHelper.removePermission();
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> null));
    }

    @Test
    @DisplayName("切点：方法上有注解，或 MyBatis 代理的 Mapper 接口上有注解时匹配")
    void pointcut() throws Exception {
        DataPermissionPointcut pointcut = new DataPermissionPointcut();
        Object annotatedProxy = proxy(AnnotatedMapper.class);
        Object plainProxy = proxy(PlainMapper.class);

        assertThat(pointcut.matches(AnnotatedService.class.getMethod("annotated"), AnnotatedService.class)).isTrue();
        assertThat(pointcut.matches(AnnotatedService.class.getMethod("plain"), AnnotatedService.class)).isFalse();
        assertThat(pointcut.matches(AnnotatedMapper.class.getMethod("selectList"), annotatedProxy.getClass())).isTrue();
        assertThat(pointcut.matches(PlainMapper.class.getMethod("selectList"), plainProxy.getClass())).isFalse();

        DataPermissionPointcutAdvisor advisor = new DataPermissionPointcutAdvisor();
        assertThat(advisor.getPointcut()).isInstanceOf(DataPermissionPointcut.class);
        assertThat(advisor.getAdvice()).isInstanceOf(DataPermissionAdvice.class);
    }

    private static MethodInvocation invocation(Object target, Method method, AtomicReference<DataPermission> seen,
                                               Runnable inside) throws Throwable {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.proceed()).thenAnswer(i -> {
            seen.set(DataPermissionHelper.getPermission());
            inside.run();
            return "result";
        });
        return invocation;
    }

    @Test
    @DisplayName("方法上的注解优先；代理类取接口上的注解；调用结束后清除")
    void setsAnnotationDuringCall() throws Throwable {
        DataPermissionAdvice advice = new DataPermissionAdvice();
        AtomicReference<DataPermission> seen = new AtomicReference<>();

        Object mapper = proxy(AnnotatedMapper.class);
        assertThat(advice.invoke(invocation(mapper, AnnotatedMapper.class.getMethod("selectOwn"), seen, () -> {
        }))).isEqualTo("result");
        assertThat(seen.get().value()[0].key()).containsExactly("userName");
        assertThat(DataPermissionHelper.getPermission()).isNull();

        advice.invoke(invocation(mapper, AnnotatedMapper.class.getMethod("selectList"), seen, () -> {
        }));
        assertThat(seen.get().value()[0].value()).containsExactly("d.dept_id");

        advice.invoke(invocation(proxy(PlainMapper.class), PlainMapper.class.getMethod("selectList"), seen, () -> {
        }));
        assertThat(seen.get()).isNull();

        advice.invoke(invocation(new AnnotatedService(), AnnotatedService.class.getMethod("plain"), seen, () -> {
        }));
        assertThat(seen.get()).isNull();
    }

    @Test
    @DisplayName("嵌套调用：内层结束后恢复外层的注解；内层抛出异常时同样恢复")
    void restoresOuterAnnotation() throws Throwable {
        DataPermissionAdvice advice = new DataPermissionAdvice();
        AtomicReference<DataPermission> outerSeen = new AtomicReference<>();
        AtomicReference<DataPermission> innerSeen = new AtomicReference<>();
        AtomicReference<DataPermission> afterInner = new AtomicReference<>();
        Object mapper = proxy(AnnotatedMapper.class);
        Method inner = AnnotatedMapper.class.getMethod("selectOwn");

        MethodInvocation outer = invocation(new AnnotatedService(), AnnotatedService.class.getMethod("annotated"), outerSeen, () -> {
            try {
                advice.invoke(invocation(mapper, inner, innerSeen, () -> {
                    throw new IllegalStateException("inner failed");
                }));
            } catch (Throwable e) {
                assertThat(e).hasMessage("inner failed");
            }
            afterInner.set(DataPermissionHelper.getPermission());
        });
        advice.invoke(outer);

        assertThat(innerSeen.get().value()[0].key()).containsExactly("userName");
        assertThat(afterInner.get()).isSameAs(outerSeen.get());
        assertThat(DataPermissionHelper.getPermission()).isNull();
    }

    @Test
    @DisplayName("数据范围代码：已知代码返回对应类型，空白或未知代码返回 null")
    void dataScopeType() {
        assertThat(DataScopeType.findCode("1")).isEqualTo(DataScopeType.ALL);
        assertThat(DataScopeType.findCode("6")).isEqualTo(DataScopeType.DEPT_AND_CHILD_OR_SELF);
        assertThat(DataScopeType.findCode("")).isNull();
        assertThat(DataScopeType.findCode(null)).isNull();
        assertThat(DataScopeType.findCode("7")).isNull();
        assertThat(DataScopeType.ALL.getSqlTemplate()).isEmpty();
        assertThat(DataScopeType.SELF.getElseSql()).contains("1 = 0");
    }

    @Test
    @DisplayName("自定义与部门及以下数据权限委托 DataScopeService；没有实现时明确报错")
    void dataScopeService() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        assertThatThrownBy(() -> new SysDataScopeService(beans.getBeanProvider(DataScopeService.class)).getRoleCustom(1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tuoyu-common-service-impl");

        DataScopeService delegate = mock(DataScopeService.class);
        when(delegate.getRoleCustom(1L)).thenReturn("100");
        when(delegate.getDeptAndChild(2L)).thenReturn("2,3");
        beans.addBean("dataScopeService", delegate);
        SysDataScopeService service = new SysDataScopeService(beans.getBeanProvider(DataScopeService.class));
        assertThat(service.getRoleCustom(1L)).isEqualTo("100");
        assertThat(service.getDeptAndChild(2L)).isEqualTo("2,3");
    }

}
