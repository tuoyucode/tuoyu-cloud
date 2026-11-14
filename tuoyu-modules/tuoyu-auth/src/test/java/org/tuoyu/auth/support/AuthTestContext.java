package org.tuoyu.auth.support;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.MessageSource;
import org.springframework.context.support.GenericApplicationContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.tuoyu.auth.service.LoginAudit;
import org.tuoyu.common.log.event.LoginInfoEvent;
import org.tuoyu.common.social.utils.AuthRedisStateCache;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 认证服务单元测试的最小 Spring 环境：国际化消息、登录日志事件、JSON 与参数校验，以及工具类在类加载时要取的 Bean。
 * 只初始化一次（MessageUtils 会缓存 MessageSource）。
 */
public final class AuthTestContext {

    private static final List<LoginInfoEvent> LOGIN_EVENTS = new CopyOnWriteArrayList<>();

    private static boolean initialized;

    private static GenericApplicationContext context;

    private AuthTestContext() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        context = new GenericApplicationContext();
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        context.registerBean("messageSource", MessageSource.class, () -> messageSource);
        context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        context.registerBean(Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator());
        // RedisUtils、SocialUtils 在类加载时取 Bean；测试中只用 mockStatic 替换它们的方法，不连接 Redis
        context.registerBean(RedissonClient.class, () -> Mockito.mock(RedissonClient.class));
        context.registerBean(AuthRedisStateCache.class, () -> Mockito.mock(AuthRedisStateCache.class));
        context.addApplicationListener(event -> {
            if (event instanceof PayloadApplicationEvent<?> payload && payload.getPayload() instanceof LoginInfoEvent loginInfo) {
                LOGIN_EVENTS.add(loginInfo);
            }
        });
        context.refresh();
        new SpringUtil().setApplicationContext(context);
        initialized = true;
    }

    /**
     * @return 测试环境的事件发布器：发布的登录日志事件记录在 {@link #loginEvents()} 中
     */
    public static ApplicationEventPublisher publisher() {
        init();
        return context;
    }

    /**
     * @return 写入测试环境的登录日志
     */
    public static LoginAudit loginAudit() {
        return new LoginAudit(publisher());
    }

    /**
     * @return 记录到的登录日志事件
     */
    public static List<LoginInfoEvent> loginEvents() {
        return LOGIN_EVENTS;
    }

}
