package org.tuoyu.common.core.validate.dicts;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验值是某个字典中的值，一个字段中有多个值时用分隔符连接、每个都要在字典中。
 * <p>
 * null 与空白不校验（是否必填由 {@code @NotBlank} 等决定）。字典来自容器中的 {@code DictService}。
 *
 * @author tuoyu
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DictPatternValidator.class)
public @interface DictPattern {

    /**
     * 字典类型，例如 {@code sys_user_sex}
     */
    String dictType();

    /**
     * 一个字段中有多个字典值时的分隔符
     */
    String separator() default ",";

    String message() default "字典值无效";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
