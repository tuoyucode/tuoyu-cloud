package org.tuoyu.common.core.utils;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 国际化消息。
 *
 * @author tuoyu
 */
public final class MessageUtils {

    private MessageUtils() {
    }

    /**
     * 按当前语言环境解析消息并代入参数。
     * <p>
     * 每次调用时从容器取 {@code MessageSource}；找不到消息、或不在 Spring 容器中时返回消息键本身。
     *
     * @param code 消息键
     * @param args 参数，替换消息中的 {@code {0}}、{@code {1}} 等
     * @return 消息
     */
    public static String message(String code, Object... args) {
        MessageSource messageSource;
        try {
            messageSource = SpringUtils.getBean(MessageSource.class);
        } catch (RuntimeException e) {
            // 不在 Spring 容器中（例如单元测试、容器尚未启动）
            return code;
        }
        try {
            return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return code;
        }
    }

}
