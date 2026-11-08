package org.tuoyu.common.rpc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 跨模块调用的契约接口（放在 tuoyu-api-* 中）。
 * <p>
 * 调用方直接注入接口（普通构造器注入，不加注解）：容器中有本地实现时注入本地调用代理，
 * 否则在微服务方式下注入远程引用，在单体方式下拒绝启动。
 * <ul>
 *     <li>参数与返回值按值语义使用：提供方不修改参数，调用方不修改返回值；</li>
 *     <li>跨模块调用不参与调用方的事务；</li>
 *     <li>参数校验由提供方负责。</li>
 * </ul>
 *
 * @author tuoyu
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RemoteApi {
}
