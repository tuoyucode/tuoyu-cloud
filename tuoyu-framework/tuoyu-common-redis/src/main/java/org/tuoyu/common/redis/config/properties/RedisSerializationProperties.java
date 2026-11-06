package org.tuoyu.common.redis.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 序列化配置
 *
 * @author tuoyu
 */
@Data
@ConfigurationProperties(prefix = "tuoyu.redis")
public class RedisSerializationProperties {

    /**
     * 额外放行的反序列化类型（包前缀或类名，建议以 . 结尾），用于业务代码自己缓存到 Redis 的类型。
     * 至少两级包名，且不能指向 JDK 或常见框架的包。默认已放行 org.tuoyu. 下的全部类型
     */
    private List<String> allowedTypePrefixes = new ArrayList<>();

}
