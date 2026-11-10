package org.tuoyu.mes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * 制造执行（MES）服务
 *
 * @author tuoyu
 */
@SpringBootApplication
public class TuoYuMesApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TuoYuMesApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  制造执行模块启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }

}
