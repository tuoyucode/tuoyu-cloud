package org.tuoyu.gen;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * 代码生成服务
 *
 * @author tuoyu
 */
@Slf4j
@SpringBootApplication
public class TuoYuGenApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TuoYuGenApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        log.info("代码生成服务启动完成");
    }

}
