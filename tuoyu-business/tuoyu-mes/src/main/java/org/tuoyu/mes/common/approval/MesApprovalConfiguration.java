package org.tuoyu.mes.common.approval;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 单据审批的配置
 *
 * @author tuoyu
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MesApprovalProperties.class)
public class MesApprovalConfiguration {

}
