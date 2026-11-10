package org.tuoyu.mes.andon.escalation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安灯超时检查的配置项
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AndonEscalationProperties.class)
public class AndonEscalationConfiguration {

}
