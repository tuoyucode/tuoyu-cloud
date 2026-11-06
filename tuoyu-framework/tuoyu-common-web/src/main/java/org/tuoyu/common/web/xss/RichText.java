package org.tuoyu.common.web.xss;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要保存 HTML 的富文本字段（只能用于 String）。
 * <p>
 * 普通字段不做任何 XSS 处理（不修改用户输入，由前端输出时转义）；标注了本注解的字段：
 * <ul>
 *     <li>JSON 入参反序列化时按 {@link HtmlSanitizer} 的白名单清洗；</li>
 *     <li>JSON 响应输出前再清洗一次，覆盖历史数据与绕过入参清洗写入的数据。</li>
 * </ul>
 * 前端展示时仍应只对标注了本注解的字段使用 v-html，其他字段一律按文本展示。
 *
 * @author tuoyu
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
@JacksonAnnotationsInside
@JsonDeserialize(using = RichTextDeserializer.class)
public @interface RichText {
}
