package org.tuoyu.common.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启用 AspectJ 自动代理与异步方法。
 * <p>
 * 有 {@code @Async} 方法的 Bean 用类代理（即使它实现了接口），按实现类注入时也能取到代理对象。
 *
 * @author tuoyu
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableAsync(proxyTargetClass = true)
public class ApplicationConfig {

}
