package org.tuoyu.common.core.utils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import java.util.Set;

/**
 * 在代码中手动执行参数校验。
 *
 * @author tuoyu
 */
public final class ValidatorUtils {

    private ValidatorUtils() {
    }

    /**
     * 用容器中的校验器按分组校验对象。
     *
     * @param object 要校验的对象
     * @param groups 校验分组
     * @param <T>    对象类型
     * @throws RuntimeException             对象为 null 时
     * @throws ConstraintViolationException 有违反的约束时
     */
    public static <T> void validate(T object, Class<?>... groups) {
        if (object == null) {
            // 全局异常处理按类型决定状态码，这里抛出的就是 RuntimeException
            throw new RuntimeException("请求参数不能为空");
        }
        Validator validator = SpringUtils.getBean(Validator.class);
        Set<ConstraintViolation<T>> violations = validator.validate(object, groups);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("参数校验异常", violations);
        }
    }

}
