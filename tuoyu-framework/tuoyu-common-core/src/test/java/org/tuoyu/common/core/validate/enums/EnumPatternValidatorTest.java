package org.tuoyu.common.core.validate.enums;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EnumPattern：值必须是枚举某个属性的取值之一")
class EnumPatternValidatorTest {

    private static EnumPatternValidator validatorFor(String fieldName) throws NoSuchFieldException {
        EnumPatternValidator validator = new EnumPatternValidator();
        validator.initialize(Target1.class.getDeclaredField(fieldName).getAnnotation(EnumPattern.class));
        return validator;
    }

    @Test
    @DisplayName("整数属性：输入按属性类型转换后比较；null 通过；空白与无法转换的值不通过")
    void integerField() throws NoSuchFieldException {
        EnumPatternValidator validator = validatorFor("status");

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(1, null)).isTrue();
        assertThat(validator.isValid("2", null)).isTrue();
        assertThat(validator.isValid(1L, null)).isTrue();
        assertThat(validator.isValid(9, null)).isFalse();
        assertThat(validator.isValid("invalid", null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid(" ", null)).isFalse();
    }

    @Test
    @DisplayName("字符串属性：区分大小写；空白按未填写处理；其他类型转成字符串比较")
    void stringField() throws NoSuchFieldException {
        EnumPatternValidator validator = validatorFor("label");

        assertThat(validator.isValid("a1", null)).isTrue();
        assertThat(validator.isValid("A1", null)).isFalse();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("  ", null)).isTrue();
        assertThat(validator.isValid(1, null)).isTrue();
        assertThat(validator.isValid(2, null)).isFalse();
    }

    @Test
    @DisplayName("布尔属性：字符串必须能识别为布尔值")
    void booleanField() throws NoSuchFieldException {
        EnumPatternValidator validator = validatorFor("flag");

        assertThat(validator.isValid(Boolean.TRUE, null)).isTrue();
        assertThat(validator.isValid("true", null)).isTrue();
        assertThat(validator.isValid(Boolean.FALSE, null)).isFalse();
        assertThat(validator.isValid("false", null)).isFalse();
        assertThat(validator.isValid("maybe", null)).isFalse();
    }

    @Test
    @DisplayName("长整数属性：整数与字符串输入都转换为长整数比较")
    void longField() throws NoSuchFieldException {
        EnumPatternValidator validator = validatorFor("id");

        assertThat(validator.isValid(10, null)).isTrue();
        assertThat(validator.isValid("10", null)).isTrue();
        assertThat(validator.isValid(11, null)).isFalse();
    }

    @Test
    @DisplayName("取值为 null 的常量不进入允许值；全部常量都为 null 时任何非 null 输入都不通过")
    void nullFieldValues() throws NoSuchFieldException {
        EnumPatternValidator partial = validatorFor("status");
        assertThat(partial.isValid(3, null)).isFalse();

        EnumPatternValidator allNull = validatorFor("empty");
        assertThat(allNull.isValid(null, null)).isTrue();
        assertThat(allNull.isValid("x", null)).isFalse();
    }

    @Test
    @DisplayName("没有配置属性名时，任何非 null 输入都不通过")
    void blankFieldName() throws NoSuchFieldException {
        EnumPatternValidator validator = validatorFor("unconfigured");

        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(1, null)).isFalse();
        assertThat(validator.isValid("ENABLED", null)).isFalse();
    }

    @Test
    @DisplayName("枚举没有该属性的 getter 时初始化失败")
    void missingGetter() throws NoSuchFieldException {
        EnumPattern annotation = Target1.class.getDeclaredField("missing").getAnnotation(EnumPattern.class);
        EnumPatternValidator validator = new EnumPatternValidator();

        assertThatThrownBy(() -> validator.initialize(annotation)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("经 Bean Validation 校验：默认提示、可重复标注（每个都要满足）、标在容器元素上")
    void throughValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertThat(messages(validator.validateValue(Target2.class, "status", 9))).containsExactly("输入值不在枚举范围内");
            assertThat(validator.validateValue(Target2.class, "status", 1)).isEmpty();

            assertThat(validator.validateValue(Target2.class, "both", 1)).isEmpty();
            assertThat(validator.validateValue(Target2.class, "both", 2)).hasSize(1);
            assertThat(validator.validateValue(Target2.class, "both", 3)).hasSize(1);
            assertThat(validator.validateValue(Target2.class, "both", 9)).hasSize(2);

            Target2 target = new Target2();
            target.codes = List.of(1, 9, 2);
            assertThat(validator.validate(target)).hasSize(1);
        }
    }

    @Test
    @DisplayName("注解：运行时保留、可重复、可以标在类型上")
    void annotation() throws NoSuchMethodException {
        assertThat(EnumPattern.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(EnumPattern.class.isAnnotationPresent(Documented.class)).isTrue();
        assertThat(EnumPattern.class.getAnnotation(Repeatable.class).value()).isEqualTo(EnumPattern.List.class);
        assertThat(EnumPattern.class.getAnnotation(Target.class).value()).containsExactlyInAnyOrder(
            ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
            ElementType.PARAMETER, ElementType.TYPE_USE);
        assertThat(EnumPattern.List.class.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(EnumPattern.class.getMethod("message").getDefaultValue()).isEqualTo("输入值不在枚举范围内");
    }

    private static <T> Set<String> messages(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }

    private enum Status {
        ENABLED(1), DISABLED(2), UNKNOWN(null);

        private final Integer code;

        Status(Integer code) {
            this.code = code;
        }

        public Integer getCode() {
            return code;
        }
    }

    private enum Small {
        ONE(1), THREE(3);

        private final Integer code;

        Small(Integer code) {
            this.code = code;
        }

        public Integer getCode() {
            return code;
        }
    }

    private enum Label {
        A("a1"), B("1");

        private final String text;

        Label(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    private enum Flag {
        ON(true);

        private final Boolean enabled;

        Flag(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getEnabled() {
            return enabled;
        }
    }

    private enum Ident {
        X(10L);

        private final Long id;

        Ident(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }

    private enum Empty {
        NONE;

        public String getValue() {
            return null;
        }
    }

    private static class Target1 {

        @EnumPattern(type = Status.class, fieldName = "code")
        Integer status;

        @EnumPattern(type = Label.class, fieldName = "text")
        String label;

        @EnumPattern(type = Flag.class, fieldName = "enabled")
        Object flag;

        @EnumPattern(type = Ident.class, fieldName = "id")
        Object id;

        @EnumPattern(type = Empty.class, fieldName = "value")
        String empty;

        @EnumPattern(type = Status.class, fieldName = "")
        Integer unconfigured;

        @EnumPattern(type = Status.class, fieldName = "missing")
        Integer missing;
    }

    static class Target2 {

        @EnumPattern(type = Status.class, fieldName = "code")
        Integer status;

        @EnumPattern(type = Status.class, fieldName = "code")
        @EnumPattern(type = Small.class, fieldName = "code")
        Integer both;

        List<@EnumPattern(type = Status.class, fieldName = "code") Integer> codes;
    }
}
