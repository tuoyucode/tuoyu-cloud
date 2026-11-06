package org.tuoyu.common.redis.codec;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Redis 值序列化使用的 JsonMapper。生产配置与测试共用，保证测试验证的就是实际使用的序列化方式。
 *
 * @author tuoyu
 */
public final class RedisJsonMapperFactory {

    private RedisJsonMapperFactory() {
    }

    /**
     * @param typeValidator 反序列化时对类名的校验，生产环境使用 {@link RedisTypeAllowlist}
     */
    public static JsonMapper create(PolymorphicTypeValidator typeValidator) {
        SimpleModule simpleModule = new SimpleModule();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        simpleModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        simpleModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
        return JsonMapper.builder()
            .addModules(simpleModule)
            .defaultTimeZone(TimeZone.getDefault())
            .changeDefaultVisibility(visibilityChecker -> visibilityChecker.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
            // 序列化时写入非 final 类型的类名，反序列化时由 typeValidator 校验类名后才会加载对应的类
            .activateDefaultTyping(typeValidator, DefaultTyping.NON_FINAL)
            .build();
    }

}
