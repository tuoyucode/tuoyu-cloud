package org.tuoyu.common.tenant.job;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按租户执行的定时任务（ADR 0006 第 6 节）：开启多租户时，标注的方法依次在每个启用且未过期的租户中执行一次，
 * 单个租户失败只记录日志，不影响其他租户；未开启多租户时只执行一次。
 * <p>
 * 方法的返回值被忽略（返回 null）。
 *
 * @author tuoyu
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TenantJob {
}
