package org.tuoyu.common.mybatis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 忽略租户隔离：标注的方法（或类中的全部方法）执行期间，SQL 不加租户条件。
 * <p>
 * 用于确实需要跨租户的场景，例如平台统计、按租户编码查询租户本身。可以标注在 Mapper 或 Service 的方法、类上；
 * 代码块中临时忽略请用 {@code TenantHelper.ignore(...)}。
 *
 * @author tuoyu
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TenantIgnore {
}
