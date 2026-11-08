package org.tuoyu.common.sensitive.handler;

import lombok.extern.slf4j.Slf4j;
import org.tuoyu.common.json.enhance.JsonEnhancementContext;
import org.tuoyu.common.json.enhance.JsonFieldContext;
import org.tuoyu.common.json.enhance.JsonFieldProcessor;
import org.tuoyu.common.sensitive.annotation.Sensitive;
import org.tuoyu.common.sensitive.core.SensitiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应脱敏处理器。
 * <p>
 * 失败闭合：
 * <ul>
 *     <li>没有 {@link SensitiveService} 实现时一律脱敏；</li>
 *     <li>判断权限时出错按“需要脱敏”处理；</li>
 *     <li>脱敏策略出错或没有生效时整体掩码（{@link org.tuoyu.common.sensitive.core.SensitiveStrategy#desensitize(String)}）；</li>
 *     <li>注解用在非字符串字段上时，需要脱敏的情况下输出 null。</li>
 * </ul>
 */
@Slf4j
@Order(100)
public class SensitiveJsonFieldProcessor implements JsonFieldProcessor {

    @Autowired(required = false)
    private SensitiveService sensitiveService;

    /**
     * 已提示过的字段，避免刷屏
     */
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    @Override
    public boolean supports(JsonFieldContext fieldContext) {
        return fieldContext.getAnnotation(Sensitive.class) != null;
    }

    @Override
    public Object process(JsonFieldContext fieldContext, Object value, JsonEnhancementContext context) {
        Sensitive sensitive = fieldContext.getAnnotation(Sensitive.class);
        if (sensitive == null || value == null || !shouldDesensitize(sensitive)) {
            return value;
        }
        if (value instanceof CharSequence text) {
            return sensitive.strategy().desensitize(text.toString());
        }
        warnOnce("type:" + fieldContext.propertyName(),
            "@Sensitive 只支持字符串字段，字段 {} 的类型为 {}，已输出 null", fieldContext.propertyName(), value.getClass().getName());
        return null;
    }

    private boolean shouldDesensitize(Sensitive sensitive) {
        if (sensitiveService == null) {
            warnOnce("service", "未提供 SensitiveService 实现，所有 @Sensitive 字段一律脱敏");
            return true;
        }
        try {
            return sensitiveService.isSensitive(sensitive.roleKey(), sensitive.perms());
        } catch (RuntimeException e) {
            log.warn("判断是否需要脱敏时出错，按需要脱敏处理：{}", e.toString());
            return true;
        }
    }

    private void warnOnce(String key, String message, Object... args) {
        if (warned.add(key)) {
            log.warn(message, args);
        }
    }

}
