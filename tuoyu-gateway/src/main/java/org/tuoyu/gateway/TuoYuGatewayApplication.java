package org.tuoyu.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * 网关启动程序
 *
 * @author Lion Li
 */
@SpringBootApplication
public class TuoYuGatewayApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TuoYuGatewayApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  MVC网关启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }

}
