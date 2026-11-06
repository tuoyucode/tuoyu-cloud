package org.tuoyu.system.domain;

import io.github.linpeilie.Converter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.system.support.SystemTestSupport;

import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 对象转换契约：编译期生成的每一个转换（Bo → 实体、实体 → Vo、Vo → 跨模块对象等），
 * 同名、同类型的属性转换后值不丢失。新增或修改 {@code @AutoMapper} 时由本测试兜底，
 * 例如目标类型新增了字段但转换配置把它忽略了。
 */
@DisplayName("对象转换：同名属性转换后值不丢失")
class ObjectConversionContractTest {

    static {
        SystemTestSupport.init();
    }

    private static final String MANIFEST = "META-INF/mapstruct-plus/module-mappers";

    /**
     * 有意不复制的属性（类型简单名.属性名）。为空表示所有同名属性都应复制
     */
    private static final Set<String> INTENTIONALLY_SKIPPED = Set.of();

    @Test
    @DisplayName("每个生成的转换都能把样例对象的同名属性复制到目标对象")
    void everyGeneratedMappingCopiesSameNamedProperties() throws Exception {
        Converter converter = SpringUtils.getBean(Converter.class);
        List<Class<?>> mappers = systemMappers();
        assertThat(mappers).as("系统模块应当有生成的转换类").hasSizeGreaterThan(40);

        List<String> problems = new ArrayList<>();
        int checkedPairs = 0;
        for (Class<?> mapper : mappers) {
            for (Method method : mapper.getDeclaredMethods()) {
                if (!isConvert(method)) {
                    continue;
                }
                Class<?> sourceType = method.getParameterTypes()[0];
                Class<?> targetType = method.getReturnType();
                Object source = sample(sourceType);

                // 转换为新对象
                Object target = converter.convert(source, targetType);
                compare(source, target, mapper, problems);
                // 填充已有对象
                Object existing = targetType.getDeclaredConstructor().newInstance();
                Object filled = converter.convert(source, existing);
                compare(source, filled, mapper, problems);
                checkedPairs++;

                // 空对象转换为空，填充时保留原目标
                Object instance = newMapper(mapper);
                assertNull(method.invoke(instance, (Object) null), mapper.getSimpleName() + " 转换 null 应返回 null");
                Method fill = fillMethod(mapper, sourceType, targetType);
                if (fill != null) {
                    Object kept = targetType.getDeclaredConstructor().newInstance();
                    assertSame(kept, fill.invoke(instance, null, kept), mapper.getSimpleName() + " 填充 null 应保留原目标");
                }
            }
        }
        assertThat(checkedPairs).isGreaterThan(40);
        assertThat(problems).as("转换后丢失的属性").isEmpty();
    }

    private static boolean isConvert(Method method) {
        return method.getName().equals("convert")
            && method.getParameterCount() == 1
            && !method.isBridge()
            && !method.isSynthetic()
            && Modifier.isPublic(method.getModifiers());
    }

    private static Method fillMethod(Class<?> mapper, Class<?> sourceType, Class<?> targetType) {
        for (Method method : mapper.getDeclaredMethods()) {
            if (method.getName().equals("convert") && method.getParameterCount() == 2 && !method.isBridge()
                && method.getParameterTypes()[0] == sourceType && method.getParameterTypes()[1] == targetType) {
                return method;
            }
        }
        return null;
    }

    private static Object newMapper(Class<?> mapper) throws Exception {
        return mapper.getDeclaredConstructor().newInstance();
    }

    /**
     * 本模块（系统模块）编译期生成的转换类，来自 mapstruct-plus 的清单
     */
    private static List<Class<?>> systemMappers() throws Exception {
        Set<String> names = new LinkedHashSet<>();
        ClassLoader loader = ObjectConversionContractTest.class.getClassLoader();
        Enumeration<URL> manifests = loader.getResources(MANIFEST);
        while (manifests.hasMoreElements()) {
            URL url = manifests.nextElement();
            if (!url.toString().contains("tuoyu-system")) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                reader.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("bean="))
                    .map(line -> line.substring("bean=".length()))
                    .forEach(names::add);
            }
        }
        List<Class<?>> classes = new ArrayList<>();
        for (String name : names) {
            classes.add(Class.forName(name, false, loader));
        }
        return classes;
    }

    private static void compare(Object source, Object target, Class<?> mapper, List<String> problems) {
        BeanWrapper from = new BeanWrapperImpl(source);
        BeanWrapper to = new BeanWrapperImpl(target);
        for (PropertyDescriptor property : from.getPropertyDescriptors()) {
            String name = property.getName();
            if (property.getReadMethod() == null || name.equals("class") || !to.isReadableProperty(name)
                || !to.isWritableProperty(name)) {
                continue;
            }
            if (to.getPropertyType(name) != property.getPropertyType()) {
                continue;
            }
            if (INTENTIONALLY_SKIPPED.contains(target.getClass().getSimpleName() + "." + name)) {
                continue;
            }
            Object expected = from.getPropertyValue(name);
            Object actual = to.getPropertyValue(name);
            boolean same = expected instanceof Collection<?> c && actual instanceof Collection<?> a
                ? c.size() == a.size()
                : expected instanceof Object[] e && actual instanceof Object[] a2
                ? e.length == a2.length
                : Objects.equals(expected, actual);
            if (!same) {
                problems.add(mapper.getSimpleName() + ": " + source.getClass().getSimpleName() + "." + name
                    + " -> " + target.getClass().getSimpleName() + "." + name);
            }
        }
    }

    /**
     * 每个可写属性都填上可区分的样例值
     */
    private static Object sample(Class<?> type) throws Exception {
        Object bean = type.getDeclaredConstructor().newInstance();
        BeanWrapper wrapper = new BeanWrapperImpl(bean);
        for (PropertyDescriptor property : wrapper.getPropertyDescriptors()) {
            if (property.getWriteMethod() == null) {
                continue;
            }
            Object value = sampleValue(property);
            if (value != null) {
                wrapper.setPropertyValue(property.getName(), value);
            }
        }
        return bean;
    }

    private static Object sampleValue(PropertyDescriptor property) throws Exception {
        Class<?> type = property.getPropertyType();
        String name = property.getName();
        if (type == String.class) {
            return "v-" + name;
        }
        if (type == Long.class || type == long.class) {
            return 7L;
        }
        if (type == Integer.class || type == int.class) {
            return 3;
        }
        if (type == Boolean.class || type == boolean.class) {
            return true;
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        }
        if (type == LocalDate.class) {
            return LocalDate.of(2026, 1, 2);
        }
        if (type == Date.class) {
            return new Date(1_700_000_000_000L);
        }
        if (type == BigDecimal.class) {
            return BigDecimal.TEN;
        }
        if (type == Long[].class) {
            return new Long[]{1L, 2L};
        }
        if (type == Integer[].class) {
            return new Integer[]{1, 2};
        }
        if (type == String[].class) {
            return new String[]{"a", "b"};
        }
        if (Map.class.isAssignableFrom(type)) {
            Map<String, Object> map = new HashMap<>();
            map.put("k", "v");
            return map;
        }
        if (List.class.isAssignableFrom(type) && property.getReadMethod() != null) {
            Type generic = property.getReadMethod().getGenericReturnType();
            if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
                if (element == String.class) {
                    return new ArrayList<>(List.of("a", "b"));
                }
                if (element == Long.class) {
                    return new ArrayList<>(List.of(1L, 2L));
                }
                if (element.getName().startsWith("org.tuoyu.")) {
                    List<Object> list = new ArrayList<>();
                    list.add(element.getDeclaredConstructor().newInstance());
                    return list;
                }
            }
        }
        return null;
    }
}
