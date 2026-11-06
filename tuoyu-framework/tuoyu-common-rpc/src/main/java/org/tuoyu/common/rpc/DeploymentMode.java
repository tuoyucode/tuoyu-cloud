package org.tuoyu.common.rpc;

import org.springframework.core.env.Environment;

import java.util.Locale;

/**
 * 部署方式，由配置项 {@value #KEY} 决定（默认微服务）
 *
 * @author tuoyu
 */
public enum DeploymentMode {

    /**
     * 微服务：跨服务调用走远程引用
     */
    MICROSERVICE,

    /**
     * 单体：所有模块在一个进程中，跨模块调用都是本地调用
     */
    MONOLITH;

    public static final String KEY = "tuoyu.deployment.mode";

    public static DeploymentMode of(Environment environment) {
        String value = environment.getProperty(KEY, "microservice").trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "microservice" -> MICROSERVICE;
            case "monolith" -> MONOLITH;
            default -> throw new IllegalStateException(KEY + " 只能是 microservice 或 monolith，当前为 " + value);
        };
    }

}
