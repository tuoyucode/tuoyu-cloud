package org.tuoyu.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * 网关启动程序
 *
 * @author tuoyu
 */
@Slf4j
@SpringBootApplication
public class TuoYuGatewayApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TuoYuGatewayApplication.class);
        // 记录启动步骤，供 actuator 的 startup 端点查看
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        log.info("网关启动完成");
    }

}
