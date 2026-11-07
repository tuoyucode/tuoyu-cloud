package org.tuoyu.common.log.annotation;

import org.tuoyu.common.log.enums.AuditAction;
import org.tuoyu.common.log.enums.OperatorType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 记录操作日志：标注在 Controller 方法上，请求结束后（成功或抛出异常）发布操作日志事件，由 system 保存。
 * <pre>{@code
 * @OperationLog(module = "用户管理", action = AuditAction.CREATE)
 * }</pre>
 *
 * @author tuoyu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 功能模块（操作日志中的“系统模块”）
     */
    String module() default "";

    /**
     * 操作类型
     */
    AuditAction action() default AuditAction.OTHER;

    /**
     * 操作人类别
     */
    OperatorType operatorType() default OperatorType.MANAGE;

    /**
     * 是否保存请求参数
     */
    boolean saveRequestData() default true;

    /**
     * 是否保存响应内容
     */
    boolean saveResponseData() default true;

    /**
     * 不记录的请求参数名（密码等敏感字段）；{@code SystemConstants.EXCLUDE_PROPERTIES} 中的字段总是排除
     */
    String[] excludeParamNames() default {};

}
