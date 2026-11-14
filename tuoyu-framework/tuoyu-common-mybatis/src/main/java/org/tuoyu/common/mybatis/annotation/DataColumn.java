package org.tuoyu.common.mybatis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限中占位名与表中列的对应关系，只在 {@link DataPermission} 中使用。
 * <p>
 * {@code key[i]} 对应 {@code value[i]}，两者个数必须相同（不同时执行 SQL 会被拒绝）。起作用的占位名只有两个：
 * <ul>
 *     <li>{@code deptName}：部门列；</li>
 *     <li>{@code userName}：创建人列。</li>
 * </ul>
 * 其他占位名不起作用；同一个占位名出现多次时以最后一次为准。列名可以带表别名（例如 {@code d.dept_id}），
 * 原样拼进条件，所以只能写代码中的常量。
 *
 * @author tuoyu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataColumn {

    /**
     * @return 占位名
     */
    String[] key() default "deptName";

    /**
     * @return 表中的列，可以带表别名
     */
    String[] value() default "dept_id";

}
