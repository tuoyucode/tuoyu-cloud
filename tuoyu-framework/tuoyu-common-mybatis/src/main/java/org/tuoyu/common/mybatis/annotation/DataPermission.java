package org.tuoyu.common.mybatis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限：说明标注的方法（或类、Mapper 接口中的全部方法）执行的 SQL 中，哪一列是部门、哪一列是创建人。
 * <p>
 * 工作方式：
 * <ol>
 *     <li>标注在 Mapper 的方法、Mapper 接口或 Service 类上，每个 {@link DataColumn} 给出占位名与列的对应关系；</li>
 *     <li>Spring 切面在调用这个方法期间把注解放进当前线程，结束后恢复上一层；</li>
 *     <li>期间执行的 SQL 由 MyBatis-Plus 的内部拦截器按当前用户各角色的数据范围加上条件（只加在最外层的 WHERE）；</li>
 *     <li>没有注解、处于忽略数据权限的代码块中、或 Mapper 标注了 {@code @InterceptorIgnore(dataPermission = "true")} 时不改写。</li>
 * </ol>
 * 只有经过 Spring 代理调用的方法才带注解。MyBatis-Plus {@code BaseMapper} 的默认方法在 MyBatis 代理内部转调其他方法，
 * 不经过 Spring 代理（例如 {@code update(Wrapper)} 转调 {@code update(实体, Wrapper)}），所以带数据权限的 Mapper
 * 要把业务会调用的默认方法也覆盖并标注。
 * <p>
 * 注解不向子类继承，也不从父类、接口的方法上查找：标注在哪个方法或类上就只对它生效。
 *
 * @author tuoyu
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /**
     * @return 占位名与列的对应关系
     */
    DataColumn[] value();

}
