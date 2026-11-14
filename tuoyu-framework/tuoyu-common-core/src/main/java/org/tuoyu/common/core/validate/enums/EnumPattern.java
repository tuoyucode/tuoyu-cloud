package org.tuoyu.common.core.validate.enums;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验值是某个枚举的某个属性的取值之一，例如 {@code @EnumPattern(type = UserStatus.class, fieldName = "code")}。
 * <p>
 * 值先转换为该属性的类型再比较；null 通过。同一元素上可以标注多个，每个都要满足；
 * 也可以标在容器元素上，例如 {@code List<@EnumPattern(...) Integer>}。
 *
 * @author tuoyu
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
    ElementType.PARAMETER, ElementType.TYPE_USE})
@Constraint(validatedBy = {EnumPatternValidator.class})
@Repeatable(EnumPattern.List.class)
public @interface EnumPattern {

    /**
     * 枚举类型
     */
    Class<? extends Enum<?>> type();

    /**
     * 枚举中用来比较的属性名，读取它的 getter（{@code get} + 首字母大写的属性名）
     */
    String fieldName();

    String message() default "输入值不在枚举范围内";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 在同一元素上标注多个 {@link EnumPattern}
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
        ElementType.PARAMETER, ElementType.TYPE_USE})
    @interface List {

        EnumPattern[] value();

    }

}
