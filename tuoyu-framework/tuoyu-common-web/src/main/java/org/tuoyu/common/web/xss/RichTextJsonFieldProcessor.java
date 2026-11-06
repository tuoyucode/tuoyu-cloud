package org.tuoyu.common.web.xss;

import org.springframework.core.annotation.Order;
import org.tuoyu.common.json.enhance.JsonEnhancementContext;
import org.tuoyu.common.json.enhance.JsonFieldContext;
import org.tuoyu.common.json.enhance.JsonFieldProcessor;

/**
 * 响应输出前清洗 {@link RichText} 字段。
 * <p>
 * 入参已经清洗过，这里再清洗一次是为了覆盖历史数据，以及不经过 JSON 入参写入的数据（导入、服务间调用、直接改库等）
 *
 * @author tuoyu
 */
@Order(200)
public class RichTextJsonFieldProcessor implements JsonFieldProcessor {

    @Override
    public boolean supports(JsonFieldContext fieldContext) {
        return fieldContext.getAnnotation(RichText.class) != null;
    }

    @Override
    public Object process(JsonFieldContext fieldContext, Object value, JsonEnhancementContext context) {
        if (value instanceof String html) {
            return HtmlSanitizer.sanitize(html);
        }
        return value;
    }

}
