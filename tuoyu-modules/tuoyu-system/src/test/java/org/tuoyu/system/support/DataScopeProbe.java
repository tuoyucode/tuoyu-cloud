package org.tuoyu.system.support;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.aop.framework.ProxyFactory;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.aspect.DataPermissionPointcutAdvisor;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 检查查询执行时数据权限是否生效：Mapper 的模拟对象外面套一层带数据权限切面的 Spring 代理（与运行时相同），
 * 本项目 Mapper 接口中的默认方法执行真实代码，最终落到 MyBatis-Plus 的方法（selectList、selectCount、
 * selectPage、selectJoinList 等）时记下当时的数据权限注解与 SQL 条件，并按方法名返回预设的结果。
 * <p>
 * 只依赖 Mapper 的公开接口与数据权限的公开注解，不关心服务按什么顺序、用哪个默认方法组织查询。
 *
 * @param <M> Mapper 类型
 * @author tuoyu
 */
public final class DataScopeProbe<M extends BaseMapper<?>> {

    /**
     * 一次落到 MyBatis-Plus 方法上的调用
     *
     * @param method     方法名
     * @param permission 调用时生效的数据权限注解（没有时为 null）
     * @param args       参数
     */
    public record Call(String method, DataPermission permission, Object[] args) {

        /**
         * @return 注解中部门列与创建人列的映射（占位名 → 列）
         */
        public Map<String, String> columns() {
            Map<String, String> columns = new HashMap<>();
            if (permission != null) {
                for (DataColumn column : permission.value()) {
                    for (int i = 0; i < column.key().length && i < column.value().length; i++) {
                        columns.put(column.key()[i], column.value()[i]);
                    }
                }
            }
            return columns;
        }
    }

    private final List<Call> calls = new ArrayList<>();
    private final Map<String, Function<Object[], Object>> results = new HashMap<>();
    private final M proxy;

    private DataScopeProbe(Class<M> type) {
        M target = Mockito.mock(type, Mockito.withSettings().defaultAnswer(new Recorder()));
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(type);
        factory.addAdvisor(new DataPermissionPointcutAdvisor());
        @SuppressWarnings("unchecked")
        M proxied = (M) factory.getProxy();
        this.proxy = proxied;
    }

    /**
     * @param type Mapper 接口
     * @return 探针
     */
    public static <M extends BaseMapper<?>> DataScopeProbe<M> of(Class<M> type) {
        SystemTestSupport.init();
        return new DataScopeProbe<>(type);
    }

    /**
     * @return 带数据权限切面的 Mapper（交给被测服务）
     */
    public M mapper() {
        return proxy;
    }

    /**
     * 预设某个 MyBatis-Plus 方法（按方法名，不分重载）的返回值
     */
    public DataScopeProbe<M> answer(String method, Function<Object[], Object> result) {
        results.put(method, result);
        return this;
    }

    /**
     * @return 全部记录
     */
    public List<Call> calls() {
        return calls;
    }

    /**
     * @return 方法名为其中之一的记录
     */
    public List<Call> calls(String... methods) {
        List<String> names = Arrays.asList(methods);
        return calls.stream().filter(c -> names.contains(c.method())).toList();
    }

    private final class Recorder implements Answer<Object> {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Method method = invocation.getMethod();
            boolean projectDefault = method.isDefault() && method.getDeclaringClass().getName().startsWith("org.tuoyu.");
            if (projectDefault) {
                return invocation.callRealMethod();
            }
            boolean delegating = method.isDefault()
                && method.getDeclaringClass() == BaseMapper.class
                && (("selectOne".equals(method.getName()) && method.getParameterCount() == 1)
                || ("exists".equals(method.getName()))
                || ("selectPage".equals(method.getName()) && method.getParameterCount() == 2)
                || ("update".equals(method.getName()) && method.getParameterCount() == 1));
            if (delegating) {
                return invocation.callRealMethod();
            }
            calls.add(new Call(method.getName(), DataPermissionHelper.getPermission(), invocation.getArguments()));
            Function<Object[], Object> result = results.get(method.getName());
            if (result != null) {
                return result.apply(invocation.getArguments());
            }
            if ("selectOne".equals(method.getName())) {
                return null;
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        }
    }
}
