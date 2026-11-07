package org.tuoyu.common.redis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交：同一用户（按令牌区分）以相同参数请求同一地址，间隔小于 {@link #interval()} 时拒绝（429）。
 * 请求失败（抛出异常或返回失败响应）时释放，可以立即重试。
 *
 * @author tuoyu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventResubmit {

    /**
     * 间隔时间，不能小于 1 秒
     */
    int interval() default 5000;

    /**
     * 间隔时间的单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 拒绝时的提示，支持国际化，格式为 {code}
     */
    String message() default "{repeat.submit.message}";

}
