package org.tuoyu.mes.equipment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 设备管理的配置项
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MesMaintenanceProperties.class)
public class MesEquipmentConfiguration {
}
