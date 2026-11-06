package org.tuoyu.common.web.xss;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 富文本字段的反序列化：读取字符串后按白名单清洗
 *
 * @author tuoyu
 */
public class RichTextDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            // 富文本只接受字符串，数组、对象等结构直接报错，不做隐式转换
            return (String) ctxt.handleUnexpectedToken(String.class, p);
        }
        return HtmlSanitizer.sanitize(p.getString());
    }

}
