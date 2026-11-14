package org.tuoyu.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * 认证授权中心
 *
 * @author tuoyu
 */
@Slf4j
@SpringBootApplication
public class TuoYuAuthApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TuoYuAuthApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        log.info("认证服务启动完成");
    }

}
