package org.tuoyu.common.core.utils.reflect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReflectUtils：按属性路径调用 getter，以及继承的字段工具")
class ReflectUtilsTest {

    @Test
    @DisplayName("按 . 分隔的路径逐级调用 getter；getter 可以在父类中、可以不是 public")
    void invokeGetter() {
        Child child = new Child();

        String name = ReflectUtils.invokeGetter(child, "inner.name");
        assertThat(name).isEqualTo("inner");
        String baseName = ReflectUtils.invokeGetter(child, "baseName");
        assertThat(baseName).isEqualTo("base");
        Inner inner = ReflectUtils.invokeGetter(child, "inner");
        assertThat(inner).isSameAs(child.getInner());
    }

    @Test
    @DisplayName("找不到 getter、或中间一级为 null 时抛出异常")
    void invokeGetterFailures() {
        assertThatThrownBy(() -> ReflectUtils.invokeGetter(new Child(), "missing"))
            .isInstanceOf(RuntimeException.class);
        Child child = new Child();
        child.inner = null;
        assertThatThrownBy(() -> ReflectUtils.invokeGetter(child, "inner.name"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("继承的字段工具：getField、getFields、getStaticFieldValue、setFieldValue")
    void fieldTools() throws IllegalAccessException {
        Child child = new Child();

        Field secret = ReflectUtils.getField(Child.class, "secret");
        assertThat(secret).isNotNull();
        assertThat(ReflectUtils.getField(Child.class, "baseName")).isNotNull();
        assertThat(ReflectUtils.getField(Child.class, "missing")).isNull();

        assertThat(Arrays.stream(ReflectUtils.getFields(Child.class)).map(Field::getName))
            .contains("inner", "secret", "CONSTANT", "baseName");
        assertThat(Arrays.stream(ReflectUtils.getFields(Child.class, field -> field.getName().startsWith("sec")))
            .map(Field::getName)).containsExactly("secret");

        assertThat(ReflectUtils.getStaticFieldValue(ReflectUtils.getField(Child.class, "CONSTANT"))).isEqualTo("c");

        ReflectUtils.setFieldValue(child, "secret", "changed");
        secret.setAccessible(true);
        assertThat(secret.get(child)).isEqualTo("changed");
    }

    static class Base {

        private final String baseName = "base";

        public String getBaseName() {
            return baseName;
        }
    }

    static class Child extends Base {

        static final String CONSTANT = "c";

        private Inner inner = new Inner();

        private String secret = "s";

        Inner getInner() {
            return inner;
        }
    }

    static class Inner {

        private final String name = "inner";

        public String getName() {
            return name;
        }
    }
}
