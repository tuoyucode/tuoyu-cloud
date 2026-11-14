package org.tuoyu.common.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ObjectUtils：空值安全的读取")
class ObjectUtilsTest {

    @Test
    @DisplayName("对象与读取函数都不为空时才读取")
    void notNullGetter() {
        Source source = new Source("alice");
        Function<Source, String> nullFunction = null;

        assertThat(ObjectUtils.notNullGetter(source, Source::name)).isEqualTo("alice");
        assertThat(ObjectUtils.notNullGetter((Source) null, Source::name)).isNull();
        assertThat(ObjectUtils.notNullGetter(source, nullFunction)).isNull();
    }

    @Test
    @DisplayName("带默认值：无法读取时返回默认值；读取结果为 null 时返回 null")
    void notNullGetterWithDefault() {
        Source source = new Source("alice");
        Function<Source, String> nullFunction = null;

        assertThat(ObjectUtils.notNullGetter(source, Source::name, "fallback")).isEqualTo("alice");
        assertThat(ObjectUtils.notNullGetter((Source) null, Source::name, "fallback")).isEqualTo("fallback");
        assertThat(ObjectUtils.notNullGetter(source, nullFunction, "fallback")).isEqualTo("fallback");
        assertThat(ObjectUtils.notNullGetter(new Source(null), Source::name, "fallback")).isNull();
    }

    @Test
    @DisplayName("notNull：为 null 时取默认值")
    void notNull() {
        assertThat(ObjectUtils.notNull("alice", "fallback")).isEqualTo("alice");
        assertThat(ObjectUtils.notNull(null, "fallback")).isEqualTo("fallback");
        assertThat(ObjectUtils.notNull("", "fallback")).isEmpty();
    }

    @Test
    @DisplayName("isNull：null 或 equals(null) 为 true 的对象")
    void isNull() {
        assertThat(ObjectUtils.isNull(null)).isTrue();
        assertThat(ObjectUtils.isNull("")).isFalse();
        assertThat(ObjectUtils.isNull(new Object())).isFalse();
        assertThat(ObjectUtils.isNull(new NullLike())).isTrue();
    }

    private record Source(String name) {
    }

    /**
     * 表示空值的对象（例如 JSON 的 null）
     */
    private static final class NullLike {

        @Override
        public boolean equals(Object obj) {
            return obj == null || obj instanceof NullLike;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }
}
